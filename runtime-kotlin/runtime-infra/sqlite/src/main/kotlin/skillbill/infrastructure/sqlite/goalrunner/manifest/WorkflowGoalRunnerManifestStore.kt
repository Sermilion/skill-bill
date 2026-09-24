package skillbill.infrastructure.sqlite.goalrunner.manifest
import me.tatarka.inject.annotations.Inject
import skillbill.goalrunner.model.GoalPlanningStatusSnapshot
import skillbill.goalrunner.model.GoalRunnerControlState
import skillbill.goalrunner.model.GoalRunnerExecutionLease
import skillbill.infrastructure.sqlite.goalrunner.control.GoalRunnerControlCoordinator
import skillbill.infrastructure.sqlite.goalrunner.control.acquireExecutionLease
import skillbill.infrastructure.sqlite.goalrunner.control.bindRepositoryIdentity
import skillbill.infrastructure.sqlite.goalrunner.control.executionLease
import skillbill.infrastructure.sqlite.goalrunner.control.heartbeatExecutionLease
import skillbill.infrastructure.sqlite.goalrunner.control.persistStopAfterSubtask
import skillbill.infrastructure.sqlite.goalrunner.control.planningSpawnAuthorization
import skillbill.infrastructure.sqlite.goalrunner.control.releaseExecutionLease
import skillbill.infrastructure.sqlite.goalrunner.control.releaseExecutionLeaseIfExpired
import skillbill.infrastructure.sqlite.goalrunner.control.requestPause
import skillbill.infrastructure.sqlite.goalrunner.control.requestPauseByIssueKey
import skillbill.infrastructure.sqlite.goalrunner.control.resume
import skillbill.infrastructure.sqlite.goalrunner.outcome.WorkflowGoalRunnerChildWorkflowPersistence
import skillbill.infrastructure.sqlite.goalrunner.outcome.WorkflowGoalRunnerScopedReplanPersistence
import skillbill.infrastructure.sqlite.goalrunner.outcome.afterIncompatibleChildDeletion
import skillbill.model.RepositoryRoot
import skillbill.ports.agentrun.model.AgentRunSpawnAuthorization
import skillbill.ports.db.DatabaseSessionFactory
import skillbill.ports.decomposition.DecompositionManifestProjectionWriter
import skillbill.ports.goalrunner.persistence.GoalChildPlanningHydratorPort
import skillbill.ports.goalrunner.runner.GoalRunnerManifestStore
import skillbill.ports.goalrunner.runner.model.GoalRunnerChildWorkflowSetup
import skillbill.ports.goalrunner.runner.model.GoalRunnerCompletionPersistenceResult
import skillbill.ports.goalrunner.runner.model.GoalRunnerLaunchAuthorization
import skillbill.ports.goalrunner.runner.model.GoalRunnerManifestState
import skillbill.ports.goalrunner.runner.model.GoalRunnerOutOfBandAcceptance
import skillbill.ports.goalrunner.runner.model.GoalRunnerPausePersistenceResult
import skillbill.ports.goalrunner.runner.model.GoalRunnerReviewPolicy
import skillbill.ports.goalrunner.runner.model.GoalRunnerScopedReplanOptions
import skillbill.ports.goalrunner.runner.model.GoalRunnerScopedReplanWriteResult
import skillbill.ports.persistence.UnitOfWork
import skillbill.ports.repository.RepositoryEnclosingRootPort
import skillbill.ports.workflow.decomposition.DecompositionManifestStore
import skillbill.ports.workflow.get
import skillbill.ports.workflow.model.WorkflowFamily
import skillbill.review.context.model.launch.CodeReviewExecutionMode
import skillbill.workflow.decomposition.DecompositionManifestValidator
import skillbill.workflow.decomposition.runtime.model.DecompositionManifestProjectionOutcome
import skillbill.workflow.engine.WorkflowEngine
import skillbill.workflow.engine.model.DurableWorkflowArtifacts
import skillbill.ports.workflow.WorkflowSnapshotValidator
import java.nio.file.Path
import java.time.Clock

class WorkflowGoalRunnerManifestStore
  @Inject
  constructor(
    private val database: DatabaseSessionFactory,
    workflowSnapshotValidator: WorkflowSnapshotValidator,
    private val decompositionManifestValidator: DecompositionManifestValidator,
    private val decompositionManifestStore: DecompositionManifestStore,
    private val clock: Clock,
    private val decompositionManifestWriter: DecompositionManifestProjectionWriter,
    private val repositoryRoot: RepositoryRoot,
    private val planningHydrator: GoalChildPlanningHydratorPort,
    private val repositoryEnclosingRootPort: RepositoryEnclosingRootPort,
  ) : GoalRunnerManifestStore {
    private val engine: WorkflowEngine = WorkflowEngine()
    private val parentProjection = GoalParentProjectionWriter(engine, decompositionManifestValidator)
    private val manifestLoader =
      WorkflowGoalRunnerManifestLoader(
        database,
        decompositionManifestValidator,
        decompositionManifestStore,
        engine,
        parentProjection,
        clock,
      )
    private val projectionPersistence =
      WorkflowGoalRunnerManifestProjectionPersistence(
        database,
        engine,
        parentProjection,
        decompositionManifestValidator,
      )
    private val childWorkflowPersistence =
      WorkflowGoalRunnerChildWorkflowPersistence(
        engine,
        planningHydrator,
        parentProjection,
        decompositionManifestValidator,
      )
    private val scopedReplanPersistence = WorkflowGoalRunnerScopedReplanPersistence(projectionPersistence)
    private val controls =
      GoalRunnerControlCoordinator(
        database,
        decompositionManifestValidator,
        clock,
        repositoryEnclosingRootPort,
      ) { unitOfWork, state ->
        projectionPersistence.saveInTransaction(unitOfWork, state)
      }

    override fun loadByIssueKey(
      issueKey: String,
      repoRoot: Path?,
    ): GoalRunnerManifestState? {
      val projected = repoRoot?.let { root -> manifestLoader.findProjectedManifest(root, issueKey) }
      val stored = manifestLoader.loadFromWorkflowStore(issueKey, projected)
      if (manifestLoader.shouldRefreshFromCompleteProjection(stored, projected)) {
        return save(
          requireNotNull(stored).copy(manifest = requireNotNull(projected), repoRoot = repoRoot),
        )
      }
      return stored?.copy(repoRoot = repoRoot) ?: projected?.let { manifest ->
        manifestLoader.importFromManifestProjection(manifest)?.copy(repoRoot = repoRoot)
      }
    }

    override fun readByIssueKey(
      issueKey: String,
      repoRoot: Path?,
    ): GoalRunnerManifestState? {
      val projected = repoRoot?.let { root -> manifestLoader.findProjectedManifest(root, issueKey) }
      val stored = manifestLoader.loadFromWorkflowStore(issueKey, projected)
      return manifestLoader.readProjection(stored, projected, repoRoot)
    }

    override fun readByIssueKeyIfPresent(
      issueKey: String,
      repoRoot: Path?,
    ): GoalRunnerManifestState? {
      val projected =
        repoRoot?.let { root ->
          manifestLoader.findProjectedManifest(root, issueKey, recoverPending = false)
        }
      val stored = manifestLoader.loadFromWorkflowStoreIfPresent(issueKey, projected)
      return manifestLoader.readProjection(stored, projected, repoRoot)
    }

    override fun loadDurableByIssueKey(issueKey: String): GoalRunnerManifestState? =
      manifestLoader.loadFromWorkflowStore(issueKey, currentProjectedManifest = null)

    override fun requestPause(parentWorkflowId: String): GoalRunnerControlState? =
      controls.requestPause(
        parentWorkflowId,
      )

    override fun pauseNow(
      parentWorkflowId: String,
      reason: String,
      pausedAt: String,
      overwriteExistingReason: Boolean,
    ): GoalRunnerControlState? = controls.pauseNow(parentWorkflowId, reason, pausedAt, overwriteExistingReason)

    override fun requestPauseByIssueKey(
      issueKey: String,
      repoRoot: Path?,
    ): GoalRunnerPausePersistenceResult? = controls.requestPauseByIssueKey(issueKey, repoRoot)

    override fun resume(parentWorkflowId: String): GoalRunnerManifestState? = controls.resume(parentWorkflowId)

    override fun pauseAtBoundary(state: GoalRunnerManifestState): GoalRunnerManifestState =
      controls.pauseAtBoundary(state)

    override fun executionLease(parentWorkflowId: String): GoalRunnerExecutionLease? =
      controls.executionLease(parentWorkflowId)

    override fun acquireExecutionLease(
      parentWorkflowId: String,
      lease: GoalRunnerExecutionLease,
      expectedOwnerToken: String?,
    ): Boolean = controls.acquireExecutionLease(parentWorkflowId, lease, expectedOwnerToken)

    override fun heartbeatExecutionLease(
      parentWorkflowId: String,
      lease: GoalRunnerExecutionLease,
    ): Boolean = controls.heartbeatExecutionLease(parentWorkflowId, lease)

    override fun releaseExecutionLease(
      parentWorkflowId: String,
      ownerToken: String,
      generation: Long,
    ): Boolean = controls.releaseExecutionLease(parentWorkflowId, ownerToken, generation)

    override fun releaseExecutionLeaseIfExpired(
      parentWorkflowId: String,
      ownerToken: String,
      generation: Long,
      nowInstant: String,
    ): Boolean =
      controls.releaseExecutionLeaseIfExpired(
        parentWorkflowId,
        ownerToken,
        generation,
        nowInstant,
      )

    override fun controlState(parentWorkflowId: String): GoalRunnerControlState =
      controls.controlState(
        parentWorkflowId,
      )

    override fun persistControlState(
      parentWorkflowId: String,
      state: GoalRunnerControlState,
    ): GoalRunnerControlState = controls.persistControlState(parentWorkflowId, state)

    override fun clearRunnerInterruptedPause(parentWorkflowId: String): GoalRunnerControlState =
      controls.clearRunnerInterruptedPause(parentWorkflowId)

    override fun bindRepositoryIdentity(
      parentWorkflowId: String,
      repositoryIdentity: String,
    ): GoalRunnerControlState = controls.bindRepositoryIdentity(parentWorkflowId, repositoryIdentity)

    override fun authorizeSubtaskLaunch(
      state: GoalRunnerManifestState,
      subtaskId: Int,
    ): GoalRunnerLaunchAuthorization = controls.authorizeSubtaskLaunch(state, subtaskId)

    override fun authorizePlanningLaunch(parentWorkflowId: String): AgentRunSpawnAuthorization? =
      controls.planningSpawnAuthorization(parentWorkflowId)

    override fun persistStopAfterSubtask(
      parentWorkflowId: String,
      subtaskId: Int,
    ): GoalRunnerControlState = controls.persistStopAfterSubtask(parentWorkflowId, subtaskId)

    override fun planningStatus(
      parentWorkflowId: String,
      orderedSubtaskIds: List<Int>,
      blockedSubtaskId: Int?,
      blockedReason: String?,
    ): GoalPlanningStatusSnapshot? =
      database.read {
        it.goalPlanningPreparations.boundedStatus(
          parentWorkflowId,
          orderedSubtaskIds,
          blockedSubtaskId,
          blockedReason,
        )
      }

    override fun save(state: GoalRunnerManifestState): GoalRunnerManifestState {
      val saved = projectionPersistence.save(state)
      writeProjectionFile(state, saved.projectionArtifacts)
      return saved.state
    }

    override fun saveRuntimeState(state: GoalRunnerManifestState): GoalRunnerManifestState =
      projectionPersistence.save(state).state

    override fun saveCompletedSubtaskAtBoundary(
      state: GoalRunnerManifestState,
      subtaskId: Int,
    ): GoalRunnerCompletionPersistenceResult = controls.saveCompletedSubtaskAtBoundary(state, subtaskId)

    override fun saveHardReset(
      state: GoalRunnerManifestState,
      preservePlanning: Boolean,
    ): GoalRunnerManifestState {
      val saved =
        database.transaction { unitOfWork ->
          if (!preservePlanning) unitOfWork.goalPlanningPreparations.deleteByGoal(state.parentWorkflowId)
          unitOfWork.workflowStates.deleteGoalChildWorkflowsByParent(state.parentWorkflowId)
          val repositoryIdentity = unitOfWork.goalRunnerControls.controlState(state.parentWorkflowId).repositoryIdentity
          val projection =
            projectionPersistence.saveInTransaction(
              unitOfWork,
              state,
              clearOutOfBandAcceptances = true,
              mergeConcurrentProgress = false,
            )
          if (repositoryIdentity != null) {
            unitOfWork.goalRunnerControls.persistControlState(
              projection.state.parentWorkflowId,
              projection.state.controlState.copy(repositoryIdentity = repositoryIdentity),
            )
          }
          projection.copy(
            state =
              projection.state.copy(
                controlState = projection.state.controlState.copy(repositoryIdentity = repositoryIdentity),
              ),
          )
        }
      writeProjectionFile(state, saved.projectionArtifacts)
      return saved.state
    }

    override fun deleteIncompatibleChildWorkflow(
      state: GoalRunnerManifestState,
      subtaskId: Int,
      workflowId: String,
    ): GoalRunnerManifestState {
      val saved =
        database.transaction { unitOfWork ->
          val selected =
            state.manifest.subtasks.singleOrNull { it.id == subtaskId }
              ?: error("Unknown or ambiguous goal subtask '$subtaskId'.")
          require(selected.workflowId == workflowId) {
            "Selected subtask '$subtaskId' does not own child workflow '$workflowId'."
          }
          val deleted =
            unitOfWork.workflowStates.deleteGoalChildWorkflow(
              state.parentWorkflowId,
              subtaskId,
              workflowId,
            )
          require(deleted == 1) {
            "Child workflow '$workflowId' is absent, compatible, or not owned by subtask '$subtaskId'."
          }
          val recoveredManifest = state.manifest.afterIncompatibleChildDeletion(subtaskId)
          projectionPersistence.saveInTransaction(unitOfWork, state.copy(manifest = recoveredManifest))
        }
      writeProjectionFile(state, saved.projectionArtifacts)
      return saved.state
    }

    override fun saveScopedReplan(
      state: GoalRunnerManifestState,
      subtaskId: Int,
      options: GoalRunnerScopedReplanOptions,
    ): GoalRunnerScopedReplanWriteResult {
      val saved =
        database.transaction { unitOfWork ->
          scopedReplanPersistence.executeScopedReplan(unitOfWork, state, subtaskId, options)
        }
      writeProjectionFile(state, saved.second)
      return saved.first
    }

    override fun sharedPreplanPayloadSha256(parentWorkflowId: String): String? =
      database.read {
        it.goalPlanningPreparations.sharedPreplanPayloadSha256(parentWorkflowId)
      }

    override fun saveNewChildWorkflow(
      state: GoalRunnerManifestState,
      setup: GoalRunnerChildWorkflowSetup,
    ): GoalRunnerManifestState {
      val saved =
        database.transaction { unitOfWork ->
          childWorkflowPersistence.saveInTransaction(unitOfWork, state, setup)
        }
      writeProjectionFile(state, saved.projectionArtifacts)
      return saved.state
    }

    override fun listOwnedGoalChildWorkflowIds(parentWorkflowId: String): List<String> =
      database.read { it.workflowStates.listGoalChildWorkflowIdsByParent(parentWorkflowId) }

    override fun purgeDecomposedGoal(parentWorkflowId: String) {
      database.transaction { unitOfWork ->
        goalRunnerPurgePersistence().purgeDecomposedGoal(unitOfWork, parentWorkflowId)
      }
    }

    override fun reviewMode(parentWorkflowId: String): CodeReviewExecutionMode? =
      database.read { unitOfWork ->
        unitOfWork.goalRunnerControls.reviewPolicy(parentWorkflowId)?.codeReviewMode
      }

    override fun persistReviewMode(
      parentWorkflowId: String,
      mode: CodeReviewExecutionMode,
    ): CodeReviewExecutionMode =
      database.transaction { unitOfWork ->
        val record =
          WorkflowFamily.TASK_RUNTIME.get(unitOfWork.workflowStates, parentWorkflowId)
            ?: error("Goal parent workflow '$parentWorkflowId' no longer exists.")
        val existing = unitOfWork.goalRunnerControls.reviewPolicy(parentWorkflowId)?.codeReviewMode
        if (existing != null) {
          parentProjection.rewrite(unitOfWork, record)
          existing
        } else {
          unitOfWork.goalRunnerControls.persistReviewPolicy(
            parentWorkflowId,
            GoalRunnerReviewPolicy(codeReviewMode = mode),
          )
          parentProjection.rewrite(unitOfWork, record)
          mode
        }
      }

    override fun reviewPolicy(parentWorkflowId: String): GoalRunnerReviewPolicy? =
      database.read { unitOfWork ->
        unitOfWork.goalRunnerControls.reviewPolicy(parentWorkflowId)
      }

    override fun persistReviewPolicy(
      parentWorkflowId: String,
      policy: GoalRunnerReviewPolicy,
    ): GoalRunnerReviewPolicy =
      database.transaction { unitOfWork ->
        val record =
          WorkflowFamily.TASK_RUNTIME.get(unitOfWork.workflowStates, parentWorkflowId)
            ?: error("Goal parent workflow '$parentWorkflowId' no longer exists.")
        val existing = unitOfWork.goalRunnerControls.reviewPolicy(parentWorkflowId)
        if (existing == policy) {
          parentProjection.rewrite(unitOfWork, record)
          existing
        } else {
          unitOfWork.goalRunnerControls.persistReviewPolicy(parentWorkflowId, policy)
          parentProjection.rewrite(unitOfWork, record)
          policy
        }
      }

    override fun outOfBandAcceptances(parentWorkflowId: String): Map<Int, GoalRunnerOutOfBandAcceptance> =
      database.read { unitOfWork ->
        unitOfWork.goalRunnerControls.outOfBandAcceptances(parentWorkflowId)
      }

    override fun persistOutOfBandAcceptance(
      parentWorkflowId: String,
      acceptance: GoalRunnerOutOfBandAcceptance,
    ): GoalRunnerOutOfBandAcceptance =
      database.transaction { unitOfWork ->
        val record =
          WorkflowFamily.TASK_RUNTIME.get(unitOfWork.workflowStates, parentWorkflowId)
            ?: error("Goal parent workflow '$parentWorkflowId' no longer exists.")
        unitOfWork.goalRunnerControls.persistOutOfBandAcceptance(parentWorkflowId, acceptance)
        parentProjection.rewrite(unitOfWork, record)
        acceptance
      }

    private fun writeProjectionFile(
      state: GoalRunnerManifestState,
      projectionArtifacts: DurableWorkflowArtifacts,
    ): DecompositionManifestProjectionOutcome {
      val outcome =
        decompositionManifestWriter.writeProjectionFromWorkflowState(
          state.repoRoot ?: repositoryRoot.path,
          projectionArtifacts,
          decompositionManifestValidator,
          decompositionManifestStore,
        )
      when (outcome) {
        is DecompositionManifestProjectionOutcome.Failed ->
          database.transaction { unitOfWork ->
            persistDecompositionManifestProjectionFailure(
              engine,
              unitOfWork,
              state.parentWorkflowId,
              outcome,
            )
          }
        is DecompositionManifestProjectionOutcome.Written ->
          database.transaction { unitOfWork ->
            clearDecompositionManifestProjectionFailure(engine, unitOfWork, state.parentWorkflowId)
          }
        DecompositionManifestProjectionOutcome.Absent -> Unit
      }
      return outcome
    }
  }

internal class WorkflowGoalRunnerPurgePersistence {
  fun purgeDecomposedGoal(
    unitOfWork: UnitOfWork,
    parentWorkflowId: String,
  ) {
    unitOfWork.purgeDecomposedGoal(parentWorkflowId)
  }
}

internal fun goalRunnerPurgePersistence(): WorkflowGoalRunnerPurgePersistence = WorkflowGoalRunnerPurgePersistence()
