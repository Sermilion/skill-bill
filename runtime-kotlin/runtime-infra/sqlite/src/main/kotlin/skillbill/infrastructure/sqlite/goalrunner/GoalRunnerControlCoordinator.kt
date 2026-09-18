package skillbill.infrastructure.sqlite.goalrunner
import skillbill.agentaddon.model.AgentAddonSelection
import skillbill.agentaddon.model.PersistedAgentAddonSelectionEntry
import skillbill.contracts.JsonCodec
import skillbill.contracts.workflow.FeatureTaskRuntimeGoalContinuationArtifactPayloadKeys
import skillbill.error.InvalidAgentAddonSelectionError
import skillbill.error.LegacyProseWorkflowError
import skillbill.goalrunner.model.GOAL_PAUSE_REASON_OPERATOR_REQUEST
import skillbill.goalrunner.model.GOAL_PAUSE_REASON_STOP_AFTER_SUBTASK
import skillbill.goalrunner.model.GoalRunnerControlState
import skillbill.goalrunner.model.GoalRunnerExecutionLease
import skillbill.infrastructure.sqlite.workflow.decompositionRuntime
import skillbill.infrastructure.sqlite.workflow.findDecomposedParentWorkflow
import skillbill.ports.db.DatabaseSessionFactory
import skillbill.ports.agentrun.model.AgentRunSpawnAuthorization
import skillbill.ports.goalrunner.runner.model.GoalRunnerLaunchAuthorizationDeniedException
import skillbill.ports.goalrunner.runner.model.GoalRunnerPausePersistenceResult
import skillbill.ports.goalrunner.acquireExecutionLease
import skillbill.ports.goalrunner.executionLease
import skillbill.ports.goalrunner.heartbeatExecutionLease
import skillbill.ports.goalrunner.releaseExecutionLease
import skillbill.ports.goalrunner.releaseExecutionLeaseIfExpired
import skillbill.ports.goalrunner.runner.model.GoalRunnerCompletionPersistenceResult
import skillbill.ports.goalrunner.runner.model.GoalRunnerLaunchAuthorization
import skillbill.ports.goalrunner.runner.model.GoalRunnerManifestState
import skillbill.ports.persistence.UnitOfWork
import skillbill.ports.workflow.WorkflowStateRepository
import skillbill.ports.workflow.get
import skillbill.ports.workflow.model.WorkflowFamily
import skillbill.ports.workflow.model.FeatureTaskWorkflowMode
import skillbill.ports.workflow.model.toSnapshot
import skillbill.workflow.decomposition.DecompositionManifestValidator
import skillbill.workflow.engine.model.WorkflowStateSnapshot
import skillbill.workflow.model.DecompositionStatus
import skillbill.workflow.model.decompositionStatus
import java.time.Clock
import java.nio.file.Path

internal class GoalRunnerControlCoordinator(
  internal val database: DatabaseSessionFactory,
  internal val decompositionManifestValidator: DecompositionManifestValidator,
  internal val clock: Clock,
  internal val saveProjection: (UnitOfWork, GoalRunnerManifestState) -> SavedManifestProjection,
) {
  fun controlState(parentWorkflowId: String): GoalRunnerControlState =
    database.read { unitOfWork -> unitOfWork.goalRunnerControls.controlState(parentWorkflowId) }

  fun persistControlState(parentWorkflowId: String, state: GoalRunnerControlState): GoalRunnerControlState =
    database.transaction { unitOfWork ->
      unitOfWork.goalRunnerControls.persistControlState(parentWorkflowId, state)
    }

  fun clearRunnerInterruptedPause(parentWorkflowId: String): GoalRunnerControlState =
    database.transaction { unitOfWork ->
      unitOfWork.goalRunnerControls.clearRunnerInterruptedPause(parentWorkflowId)
    }

  fun executionLease(parentWorkflowId: String): GoalRunnerExecutionLease? =
    database.read { unitOfWork -> unitOfWork.goalRunnerControls.executionLease(parentWorkflowId) }

  fun acquireExecutionLease(
    parentWorkflowId: String,
    lease: GoalRunnerExecutionLease,
    expectedOwnerToken: String?,
  ): Boolean = database.transaction { unitOfWork ->
    reconcileControlStateForManifest(unitOfWork, parentWorkflowId, decompositionManifestValidator)
    unitOfWork.goalRunnerControls.acquireExecutionLease(parentWorkflowId, lease, expectedOwnerToken)
  }

  fun heartbeatExecutionLease(parentWorkflowId: String, lease: GoalRunnerExecutionLease): Boolean =
    database.transaction { unitOfWork ->
      reconcileControlStateForManifest(unitOfWork, parentWorkflowId, decompositionManifestValidator)
      unitOfWork.goalRunnerControls.heartbeatExecutionLease(parentWorkflowId, lease)
    }

  fun releaseExecutionLease(parentWorkflowId: String, ownerToken: String, generation: Long): Boolean =
    database.transaction { unitOfWork ->
      unitOfWork.goalRunnerControls.releaseExecutionLease(parentWorkflowId, ownerToken, generation)
    }

  fun releaseExecutionLeaseIfExpired(
    parentWorkflowId: String,
    ownerToken: String,
    generation: Long,
    nowInstant: String,
  ): Boolean = database.transaction { unitOfWork ->
    unitOfWork.goalRunnerControls.releaseExecutionLeaseIfExpired(
      parentWorkflowId,
      ownerToken,
      generation,
      nowInstant,
    )
  }

  fun authorizeSubtaskLaunch(state: GoalRunnerManifestState, subtaskId: Int): GoalRunnerLaunchAuthorization =
    database.transaction { unitOfWork ->
      require(subtaskId > 0) { "subtaskId must be positive." }
      val existing = requireParent(unitOfWork, state.parentWorkflowId)
      val controls = unitOfWork.goalRunnerControls.controlState(existing.workflowId)
      val manifest = existing.decompositionRuntime(decompositionManifestValidator) ?: state.manifest
      GoalRunnerLaunchAuthorization(
        authorized = !controls.requiresPauseBoundary(manifest),
        controlState = controls,
        spawnAuthorization = spawnAuthorization(state),
      )
    }

  fun pauseNow(
    parentWorkflowId: String,
    reason: String,
    pausedAt: String,
    overwriteExistingReason: Boolean,
  ): GoalRunnerControlState? = database.transaction { unitOfWork ->
    val parent = WorkflowFamily.TASK_RUNTIME.get(unitOfWork.workflowStates, parentWorkflowId)
      ?: return@transaction null
    migrateLegacyGoalRunnerControls(unitOfWork, parent)
    val existing = unitOfWork.goalRunnerControls.controlState(parentWorkflowId)
    if (existing.paused && !overwriteExistingReason) {
      return@transaction existing
    }
    unitOfWork.goalRunnerControls.persistControlState(
      parentWorkflowId,
      existing.copy(
        pauseRequested = true,
        pauseConsumed = true,
        paused = true,
        pauseReason = reason,
        pausedAt = pausedAt,
      ),
    )
  }

  fun pauseAtBoundary(state: GoalRunnerManifestState): GoalRunnerManifestState = database.transaction { unitOfWork ->
    val parent = requireParent(unitOfWork, state.parentWorkflowId)
    val controls = unitOfWork.goalRunnerControls.controlState(parent.workflowId)
    val authoritativeManifest = parent.decompositionRuntime(decompositionManifestValidator) ?: state.manifest
    val authoritativeState = state.copy(manifest = authoritativeManifest)
    val targetReached = controls.targetReached(authoritativeState)
    val pausedControls = if (controls.requiresPauseBoundary(authoritativeManifest)) {
      controls.pauseAtOperatorBoundary(clock.instant().toString(), targetReached)
    } else {
      controls
    }
    val saved = saveProjection(unitOfWork, authoritativeState)
    if (pausedControls != controls) {
      unitOfWork.goalRunnerControls.persistControlState(parent.workflowId, pausedControls)
    }
    saved.state.copy(controlState = pausedControls)
  }

  fun saveCompletedSubtaskAtBoundary(
    state: GoalRunnerManifestState,
    subtaskId: Int,
  ): GoalRunnerCompletionPersistenceResult = database.transaction { unitOfWork ->
    val parent = requireParent(unitOfWork, state.parentWorkflowId)
    val controls = unitOfWork.goalRunnerControls.controlState(parent.workflowId)
    val persistedManifest = parent.decompositionRuntime(decompositionManifestValidator) ?: state.manifest
    val authoritativeManifest = mergeConcurrentGoalProgress(persistedManifest, state.manifest)
    val authoritativeState = state.copy(manifest = authoritativeManifest)
    val targetReached = controls.stopAfterSubtaskId == subtaskId && !controls.stopAfterConsumed
    val operatorRequested = controls.pauseRequested && !controls.pauseConsumed
    val shouldPause = controls.requiresPauseBoundary(authoritativeManifest) || targetReached || operatorRequested
    val nextControls =
      if (shouldPause) controls.pauseAtOperatorBoundary(clock.instant().toString(), targetReached) else controls
    val saved = saveProjection(unitOfWork, authoritativeState)
    if (nextControls != controls) {
      unitOfWork.goalRunnerControls.persistControlState(parent.workflowId, nextControls)
    }
    GoalRunnerCompletionPersistenceResult(
      state = saved.state.copy(controlState = nextControls),
      paused = shouldPause,
    )
  }
}

internal fun reconcileControlStateForManifest(
  unitOfWork: UnitOfWork,
  parentWorkflowId: String,
  validator: DecompositionManifestValidator,
) {
  val parent = WorkflowFamily.TASK_RUNTIME.get(unitOfWork.workflowStates, parentWorkflowId) ?: return
  val manifest = parent.decompositionRuntime(validator) ?: return
  val existing = unitOfWork.goalRunnerControls.controlState(parentWorkflowId)
  val reconciled = existing.reconciledForCurrentSubtask(manifest.currentSubtaskIntent.subtaskId)
  if (reconciled != existing) {
    unitOfWork.goalRunnerControls.persistControlState(parentWorkflowId, reconciled)
  }
}

internal fun GoalRunnerControlCoordinator.requireParent(
  unitOfWork: UnitOfWork,
  parentWorkflowId: String,
): WorkflowStateSnapshot {
  val parent = WorkflowFamily.TASK_RUNTIME.get(unitOfWork.workflowStates, parentWorkflowId)
    ?: error("Unknown decomposed parent workflow '$parentWorkflowId'.")
  migrateLegacyGoalRunnerControls(unitOfWork, parent)
  return parent
}

internal fun GoalRunnerControlCoordinator.spawnAuthorization(
  state: GoalRunnerManifestState,
): AgentRunSpawnAuthorization = object : AgentRunSpawnAuthorization {
  override fun <T> withAuthorization(spawn: () -> T): T = database.transaction { unitOfWork ->
    val parent = requireParent(unitOfWork, state.parentWorkflowId)
    val controls = unitOfWork.goalRunnerControls.controlState(parent.workflowId)
    val manifest = parent.decompositionRuntime(decompositionManifestValidator) ?: state.manifest
    if (controls.requiresPauseBoundary(manifest)) {
      throw GoalRunnerLaunchAuthorizationDeniedException(controls)
    }
    spawn()
  }
}

internal fun GoalRunnerControlState.targetReached(state: GoalRunnerManifestState): Boolean =
  stopAfterSubtaskId?.let { targetId ->
    state.manifest.subtasks.any {
      it.id == targetId && it.status.decompositionStatus() == DecompositionStatus.COMPLETE
    }
  } == true && !stopAfterConsumed

internal fun GoalRunnerControlCoordinator.bindRepositoryIdentity(
  parentWorkflowId: String,
  repositoryIdentity: String,
): GoalRunnerControlState = database.transaction { unitOfWork ->
  require(repositoryIdentity.isNotBlank()) { "repositoryIdentity is required." }
  val parent = requireParent(unitOfWork, parentWorkflowId)
  val existing = unitOfWork.goalRunnerControls.controlState(parent.workflowId)
  if (existing.repositoryIdentity == repositoryIdentity) {
    existing
  } else {
    unitOfWork.goalRunnerControls.persistControlState(
      parent.workflowId,
      existing.copy(repositoryIdentity = repositoryIdentity),
    )
  }
}

internal fun GoalRunnerControlCoordinator.planningSpawnAuthorization(
  parentWorkflowId: String,
): AgentRunSpawnAuthorization = object : AgentRunSpawnAuthorization {
  override fun <T> withAuthorization(spawn: () -> T): T = database.transaction { unitOfWork ->
    val parent = requireParent(unitOfWork, parentWorkflowId)
    val controls = unitOfWork.goalRunnerControls.controlState(parent.workflowId)
    val manifest = parent.decompositionRuntime(decompositionManifestValidator)
      ?: error("Goal parent '$parentWorkflowId' has no decomposition manifest.")
    if (controls.requiresPauseBoundary(manifest)) {
      throw GoalRunnerLaunchAuthorizationDeniedException(controls)
    }
    spawn()
  }
}

internal fun GoalRunnerControlCoordinator.persistStopAfterSubtask(
  parentWorkflowId: String,
  subtaskId: Int,
): GoalRunnerControlState = database.transaction { unitOfWork ->
  require(subtaskId > 0) { "stop-after subtask id must be positive." }
  val parent = requireParent(unitOfWork, parentWorkflowId)
  val manifest = parent.decompositionRuntime(decompositionManifestValidator)
    ?: error("Goal parent '$parentWorkflowId' has no decomposition manifest.")
  require(manifest.subtasks.any { it.id == subtaskId }) {
    "Goal parent '$parentWorkflowId' has no subtask '$subtaskId'."
  }
  val existing = unitOfWork.goalRunnerControls.controlState(parentWorkflowId)
  require(existing.stopAfterSubtaskId == null || existing.stopAfterSubtaskId == subtaskId) {
    "Goal parent '$parentWorkflowId' already has stop-after subtask ${existing.stopAfterSubtaskId}."
  }
  existing.stopAfterSubtaskId?.let { existing }
    ?: unitOfWork.goalRunnerControls.persistControlState(
      parentWorkflowId,
      existing.copy(stopAfterSubtaskId = subtaskId),
    )
}

internal fun GoalRunnerControlCoordinator.resume(parentWorkflowId: String): GoalRunnerManifestState? =
  database.transaction { unitOfWork ->
    val parent = WorkflowFamily.TASK_RUNTIME.get(unitOfWork.workflowStates, parentWorkflowId)
      ?: return@transaction null
    migrateLegacyGoalRunnerControls(unitOfWork, parent)
    val existing = unitOfWork.goalRunnerControls.controlState(parentWorkflowId)
    val resumed = if (existing.paused || existing.pauseRequested) {
      unitOfWork.goalRunnerControls.persistControlState(
        parentWorkflowId,
        existing.copy(
          pauseRequested = false,
          pauseConsumed = false,
          paused = false,
          pauseReason = null,
          pausedAt = null,
        ),
      )
    } else {
      existing
    }
    parent.decompositionRuntime(decompositionManifestValidator)?.let { manifest ->
      GoalRunnerManifestState(
        parentWorkflowId = parent.workflowId,
        dbPath = unitOfWork.dbPath.toString(),
        manifest = manifest,
        controlState = resumed,
      )
    }
  }

internal fun GoalRunnerControlCoordinator.persistPauseRequest(
  unitOfWork: UnitOfWork,
  parentWorkflowId: String,
): GoalRunnerControlState {
  val existing = unitOfWork.goalRunnerControls.controlState(parentWorkflowId)
  return if (existing.paused || existing.pauseRequested) {
    existing
  } else {
    unitOfWork.goalRunnerControls.persistControlState(
      parentWorkflowId,
      existing.copy(
        pauseRequested = true,
        pauseConsumed = false,
        pauseReason = GOAL_PAUSE_REASON_OPERATOR_REQUEST,
      ),
    )
  }
}

internal fun GoalRunnerControlCoordinator.requestPause(parentWorkflowId: String): GoalRunnerControlState? =
  database.transaction { unitOfWork ->
    WorkflowFamily.TASK_RUNTIME.get(unitOfWork.workflowStates, parentWorkflowId)?.let { parent ->
      migrateLegacyGoalRunnerControls(unitOfWork, parent)
      persistPauseRequest(unitOfWork, parentWorkflowId)
    }
  }

internal fun GoalRunnerControlCoordinator.requestPauseByIssueKey(
  issueKey: String,
  repoRoot: Path?,
): GoalRunnerPausePersistenceResult? = database.transaction { unitOfWork ->
  val parent = unitOfWork.workflowStates.findDecomposedParentWorkflow(
    issueKey,
    decompositionManifestValidator,
  ) ?: return@transaction null
  migrateLegacyGoalRunnerControls(unitOfWork, parent.toSnapshot())
  val existing = unitOfWork.goalRunnerControls.controlState(parent.workflowId)
  if (repoRoot != null) {
    val identity = goalRepositoryIdentity(repoRoot)
    if (existing.repositoryIdentity != identity) {
      unitOfWork.goalRunnerControls.persistControlState(
        parent.workflowId,
        existing.copy(repositoryIdentity = identity),
      )
    }
  }
  GoalRunnerPausePersistenceResult(parent.workflowId, persistPauseRequest(unitOfWork, parent.workflowId))
}

internal fun workflowFamilyFor(workflowStates: WorkflowStateRepository, workflowId: String): WorkflowFamily? {
  val featureTaskRow = workflowStates.getFeatureTaskWorkflow(workflowId)
  if (featureTaskRow != null) {
    return when (featureTaskRow.mode) {
      FeatureTaskWorkflowMode.RUNTIME -> WorkflowFamily.TASK_RUNTIME
      FeatureTaskWorkflowMode.PROSE, null -> throw LegacyProseWorkflowError(workflowId, featureTaskRow.issueKey)
    }
  }
  return if (workflowStates.getFeatureVerifyWorkflow(workflowId) != null) {
    WorkflowFamily.VERIFY
  } else {
    null
  }
}

internal fun GoalRunnerControlState.pauseAtOperatorBoundary(
  pausedAtNow: String,
  targetReached: Boolean = false,
): GoalRunnerControlState = when {
  paused -> copy(stopAfterConsumed = stopAfterConsumed || targetReached)
  pauseRequested -> copy(
    pauseConsumed = true,
    paused = true,
    pauseReason = pauseReason ?: GOAL_PAUSE_REASON_OPERATOR_REQUEST,
    pausedAt = pausedAtNow,
    stopAfterConsumed = stopAfterConsumed || targetReached,
  )
  targetReached -> copy(
    paused = true,
    pauseReason = GOAL_PAUSE_REASON_STOP_AFTER_SUBTASK,
    pausedAt = pausedAtNow,
    stopAfterConsumed = true,
  )
  else -> this
}

internal fun decodeGoalAgentAddonSelection(raw: Any?): AgentAddonSelection {
  val values = raw ?: return AgentAddonSelection()
  val entries = values as? List<*>
    ?: throw InvalidAgentAddonSelectionError("Goal review policy agent_addon_selection must be a list.")
  return AgentAddonSelection(
    entries.mapIndexed(::decodeGoalAgentAddonSelectionEntry),
  )
}

private fun decodeGoalAgentAddonSelectionEntry(index: Int, value: Any?): PersistedAgentAddonSelectionEntry {
  val entry = JsonCodec.anyToStringAnyMap(value)
    ?: throw InvalidAgentAddonSelectionError(
      "Goal review policy agent_addon_selection entry $index must be a map.",
    )
  val expectedKeys = setOf(
    FeatureTaskRuntimeGoalContinuationArtifactPayloadKeys.ADDON_SLUG,
    FeatureTaskRuntimeGoalContinuationArtifactPayloadKeys.ADDON_SOURCE_IDENTITY,
    FeatureTaskRuntimeGoalContinuationArtifactPayloadKeys.ADDON_CONTENT_SHA256,
  )
  if (entry.keys != expectedKeys) {
    throw InvalidAgentAddonSelectionError(
      "Goal review policy agent_addon_selection entry $index has invalid fields.",
    )
  }
  return PersistedAgentAddonSelectionEntry(
    requiredAddonField(entry, index, FeatureTaskRuntimeGoalContinuationArtifactPayloadKeys.ADDON_SLUG, "slug"),
    requiredAddonField(
      entry,
      index,
      FeatureTaskRuntimeGoalContinuationArtifactPayloadKeys.ADDON_SOURCE_IDENTITY,
      "source_identity",
    ),
    requiredAddonField(
      entry,
      index,
      FeatureTaskRuntimeGoalContinuationArtifactPayloadKeys.ADDON_CONTENT_SHA256,
      "content_sha256",
    ),
  )
}

private fun requiredAddonField(entry: Map<String, Any?>, index: Int, key: String, label: String): String =
  entry[key] as? String
    ?: throw InvalidAgentAddonSelectionError("Goal review policy add-on entry $index is missing $label.")
