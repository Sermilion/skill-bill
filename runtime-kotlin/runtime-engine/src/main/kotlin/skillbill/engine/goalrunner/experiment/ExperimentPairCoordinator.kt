package skillbill.engine.goalrunner.experiment

import me.tatarka.inject.annotations.Inject
import skillbill.application.decomposition.baseBranch
import skillbill.application.decomposition.branchName
import skillbill.contracts.JsonCodec
import skillbill.contracts.experiment.EXPERIMENT_PAIR_CONTRACT_VERSION
import skillbill.contracts.experiment.ExperimentPairPayloadKeys
import skillbill.contracts.experiment.ExperimentReportPayloadKeys
import skillbill.contracts.experiment.ExperimentTelemetryPayloadKeys
import skillbill.contracts.telemetry.TelemetryMeasurementAvailability
import skillbill.engine.experiment.isolation.ExperimentCheckpointNamespace
import skillbill.engine.experiment.observation.ExperimentObservationMeasurement
import skillbill.engine.experiment.observation.ExperimentObservationRecordRequest
import skillbill.engine.experiment.observation.ExperimentObservationRecorder
import skillbill.engine.experiment.report.ExperimentReportProjector
import skillbill.engine.experiment.telemetry.ExperimentTelemetryRecorder
import skillbill.engine.featuretask.lifecycle.subtask.DirtyPaths
import skillbill.engine.featuretask.lifecycle.subtask.DirtyPathsError
import skillbill.engine.featuretask.lifecycle.subtask.dirtyImplementationPaths
import skillbill.engine.goalrunner.model.GoalRunnerRunRequest
import skillbill.engine.goalrunner.status.completed
import skillbill.error.shellcontent.ExperimentDirtySourceRefusalError
import skillbill.error.shellcontent.ExperimentIsolationCapabilityRefusalError
import skillbill.experiment.model.ExperimentArmId
import skillbill.experiment.model.ExperimentExecutionMode
import skillbill.goalrunner.model.GoalPullRequestStatus
import skillbill.goalrunner.model.GoalRunnerRunReport
import skillbill.goalrunner.model.GoalRunnerStopReason
import skillbill.goalrunner.model.GoalRunnerStopReport
import skillbill.goalrunner.terminalStatus
import skillbill.ports.experiment.isolation.ExperimentArmIsolationContext
import skillbill.ports.experiment.isolation.ExperimentArmStatePaths
import skillbill.ports.experiment.isolation.ExperimentIsolationCapabilityPort
import skillbill.ports.experiment.measurement.ExperimentArmMeasurement
import skillbill.ports.experiment.measurement.ExperimentArmMeasurementPort
import skillbill.ports.experiment.measurement.ExperimentMeasuredValue
import skillbill.ports.experiment.pair.ExperimentPairOwnerPort
import skillbill.ports.experiment.pair.ExperimentPairPayload
import skillbill.ports.experiment.pair.ExperimentPairPersistedState
import skillbill.ports.experiment.publication.ExperimentParentDeliveryPort
import skillbill.ports.experiment.publication.ExperimentParentDeliveryRequest
import skillbill.ports.experiment.publication.ExperimentPublicationResult
import skillbill.ports.experiment.selection.ExperimentLaunchSelection
import skillbill.ports.experiment.selection.ExperimentSelectionPort
import skillbill.ports.workflow.gitops.WorkflowGitOperations
import skillbill.ports.workflow.gitops.model.WorkflowGitOperationResult
import skillbill.ports.workflow.gitops.readiness.resolveReadinessTreeIdentity
import skillbill.ports.workflow.gitops.repositoryFingerprint
import skillbill.ports.workflow.gitops.worktree.LinkedWorktreeAddRequest
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.time.Clock
import java.time.Instant
import java.util.UUID
import kotlin.random.Random
import kotlin.time.Duration.Companion.minutes

@Inject
class ExperimentPairCoordinator(
  private val goalRunner: ExperimentGoalRunnerPort,
  private val selectionPort: ExperimentSelectionPort,
  private val pairOwner: ExperimentPairOwnerPort,
  private val gitOperations: WorkflowGitOperations,
  private val isolationCapability: ExperimentIsolationCapabilityPort,
  private val measurementPort: ExperimentArmMeasurementPort?,
  private val parentDelivery: ExperimentParentDeliveryPort,
  private val telemetryRecorder: ExperimentTelemetryRecorder?,
  private val random: Random,
  private val clock: Clock,
) {
  private data class ArmLifecycleUpdate(
    val payload: Map<String, Any?>,
    val pairId: String,
    val arm: ExperimentArmId,
    val terminalStatus: String,
    val worktreePath: Path,
    val workflowId: String? = null,
    val failureReason: String? = null,
  )

  private data class SourceIdentitySnapshot(
    val frozen: Map<*, *>,
    val currentCommit: String,
    val currentRepository: String,
    val currentTree: String,
    val currentSpecHash: String,
    val repoRoot: Path,
    val issueKey: String,
  )

  fun run(request: GoalRunnerRunRequest): GoalRunnerRunReport {
    val requiresPair =
      request.experimentsParameter != null ||
        request.savedExperimentSelection != null ||
        request.experimentPairId != null
    if (!requiresPair) return runWithoutLease(request)
    val savedSelection =
      request.savedExperimentSelection
        ?: request.experimentPairId?.let { pairOwner.load(it)?.selectedNames }
    val launchSelection =
      selectionPort.resolveForLaunch(
        repoRoot = request.repoRoot,
        parameter = request.experimentsParameter,
        mode = ExperimentExecutionMode.GOAL_PAIR,
        savedSelection = savedSelection,
      )
    if (launchSelection.normalizedNames.isEmpty()) {
      return goalRunner.run(request.copy(experimentsParameter = null, savedExperimentSelection = null))
    }
    val pairId = request.experimentPairId ?: UUID.randomUUID().toString()
    val leaseToken = UUID.randomUUID().toString()
    if (!pairOwner.acquireLease(pairId, leaseToken, clock.millis(), 30.minutes.inWholeMilliseconds)) {
      throw ExperimentIsolationCapabilityRefusalError("Experiment pair $pairId is already owned by another runtime.")
    }
    return try {
      runWithoutLease(request.copy(experimentPairId = pairId), launchSelection)
    } finally {
      pairOwner.releaseLease(pairId, leaseToken)
    }
  }

  private fun runWithoutLease(
    request: GoalRunnerRunRequest,
    prevalidatedSelection: ExperimentLaunchSelection? = null,
  ): GoalRunnerRunReport {
    val launchSelection =
      prevalidatedSelection ?: selectionPort.resolveForLaunch(
        repoRoot = request.repoRoot,
        parameter = request.experimentsParameter,
        mode = ExperimentExecutionMode.GOAL_PAIR,
        savedSelection =
          request.savedExperimentSelection
            ?: request.experimentPairId?.let { pairOwner.load(it)?.selectedNames },
      )
    if (launchSelection.normalizedNames.isEmpty()) {
      return goalRunner.run(request.copy(experimentsParameter = null, savedExperimentSelection = null))
    }
    val execution = prepareExecution(request, launchSelection)
    val lastReport = executeArms(execution)
    val finalReport = lastReport ?: reconstructedGoalReport(request.issueKey, execution.pairId)
    finalizeExecution(execution, finalReport)
    return finalReport
  }

  private data class GoalPairExecution(
    val request: GoalRunnerRunRequest,
    val pairId: String,
    val selection: ExperimentLaunchSelection,
    val armOrder: List<ExperimentArmId>,
    val persisted: ExperimentPairPersistedState?,
  )

  private fun prepareExecution(
    request: GoalRunnerRunRequest,
    selection: ExperimentLaunchSelection,
  ): GoalPairExecution {
    val pairId = request.experimentPairId ?: UUID.randomUUID().toString()
    val persisted = pairOwner.load(pairId)
    val armSelection = persisted?.let { ArmSelection(it.armOrder, it.randomSeed) } ?: drawArmOrder()
    armSelection.order.forEach { arm ->
      val armWorktree = armWorktreePath(request.repoRoot, pairId, arm)
      isolationCapability.assertLaunchSupported(
        ExperimentArmIsolationContext(
          pairId = pairId,
          armId = arm,
          checkpointNamespacePrefix = ExperimentCheckpointNamespace.prefix(pairId, arm),
          treatmentEnabled = arm == ExperimentArmId.TREATMENT,
          statePaths = statePaths(request.repoRoot, armWorktree),
        ),
      )
    }
    ensureCleanSource(request)
    if (persisted == null) {
      pairOwner.save(
        ExperimentPairPersistedState(
          pairId = pairId,
          executionMode = ExperimentExecutionMode.GOAL_PAIR,
          selectedNames = selection.normalizedNames,
          armOrder = armSelection.order,
          randomSeed = armSelection.seed,
          pairPayload =
            ExperimentPairPayload(
              frozenPairPayload(
                request = request,
                pairId = pairId,
                selection = selection,
                armOrder = armSelection.order,
                randomSeed = armSelection.seed,
              ),
            ),
        ),
      )
    }
    return GoalPairExecution(request, pairId, selection, armSelection.order, persisted)
  }

  private fun ensureCleanSource(request: GoalRunnerRunRequest) {
    when (val dirty = gitOperations.dirtyImplementationPaths(request.repoRoot)) {
      is DirtyPathsError -> throw ExperimentDirtySourceRefusalError(listOf(dirty.reason))
      is DirtyPaths -> {
        val dirtyPaths =
          dirty.paths.filterNot { path ->
            isPreparedSpecPath(request.repoRoot, request.issueKey, path)
          }
        if (dirtyPaths.isNotEmpty()) throw ExperimentDirtySourceRefusalError(dirtyPaths)
      }
    }
  }

  private fun executeArms(execution: GoalPairExecution): GoalRunnerRunReport? {
    val completedArms = completedArms(execution.persisted)
    recordCompletedArmObservations(execution, completedArms)
    var lastReport: GoalRunnerRunReport? = null
    for (arm in execution.armOrder.filterNot(completedArms::contains)) {
      val result = runGoalArm(execution, arm)
      lastReport = result
      if (result.shouldPausePair()) break
    }
    return lastReport
  }

  private fun completedArms(persisted: ExperimentPairPersistedState?): Set<ExperimentArmId> =
    persisted?.pairPayload
      ?.get(ExperimentPairPayloadKeys.ARM_OUTCOMES)
      ?.let { it as? List<*> }
      ?.filterIsInstance<Map<*, *>>()
      ?.filter { it[ExperimentPairPayloadKeys.TERMINAL_STATUS] == "completed" }
      ?.mapNotNull { it[ExperimentPairPayloadKeys.ARM_ID]?.toString()?.let(ExperimentArmId::fromWire) }
      ?.toSet()
      .orEmpty()

  private fun recordCompletedArmObservations(
    execution: GoalPairExecution,
    completedArms: Set<ExperimentArmId>,
  ) {
    completedArms.forEach { arm ->
      val outcome = armOutcome(pairOwner.load(execution.pairId)?.pairPayload?.toMap(), arm) ?: return@forEach
      recordArmObservation(
        pairId = execution.pairId,
        arm = arm,
        workflowId =
          outcome[ExperimentPairPayloadKeys.WORKFLOW_ID]?.toString()
            ?: "${execution.pairId}:${arm.wireValue}",
      )
    }
  }

  private fun runGoalArm(
    execution: GoalPairExecution,
    arm: ExperimentArmId,
  ): GoalRunnerRunReport {
    val request = execution.request
    verifyFrozenSourceIdentity(
      request.repoRoot,
      execution.pairId,
      request.issueKey,
      pairOwner.load(execution.pairId)?.pairPayload?.toMap(),
    )
    val armWorktree = armWorktreePath(request.repoRoot, execution.pairId, arm)
    ensureArmWorktree(request.repoRoot, armWorktree, execution.pairId, arm)
    prepareArmStatePaths(statePaths(request.repoRoot, armWorktree))
    copyFrozenSpecBundle(request.repoRoot, armWorktree, request.issueKey)
    val armRequest = armRequest(execution, arm, armWorktree)
    setArmRunning(execution.pairId, arm, armWorktree)
    val armResult = runCatching { goalRunner.run(armRequest) }
    val armReport =
      armResult.getOrElse { failure ->
        setArmFailed(execution.pairId, arm, armWorktree, failure)
        return failedArmReport(request.issueKey, execution.pairId, arm, failure)
      }
    val isolationObservation =
      isolationCapability.observe(
        ExperimentArmIsolationContext(
          pairId = execution.pairId,
          armId = arm,
          checkpointNamespacePrefix = ExperimentCheckpointNamespace.prefix(execution.pairId, arm),
          treatmentEnabled = arm == ExperimentArmId.TREATMENT,
          statePaths = statePaths(request.repoRoot, armWorktree),
        ),
      )
    if (!isolationObservation.valid) {
      val reason = isolationObservation.policyBreaches.joinToString("; ")
      setArmFailed(
        execution.pairId,
        arm,
        armWorktree,
        IllegalStateException("isolation policy breach: $reason"),
      )
      return failedArmReport(
        request.issueKey,
        execution.pairId,
        arm,
        IllegalStateException("isolation policy breach: $reason"),
      )
    }
    recordArmCompletion(execution, arm, armReport, armWorktree)
    return armReport
  }

  private fun armRequest(
    execution: GoalPairExecution,
    arm: ExperimentArmId,
    armWorktree: Path,
  ): GoalRunnerRunRequest =
    execution.request.copy(
      repoRoot = armWorktree,
      experimentPairId = execution.pairId,
      experimentArmId = arm,
      savedExperimentSelection = execution.selection.normalizedNames,
      experimentsParameter = null,
      experimentTreatmentCapabilities = execution.selection.treatmentCapabilities,
      experimentTreatmentCapabilitiesDenied =
        if (arm == ExperimentArmId.CONTROL) {
          execution.selection.treatmentCapabilities
        } else {
          emptySet()
        },
      deferRemotePublication = true,
    )

  private fun recordArmCompletion(
    execution: GoalPairExecution,
    arm: ExperimentArmId,
    report: GoalRunnerRunReport,
    armWorktree: Path,
  ) {
    pairOwner.save(
      updateArmOutcome(
        pairOwner.load(execution.pairId)?.pairPayload?.toMap().orEmpty(),
        execution.pairId,
        arm,
        report,
        armWorktree,
      ),
    )
    recordArmObservation(
      pairId = execution.pairId,
      workflowId = report.parentWorkflowId ?: "${execution.pairId}:${arm.wireValue}",
      arm = arm,
    )
  }

  private fun setArmRunning(
    pairId: String,
    arm: ExperimentArmId,
    worktreePath: Path,
  ) {
    pairOwner.save(
      updateArmLifecycle(
        ArmLifecycleUpdate(
          payload = pairOwner.load(pairId)?.pairPayload?.toMap().orEmpty(),
          pairId = pairId,
          arm = arm,
          terminalStatus = "running",
          worktreePath = worktreePath,
        ),
      ),
    )
  }

  private fun setArmFailed(
    pairId: String,
    arm: ExperimentArmId,
    worktreePath: Path,
    failure: Throwable,
  ) {
    pairOwner.save(
      updateArmLifecycle(
        ArmLifecycleUpdate(
          payload = pairOwner.load(pairId)?.pairPayload?.toMap().orEmpty(),
          pairId = pairId,
          arm = arm,
          terminalStatus = "failed",
          worktreePath = worktreePath,
          failureReason = failure.message.orEmpty().ifBlank { failure::class.simpleName.orEmpty() },
        ),
      ),
    )
  }

  private fun finalizeExecution(
    execution: GoalPairExecution,
    finalReport: GoalRunnerRunReport,
  ) {
    val pairId = execution.pairId
    pairOwner.load(pairId)?.pairPayload?.let { payload ->
      pairOwner.saveReport(
        pairId,
        ExperimentPairPayload(
          ExperimentReportProjector.project(payload.toMap(), ExperimentExecutionMode.GOAL_PAIR.wireValue),
        ),
      )
    }
    val controlOutcome =
      pairOwner.load(pairId)?.pairPayload
        ?.get(ExperimentPairPayloadKeys.ARM_OUTCOMES)
        .let { it as? List<*> }
        ?.filterIsInstance<Map<*, *>>()
        ?.firstOrNull { it[ExperimentPairPayloadKeys.ARM_ID] == ExperimentArmId.CONTROL.wireValue }
    val controlWorktree = armWorktreePath(execution.request.repoRoot, pairId, ExperimentArmId.CONTROL)
    val controlCommitSha =
      (gitOperations.headCommitSha(controlWorktree) as? WorkflowGitOperationResult.Ok)
        ?.value?.trim()?.takeIf(String::isNotBlank)
    val publication =
      parentDelivery.reconcile(
        pairId = pairId,
        controlWorkflowId =
          controlOutcome?.get(ExperimentPairPayloadKeys.WORKFLOW_ID)?.toString()
            ?: "$pairId:${ExperimentArmId.CONTROL.wireValue}",
        controlCommitSha = controlCommitSha,
        controlCompleted = controlOutcome?.get(ExperimentPairPayloadKeys.TERMINAL_STATUS) == "completed",
        request =
          ExperimentParentDeliveryRequest(
            issueKey = execution.request.issueKey,
            controlRepoRoot = controlWorktree,
          ),
      )
    updateDeliveryState(pairId, publication)
    telemetryRecorder?.record(
      pairId = pairId,
      cohort = ExperimentExecutionMode.GOAL_PAIR.wireValue,
      metrics =
        mapOf(
          ExperimentTelemetryPayloadKeys.PAIR_STATUS to
            pairOwner.load(pairId)
              ?.pairPayload?.get(ExperimentPairPayloadKeys.PAIR_STATUS),
          ExperimentTelemetryPayloadKeys.ARM_COUNT to
            pairOwner.load(pairId)?.pairPayload?.get(ExperimentPairPayloadKeys.ARM_OUTCOMES)
              .let { (it as? List<*>)?.size ?: 0 },
          ExperimentTelemetryPayloadKeys.DELIVERED_FEATURE_COUNT to
            if (finalReport is GoalRunnerRunReport.Completed) 1 else 0,
        ),
    )
  }

  private fun updateDeliveryState(
    pairId: String,
    publication: ExperimentPublicationResult,
  ) {
    pairOwner.load(pairId)?.let { state ->
      val payload = state.pairPayload.toMap().toMutableMap()
      payload[ExperimentPairPayloadKeys.DELIVERY_STATUS] =
        when {
          publication.published -> "published"
          publication.reason != null -> "blocked"
          else -> "deferred"
        }
      publication.reason?.let { reason ->
        val existing =
          (payload[ExperimentReportPayloadKeys.EXCLUSION_REASONS] as? List<*>)
            .orEmpty()
            .map { it.toString() }
        payload[ExperimentReportPayloadKeys.EXCLUSION_REASONS] = (existing + reason).distinct()
      }
      pairOwner.save(state.copy(pairPayload = ExperimentPairPayload(payload)))
    }
  }

  private fun reconstructedGoalReport(
    issueKey: String,
    pairId: String,
  ): GoalRunnerRunReport {
    val outcomes =
      (pairOwner.load(pairId)?.pairPayload?.get(ExperimentPairPayloadKeys.ARM_OUTCOMES) as? List<*>)
        .orEmpty()
        .filterIsInstance<Map<*, *>>()
    val completed = outcomes.count { it[ExperimentPairPayloadKeys.TERMINAL_STATUS] == "completed" }
    if (completed == 2) {
      return GoalRunnerRunReport.Completed(
        issueKey = issueKey,
        attemptedSubtasks = emptyList(),
        pullRequestUrl = null,
        pullRequestStatus = GoalPullRequestStatus.EXISTING,
        subtasksCompleted = 0,
        subtasksPending = 0,
        subtasksBlocked = 0,
        parentWorkflowId =
          outcomes.firstOrNull {
            it[ExperimentPairPayloadKeys.ARM_ID] == ExperimentArmId.CONTROL.wireValue
          }?.get(ExperimentPairPayloadKeys.WORKFLOW_ID)?.toString(),
      )
    }
    throw ExperimentIsolationCapabilityRefusalError(
      "Experiment pair $pairId has no resumable arm and no complete durable outcome.",
    )
  }

  private fun drawArmOrder(): ArmSelection {
    val seed = random.nextLong()
    val first = if ((seed and 1L) == 0L) ExperimentArmId.CONTROL else ExperimentArmId.TREATMENT
    val second = if (first == ExperimentArmId.CONTROL) ExperimentArmId.TREATMENT else ExperimentArmId.CONTROL
    return ArmSelection(listOf(first, second), seed.toString())
  }

  private fun verifyFrozenSourceIdentity(
    repoRoot: Path,
    pairId: String,
    issueKey: String,
    payload: Map<String, Any?>?,
  ) {
    val frozen =
      payload?.get(ExperimentPairPayloadKeys.FROZEN_INPUT_IDENTITY) as? Map<*, *>
        ?: throw ExperimentDirtySourceRefusalError(listOf("experiment pair has no frozen input identity"))
    val currentCommit =
      (gitOperations.headCommitSha(repoRoot) as? WorkflowGitOperationResult.Ok)
        ?.value?.trim().orEmpty()
    val currentRepository =
      (gitOperations.repositoryFingerprint(repoRoot) as? WorkflowGitOperationResult.Ok)
        ?.value?.trim().orEmpty()
    val currentTree = sourceTreeIdentity(repoRoot, pairId)
    val currentSpecHash = specBundleHash(repoRoot, issueKey)
    if (!sourceIdentityMatches(
        SourceIdentitySnapshot(
          frozen,
          currentCommit,
          currentRepository,
          currentTree,
          currentSpecHash,
          repoRoot,
          issueKey,
        ),
      )
    ) {
      throw ExperimentDirtySourceRefusalError(
        dirtyPathsOutsidePreparedSpec(repoRoot, issueKey)
          .ifEmpty { listOf("source identity changed after experiment pair capture") },
      )
    }
  }

  private fun sourceIdentityMatches(snapshot: SourceIdentitySnapshot): Boolean =
    snapshot.currentCommit == snapshot.frozen[ExperimentPairPayloadKeys.SOURCE_COMMIT_SHA]?.toString() &&
      snapshot.currentRepository == snapshot.frozen[ExperimentPairPayloadKeys.REPOSITORY_IDENTITY]?.toString() &&
      snapshot.currentTree == snapshot.frozen[ExperimentPairPayloadKeys.SOURCE_TREE_SHA]?.toString() &&
      snapshot.currentSpecHash == snapshot.frozen[ExperimentPairPayloadKeys.SPEC_BUNDLE_HASH]?.toString() &&
      dirtyPathsOutsidePreparedSpec(snapshot.repoRoot, snapshot.issueKey).isEmpty()

  private fun frozenPairPayload(
    request: GoalRunnerRunRequest,
    pairId: String,
    selection: ExperimentLaunchSelection,
    armOrder: List<ExperimentArmId>,
    randomSeed: String,
  ): Map<String, Any?> {
    val sourceCommit =
      (gitOperations.headCommitSha(request.repoRoot) as? WorkflowGitOperationResult.Ok)
        ?.value?.trim().orEmpty()
    val repositoryIdentity =
      (
        gitOperations.repositoryFingerprint(request.repoRoot)
          as? WorkflowGitOperationResult.Ok
      )?.value?.trim().orEmpty()
    val sourceTree = sourceTreeIdentity(request.repoRoot, pairId)
    if (sourceCommit.isBlank() || repositoryIdentity.isBlank() || sourceTree.isBlank()) {
      throw ExperimentIsolationCapabilityRefusalError(
        "Could not capture source commit, source tree, and repository identity before the experiment pair started.",
      )
    }
    val specBundleHash = specBundleHash(request.repoRoot, request.issueKey)
    return linkedMapOf(
      ExperimentPairPayloadKeys.CONTRACT_VERSION to EXPERIMENT_PAIR_CONTRACT_VERSION,
      ExperimentPairPayloadKeys.PAIR_ID to pairId,
      ExperimentPairPayloadKeys.EXECUTION_MODE to ExperimentExecutionMode.GOAL_PAIR.wireValue,
      ExperimentPairPayloadKeys.SELECTED_EXPERIMENT_NAMES to selection.normalizedNames,
      ExperimentPairPayloadKeys.ARM_ORDER to armOrder.map { it.wireValue },
      ExperimentPairPayloadKeys.RANDOM_SEED to randomSeed,
      ExperimentPairPayloadKeys.DELIVERY_ARM to ExperimentArmId.CONTROL.wireValue,
      ExperimentPairPayloadKeys.PAIR_STATUS to "pending",
      ExperimentPairPayloadKeys.DELIVERY_STATUS to "deferred",
      ExperimentPairPayloadKeys.FROZEN_INPUT_IDENTITY to
        mapOf(
          ExperimentPairPayloadKeys.REPOSITORY_IDENTITY to repositoryIdentity,
          ExperimentPairPayloadKeys.SOURCE_COMMIT_SHA to sourceCommit,
          ExperimentPairPayloadKeys.SOURCE_TREE_SHA to sourceTree,
          ExperimentPairPayloadKeys.SPEC_BUNDLE_HASH to specBundleHash,
          ExperimentPairPayloadKeys.EFFECTIVE_CONFIG_HASH to
            sha256Hex(
              (selection.normalizedNames.joinToString(",") + "\u0000" + selection.availabilitySummary)
                .encodeToByteArray(),
            ),
          ExperimentPairPayloadKeys.RUN_SETTINGS_HASH to
            sha256Hex(
              "${request.timeout}|${request.progressIdleTimeout}|${request.planningBudget}".encodeToByteArray(),
            ),
          ExperimentPairPayloadKeys.PHASE_ROUTES_HASH to
            sha256Hex(
              "${request.codeReviewMode}|${request.stopAfterSubtaskId}".encodeToByteArray(),
            ),
          ExperimentPairPayloadKeys.PACK_ADDON_HASH to
            sha256Hex(
              request.agentAddonSelection.entries.joinToString("|").encodeToByteArray(),
            ),
          ExperimentPairPayloadKeys.PROVIDER_MODEL_EFFORT to
            "${request.invokedAgentId}:${request.configuredAgentOverrideId.orEmpty()}:default",
          ExperimentPairPayloadKeys.SKILL_BILL_VERSION to "runtime",
        ),
      ExperimentPairPayloadKeys.ARM_OUTCOMES to emptyList<Any>(),
    )
  }

  private fun updateArmOutcome(
    payload: Map<String, Any?>,
    pairId: String,
    arm: ExperimentArmId,
    report: GoalRunnerRunReport,
    worktreePath: Path,
  ): ExperimentPairPersistedState {
    return updateArmLifecycle(
      ArmLifecycleUpdate(
        payload = payload,
        pairId = pairId,
        arm = arm,
        terminalStatus = report.armTerminalStatus(),
        workflowId = report.parentWorkflowId ?: "$pairId:${arm.wireValue}",
        worktreePath = worktreePath,
      ),
    )
  }

  private fun updateArmLifecycle(update: ArmLifecycleUpdate): ExperimentPairPersistedState {
    val payload = update.payload
    val pairId = update.pairId
    val arm = update.arm
    val terminalStatus = update.terminalStatus
    val workflowId = update.workflowId
    val worktreePath = update.worktreePath
    val failureReason = update.failureReason
    val outcomes =
      (payload[ExperimentPairPayloadKeys.ARM_OUTCOMES] as? List<*>)
        ?.filterIsInstance<Map<*, *>>()
        ?.map { it.entries.associate { entry -> entry.key.toString() to entry.value } }
        ?.filterNot { it[ExperimentPairPayloadKeys.ARM_ID] == arm.wireValue }
        ?.toMutableList()
        ?: mutableListOf()
    outcomes +=
      mapOf(
        ExperimentPairPayloadKeys.ARM_ID to arm.wireValue,
        ExperimentPairPayloadKeys.WORKFLOW_ID to workflowId,
        ExperimentPairPayloadKeys.WORKTREE_PATH to worktreePath.toString(),
        ExperimentPairPayloadKeys.TERMINAL_STATUS to terminalStatus,
        ExperimentPairPayloadKeys.DEFERRED_PUBLICATION to true,
        ExperimentPairPayloadKeys.FAILURE_REASON to failureReason,
      ).filterValues { it != null }
    val updated = payload.toMutableMap()
    updated[ExperimentPairPayloadKeys.ARM_OUTCOMES] = outcomes
    updated[ExperimentPairPayloadKeys.PAIR_STATUS] =
      when {
        outcomes.any { it[ExperimentPairPayloadKeys.TERMINAL_STATUS] == "paused" } -> "paused"
        outcomes.any { it[ExperimentPairPayloadKeys.TERMINAL_STATUS] == "failed" } -> "failed"
        outcomes.size == 2 &&
          outcomes.all {
            it[ExperimentPairPayloadKeys.TERMINAL_STATUS] == "completed"
          }
        -> "completed"
        else -> "running"
      }
    return ExperimentPairPersistedState(
      pairId = pairId,
      executionMode = ExperimentExecutionMode.GOAL_PAIR,
      selectedNames =
        (updated[ExperimentPairPayloadKeys.SELECTED_EXPERIMENT_NAMES] as? List<*>)
          ?.map { it.toString() }.orEmpty(),
      armOrder =
        (updated[ExperimentPairPayloadKeys.ARM_ORDER] as? List<*>)
          ?.mapNotNull { ExperimentArmId.fromWire(it.toString()) }
          .orEmpty(),
      randomSeed = updated[ExperimentPairPayloadKeys.RANDOM_SEED]?.toString().orEmpty(),
      pairPayload = ExperimentPairPayload(updated),
    )
  }

  private fun failedArmReport(
    issueKey: String,
    pairId: String,
    arm: ExperimentArmId,
    failure: Throwable,
  ): GoalRunnerRunReport.Stopped =
    GoalRunnerRunReport.Stopped(
      issueKey = issueKey,
      attemptedSubtasks = emptyList(),
      stop =
        GoalRunnerStopReport(
          issueKey = issueKey,
          subtaskId = 0,
          reason = GoalRunnerStopReason.FAILED,
          blockedReason = "Experiment ${arm.wireValue} arm failed: ${failure.message.orEmpty()}",
          workflowId = "$pairId:${arm.wireValue}",
          lastResumableStep = "plan",
        ),
    )

  private fun ensureArmWorktree(
    repositoryRoot: Path,
    worktreePath: Path,
    pairId: String,
    arm: ExperimentArmId,
  ) {
    if (Files.isDirectory(worktreePath)) return
    Files.createDirectories(requireNotNull(worktreePath.parent))
    val baseRef =
      (gitOperations.headCommitSha(repositoryRoot) as? WorkflowGitOperationResult.Ok)
        ?.value?.trim().orEmpty()
    if (baseRef.isBlank()) {
      throw ExperimentIsolationCapabilityRefusalError(
        "Could not resolve the frozen base commit for experiment arm ${arm.wireValue}.",
      )
    }
    gitOperations.linkedWorktreeOperations.addLinkedWorktree(
      LinkedWorktreeAddRequest(
        repositoryRoot = repositoryRoot,
        worktreePath = worktreePath,
        branchName = "skill-bill/experiments/${safePairId(pairId)}/${arm.wireValue}",
        baseRef = baseRef,
      ),
    )
  }

  private fun armWorktreePath(
    repositoryRoot: Path,
    pairId: String,
    arm: ExperimentArmId,
  ): Path =
    requireNotNull(repositoryRoot.toAbsolutePath().normalize().parent)
      .resolve(".skill-bill-experiments")
      .resolve(safePairId(pairId))
      .resolve(arm.wireValue)

  private fun statePaths(
    repositoryRoot: Path,
    armWorktree: Path,
  ): ExperimentArmStatePaths =
    ExperimentArmStatePaths(
      runtimeDatabase = armWorktree.resolve(".skill-bill/runtime.db"),
      learningStore = armWorktree.resolve(".skill-bill/learning"),
      graphIndex = armWorktree.resolve(".skill-bill/graph-index"),
      buildOutput = armWorktree.resolve("build"),
      writableCache = armWorktree.resolve(".skill-bill/cache"),
      worktreeEditJournal = armWorktree.resolve(".skill-bill/edit-journal"),
      sharedHostCaches =
        listOf(
          repositoryRoot.resolve(".gradle"),
          repositoryRoot.resolve(".skill-bill/shared-caches"),
        ),
    )

  private fun prepareArmStatePaths(paths: ExperimentArmStatePaths) {
    listOfNotNull(
      paths.runtimeDatabase?.parent,
      paths.learningStore,
      paths.graphIndex,
      paths.buildOutput,
      paths.writableCache,
      paths.worktreeEditJournal,
    ).forEach { path -> Files.createDirectories(path) }
  }

  private fun safePairId(pairId: String): String = pairId.replace(Regex("[^A-Za-z0-9._-]"), "-")

  private fun sourceTreeIdentity(
    repositoryRoot: Path,
    pairId: String,
  ): String {
    val branch =
      (gitOperations.currentBranch(repositoryRoot) as? WorkflowGitOperationResult.Ok)
        ?.value?.trim().orEmpty()
    return if (branch.isBlank()) {
      ""
    } else {
      gitOperations.resolveReadinessTreeIdentity(
        repoRoot = repositoryRoot,
        baseBranch = branch,
        workflowId = "experiment:$pairId",
      )?.sourceTreeSha.orEmpty()
    }
  }

  private fun specBundleHash(
    repositoryRoot: Path,
    issueKey: String,
  ): String {
    val specRoot = requireNotNull(specBundleRoot(repositoryRoot, issueKey))
    if (!Files.isDirectory(specRoot)) {
      throw ExperimentIsolationCapabilityRefusalError(
        "Could not locate the prepared specification bundle for $issueKey.",
      )
    }
    val digest = MessageDigest.getInstance("SHA-256")
    Files.walk(specRoot).use { paths ->
      paths
        .filter(Files::isRegularFile)
        .sorted()
        .forEach { path ->
          digest.update(specRoot.relativize(path).toString().replace('\\', '/').encodeToByteArray())
          digest.update(0.toByte())
          digest.update(Files.readAllBytes(path))
          digest.update(0.toByte())
        }
    }
    return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
  }

  private fun specBundleRoot(
    repositoryRoot: Path,
    issueKey: String,
  ): Path? {
    val root = repositoryRoot.resolve(".feature-specs").normalize()
    val exact = root.resolve(issueKey)
    if (Files.isDirectory(exact)) return exact
    if (!Files.isDirectory(root)) return null
    return Files.list(root).use { paths ->
      paths
        .filter(Files::isDirectory)
        .filter { path -> path.fileName.toString().startsWith("$issueKey-") }
        .findFirst()
        .orElse(null)
    }
  }

  private fun copyFrozenSpecBundle(
    repositoryRoot: Path,
    armRoot: Path,
    issueKey: String,
  ) {
    val source =
      specBundleRoot(repositoryRoot, issueKey)
        ?: throw ExperimentIsolationCapabilityRefusalError("Prepared specification bundle for $issueKey is missing.")
    val relative = repositoryRoot.relativize(source)
    val destination = armRoot.resolve(relative)
    ExperimentFrozenSpecBundle.copy(source, destination)
  }

  private fun dirtyPathsOutsidePreparedSpec(
    repositoryRoot: Path,
    issueKey: String,
  ): List<String> {
    val result = gitOperations.dirtyImplementationPaths(repositoryRoot)
    return when (result) {
      is DirtyPathsError -> listOf(result.reason)
      is DirtyPaths -> result.paths.filterNot { path -> isPreparedSpecPath(repositoryRoot, issueKey, path) }
    }
  }

  private fun isPreparedSpecPath(
    repositoryRoot: Path,
    issueKey: String,
    path: String,
  ): Boolean {
    val root = specBundleRoot(repositoryRoot, issueKey) ?: return false
    val relativeRoot = repositoryRoot.relativize(root).toString().replace('\\', '/')
    return path == relativeRoot || path.startsWith("$relativeRoot/")
  }

  private fun observationMeasurements(measurements: ExperimentArmMeasurement): List<ExperimentObservationMeasurement> =
    listOf(
      ExperimentObservationMeasurement(
        metricId = "attempt_count",
        quantity = 1.0,
        availability = TelemetryMeasurementAvailability.MEASURED.wireValue,
      ),
      ExperimentObservationMeasurement(
        metricId = "setup_cost",
        quantity = measurements.setupCost.quantity,
        availability = measurements.setupCost.availability,
        reason = measurements.setupCost.reason,
      ),
      ExperimentObservationMeasurement(
        metricId = "usage",
        quantity = measurements.usage.quantity,
        availability = measurements.usage.availability,
        reason = measurements.usage.reason,
      ),
      ExperimentObservationMeasurement(
        metricId = "cost",
        quantity = measurements.cost.quantity,
        availability = measurements.cost.availability,
        reason = measurements.cost.reason,
      ),
    )

  private fun recordArmObservation(
    pairId: String,
    arm: ExperimentArmId,
    workflowId: String,
  ) {
    val measurements = measurementPort?.measure(pairId, arm.wireValue, workflowId) ?: unavailableMeasurement()
    ExperimentObservationRecorder(pairOwner).record(
      ExperimentObservationRecordRequest(
        pairId = pairId,
        armId = arm.wireValue,
        workflowId = workflowId,
        phaseId = "goal",
        attempt = 1,
        recordedAt = Instant.now(clock).toString(),
        measurements = observationMeasurements(measurements),
      ),
    )
  }

  private fun unavailableMeasurement(): ExperimentArmMeasurement =
    ExperimentArmMeasurement(
      setupCost = unavailableValue("provider setup cost was not recorded"),
      usage = unavailableValue("provider usage was not recorded"),
      cost = unavailableValue("provider cost was not recorded"),
    )

  private fun unavailableValue(reason: String): ExperimentMeasuredValue =
    ExperimentMeasuredValue(
      availability = TelemetryMeasurementAvailability.UNAVAILABLE_INCOMPLETE.wireValue,
      reason = reason,
    )

  private fun armOutcome(
    payload: Map<String, Any?>?,
    arm: ExperimentArmId,
  ): Map<String, Any?>? =
    (payload?.get(ExperimentPairPayloadKeys.ARM_OUTCOMES) as? List<*>)
      ?.filterIsInstance<Map<*, *>>()
      ?.map { outcome -> outcome.entries.associate { entry -> entry.key.toString() to entry.value } }
      ?.firstOrNull { outcome -> outcome[ExperimentPairPayloadKeys.ARM_ID] == arm.wireValue }
}

private fun ExperimentPairPayload.toMap(): Map<String, Any?> =
  JsonCodec.anyToStringAnyMap(
    JsonCodec.jsonElementToValue(requireNotNull(JsonCodec.parseObjectOrNull(toJson()))),
  ) ?: error("Experiment pair payload must decode to an object.")

internal fun GoalRunnerRunReport.shouldPausePair(): Boolean =
  this is GoalRunnerRunReport.Stopped &&
    stop.reason in GoalRunnerStopReason.RESUMABLE_STOP_REASONS

internal fun GoalRunnerRunReport.armTerminalStatus(): String =
  when (this) {
    is GoalRunnerRunReport.Completed -> "completed"
    is GoalRunnerRunReport.Stopped ->
      if (stop.reason in GoalRunnerStopReason.RESUMABLE_STOP_REASONS) "paused" else "failed"
  }

private data class ArmSelection(
  val order: List<ExperimentArmId>,
  val seed: String,
)

private fun sha256Hex(bytes: ByteArray): String =
  MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { byte ->
    "%02x".format(byte)
  }
