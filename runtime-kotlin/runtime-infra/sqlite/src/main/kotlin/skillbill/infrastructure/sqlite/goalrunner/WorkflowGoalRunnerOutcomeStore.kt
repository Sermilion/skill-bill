package skillbill.infrastructure.sqlite.goalrunner

import me.tatarka.inject.annotations.Inject
import skillbill.goalrunner.model.GoalRunnerAttemptLedgerSummary
import skillbill.goalrunner.model.GoalRunnerObservabilityRecordRequest
import skillbill.goalrunner.model.GoalRunnerStoredOutcome
import skillbill.goalrunner.model.GoalRunnerSupervisionEvent
import skillbill.goalrunner.model.GoalRunnerWorkerSubtaskRequestOutcome
import skillbill.infrastructure.sqlite.decomposition.decodeArtifacts
import skillbill.ports.db.DatabaseSessionFactory
import skillbill.ports.decomposition.DecompositionManifestProjectionWriter
import skillbill.ports.goalrunner.persistence.GoalRunnerChildRepairRunnerPort
import skillbill.ports.goalrunner.persistence.GoalRunnerChildRepairStore
import skillbill.ports.goalrunner.persistence.model.GoalRunnerChildRepairApplyRequest
import skillbill.ports.goalrunner.persistence.model.GoalRunnerChildRepairApplyResult
import skillbill.ports.goalrunner.persistence.model.GoalRunnerChildWedgeDiagnosis
import skillbill.ports.goalrunner.persistence.model.GoalRunnerChildWedgeDiagnosisRequest
import skillbill.ports.goalrunner.persistence.model.GoalRunnerChildWedgeRepairRequest
import skillbill.ports.goalrunner.persistence.model.GoalSubtaskIdentity
import skillbill.ports.goalrunner.runner.GoalRunnerAttemptLedgerStore
import skillbill.ports.goalrunner.runner.GoalRunnerReviewOutcomeStore
import skillbill.ports.goalrunner.runner.GoalRunnerTerminalOutcomeStore
import skillbill.ports.goalrunner.runner.GoalRunnerWorkflowOutcomeStore
import skillbill.ports.goalrunner.runner.model.GoalRunnerAttemptLedgerRecordRequest
import skillbill.ports.goalrunner.runner.model.GoalRunnerLedgerSequenceWatermarks
import skillbill.ports.goalrunner.runner.model.GoalRunnerProgressEventRecordRequest
import skillbill.ports.goalrunner.runner.model.GoalRunnerReconcileGate
import skillbill.ports.goalrunner.runner.model.GoalRunnerWorkflowProgress
import skillbill.ports.workflow.WorkflowStateRepository
import skillbill.ports.workflow.decomposition.DecompositionManifestStore
import skillbill.ports.workflow.get
import skillbill.ports.workflow.gitops.WorkflowGitOperations
import skillbill.ports.workflow.model.WorkflowFamily
import skillbill.ports.workflow.save
import skillbill.workflow.decomposition.DecompositionManifestValidator
import skillbill.workflow.decomposition.runtime.model.DecompositionManifestProjectionOutcome
import skillbill.workflow.engine.WorkflowEngine
import skillbill.workflow.engine.model.WorkflowArtifactPatch
import skillbill.workflow.engine.model.WorkflowStateSnapshot
import skillbill.workflow.engine.model.WorkflowUpdateInput
import skillbill.workflow.goal.model.GOAL_SUBTASK_REVIEW_STATE_ARTIFACT_KEY
import skillbill.workflow.goal.model.GoalProgressEvent
import skillbill.workflow.goal.model.GoalSubtaskReviewPassResult
import skillbill.workflow.goal.model.GoalSubtaskReviewState
import skillbill.workflow.taskruntime.FeatureTaskRuntimePhaseOutputValidator
import java.nio.file.Path

internal data class RecoverMissingResultPrefixTerminalOutcomeArgs(
  internal val workflowStates: WorkflowStateRepository,
  internal val family: WorkflowFamily,
  internal val record: WorkflowStateSnapshot,
  internal val output: Any,
  internal val issueKey: String,
  internal val subtaskId: Int,
  internal val workflowId: String,
)

class WorkflowGoalRunnerOutcomeStore @Inject constructor(
  private val database: DatabaseSessionFactory,
  dependencies: WorkflowGoalRunnerOutcomeStoreDependencies,
) : GoalRunnerWorkflowOutcomeStore,
  GoalRunnerAttemptLedgerStore,
  GoalRunnerChildRepairStore {
  private val workflowSnapshotValidator = dependencies.workflowSnapshotValidator
  private val goalObservabilityEventValidator = dependencies.goalObservabilityEventValidator
  private val goalProgressEventValidator = dependencies.goalProgressEventValidator
  private val gitOperations = dependencies.gitOperations
  private val phaseOutputValidator = dependencies.phaseOutputValidator
  private val workerSupervisor = dependencies.workerSupervisor
  private val clock = dependencies.clock
  private val decompositionManifestValidator = dependencies.decompositionManifestValidator
  private val decompositionManifestStore = dependencies.decompositionManifestStore
  private val decompositionManifestWriter = dependencies.decompositionManifestWriter
  private val childRepairExecutor = dependencies.childRepairExecutor
  private val engine = WorkflowEngine(workflowSnapshotValidator)
  private val blockWrites = WorkflowGoalRunnerBlockWrites(engine, clock)
  private val terminalPersistence = WorkflowGoalRunnerOutcomeTerminalPersistence(
    engine,
    gitOperations,
    workerSupervisor,
    clock,
  )
  private val outcomeReconcile = WorkflowGoalRunnerOutcomeReconcile(
    engine,
    gitOperations,
    goalObservabilityEventValidator,
    blockWrites,
    terminalPersistence,
    clock,
  )
  private val progressRecording = WorkflowGoalRunnerProgressRecording(
    database,
    engine,
    goalObservabilityEventValidator,
    goalProgressEventValidator,
  )
  private val terminal = WorkflowGoalRunnerTerminalBridge(database, terminalPersistence, gitOperations)
  private val review = WorkflowGoalRunnerReviewBridge(database, engine, phaseOutputValidator)
  private val reconcile = WorkflowGoalRunnerReconcileBridge(database, outcomeReconcile)
  private val blocks = WorkflowGoalRunnerBlockBridge(database, blockWrites)
  private val childRepairBridge = WorkflowGoalRunnerChildRepairBridge(
    database,
    childRepairExecutor,
    decompositionManifestValidator,
    decompositionManifestStore,
    decompositionManifestWriter,
    engine,
  )

  override fun terminalOutcome(workflowId: String, issueKey: String, subtaskId: Int): GoalRunnerStoredOutcome? =
    terminal.terminalOutcome(workflowId, issueKey, subtaskId)

  override fun recoverAndPersistTerminalOutcome(
    workflowId: String,
    issueKey: String,
    subtaskId: Int,
    repoRoot: Path,
  ): GoalRunnerStoredOutcome? = terminal.recoverAndPersistTerminalOutcome(workflowId, issueKey, subtaskId, repoRoot)

  override fun recoverMissingResultPrefixOutput(
    workflowId: String,
    issueKey: String,
    subtaskId: Int,
    output: Any,
  ): GoalRunnerStoredOutcome? = terminal.recoverMissingResultPrefixOutput(workflowId, issueKey, subtaskId, output)

  override fun goalSubtaskReviewState(workflowId: String): GoalSubtaskReviewState? =
    review.goalSubtaskReviewState(workflowId)

  override fun unemittedGoalReviewPasses(workflowId: String): List<GoalSubtaskReviewPassResult> =
    review.unemittedGoalReviewPasses(workflowId)

  override fun acknowledgeGoalReviewPass(workflowId: String, passNumber: Int): Boolean =
    review.acknowledgeGoalReviewPass(workflowId, passNumber)

  override fun progress(workflowId: String): GoalRunnerWorkflowProgress? = progressRecording.progress(workflowId)

  override fun recordObservabilityEvent(request: GoalRunnerObservabilityRecordRequest): Boolean =
    progressRecording.recordObservabilityEvent(request)

  override fun recordProgressEvent(request: GoalRunnerProgressEventRecordRequest): Boolean =
    progressRecording.recordProgressEvent(request)

  override fun progressEvents(workflowId: String): List<GoalProgressEvent> =
    progressRecording.progressEvents(workflowId)

  override fun recordAttemptLedgerEntry(request: GoalRunnerAttemptLedgerRecordRequest): Boolean =
    progressRecording.recordAttemptLedgerEntry(request)

  override fun recordWorkerSubtaskRequestOutcomes(
    workflowId: String,
    outcomes: List<GoalRunnerWorkerSubtaskRequestOutcome>,
  ): Boolean = progressRecording.recordWorkerSubtaskRequestOutcomes(workflowId, outcomes)

  override fun ledgerSequenceWatermarks(issueKey: String): GoalRunnerLedgerSequenceWatermarks =
    progressRecording.ledgerSequenceWatermarks(issueKey)

  override fun childWorkflowLoopIterations(workflowId: String): Map<String, Int> =
    progressRecording.childWorkflowLoopIterations(workflowId)

  override fun authoritativeOutcomes(issueKey: String): Map<Int, GoalRunnerStoredOutcome> =
    reconcile.authoritativeOutcomes(issueKey)

  override fun reconcileAuthoritativeOutcomes(
    issueKey: String,
    activeWorkflowIds: Set<String>,
    gate: GoalRunnerReconcileGate,
    repoRoot: Path?,
  ): Map<Int, GoalRunnerStoredOutcome> =
    reconcile.reconcileAuthoritativeOutcomes(issueKey, activeWorkflowIds, gate, repoRoot)

  override fun markBlocked(
    workflowId: String,
    blockedReason: String,
    lastResumableStep: String,
    supervisionEvent: GoalRunnerSupervisionEvent?,
  ): String? = blocks.markBlocked(workflowId, blockedReason, lastResumableStep, supervisionEvent)

  override fun reopenBlockedPhaseForOperatorResume(
    workflowId: String,
    preferredPhaseId: String,
    reason: String,
  ): Boolean = blocks.reopenBlockedPhaseForOperatorResume(workflowId, preferredPhaseId, reason)

  override fun readAttemptLedgerSummary(issueKey: String): GoalRunnerAttemptLedgerSummary =
    progressRecording.readAttemptLedgerSummary(issueKey)

  override fun diagnoseChildWedges(request: GoalRunnerChildWedgeDiagnosisRequest): GoalRunnerChildWedgeDiagnosis =
    childRepairBridge.diagnoseChildWedges(request)

  override fun applyChildWedgeRepairs(request: GoalRunnerChildWedgeRepairRequest): GoalRunnerChildRepairApplyResult =
    childRepairBridge.applyChildWedgeRepairs(request)
}

internal class WorkflowGoalRunnerTerminalBridge(
  private val database: DatabaseSessionFactory,
  private val terminalPersistence: WorkflowGoalRunnerOutcomeTerminalPersistence,
  private val gitOperations: WorkflowGitOperations,
) : GoalRunnerTerminalOutcomeStore {
  override fun terminalOutcome(workflowId: String, issueKey: String, subtaskId: Int): GoalRunnerStoredOutcome? =
    database.read { unitOfWork ->
      terminalPersistence.resolveTerminalOutcome(unitOfWork.workflowStates, workflowId, issueKey, subtaskId) { null }
    }

  override fun recoverAndPersistTerminalOutcome(
    workflowId: String,
    issueKey: String,
    subtaskId: Int,
    repoRoot: Path,
  ): GoalRunnerStoredOutcome? = database.transaction { unitOfWork ->
    terminalPersistence.displaceStaleBlockedContinuationOutcomeIfPresent(
      unitOfWork.workflowStates,
      workflowId,
      issueKey,
      subtaskId,
    )
    val resolved = terminalPersistence.resolveTerminalOutcome(
      unitOfWork.workflowStates,
      workflowId,
      issueKey,
      subtaskId,
    ) {
      gitOperations.headCommitSha(repoRoot).measuredCommitSha()
    } ?: return@transaction terminalPersistence.crashReconcileToResumable(
      unitOfWork.workflowStates,
      workflowId,
      issueKey,
      subtaskId,
    )
    val recovered = resolved.let { outcome ->
      terminalPersistence.recoverResolvedCommitPushBlock(
        workflowStates = unitOfWork.workflowStates,
        identity = GoalSubtaskIdentity(workflowId, issueKey, subtaskId),
        repoRoot = repoRoot,
        outcome = outcome,
      ) ?: outcome
    }
    recovered.also { outcome ->
      terminalPersistence.persistMeasuredCompletion(
        unitOfWork.workflowStates,
        workflowId,
        issueKey,
        subtaskId,
        outcome,
      )
    }
  }

  override fun recoverMissingResultPrefixOutput(
    workflowId: String,
    issueKey: String,
    subtaskId: Int,
    output: Any,
  ): GoalRunnerStoredOutcome? = database.transaction { unitOfWork ->
    val family = workflowFamilyFor(unitOfWork.workflowStates, workflowId) ?: return@transaction null
    val record = family.get(unitOfWork.workflowStates, workflowId) ?: return@transaction null
    terminalPersistence.recoverMissingResultPrefixTerminalOutcome(
      RecoverMissingResultPrefixTerminalOutcomeArgs(
        workflowStates = unitOfWork.workflowStates,
        family = family,
        record = record,
        output = output,
        issueKey = issueKey,
        subtaskId = subtaskId,
        workflowId = workflowId,
      ),
    )
  }
}

internal class WorkflowGoalRunnerReviewBridge(
  private val database: DatabaseSessionFactory,
  private val engine: WorkflowEngine,
  private val phaseOutputValidator: FeatureTaskRuntimePhaseOutputValidator,
) : GoalRunnerReviewOutcomeStore {
  override fun goalSubtaskReviewState(workflowId: String): GoalSubtaskReviewState? = database.read { unitOfWork ->
    val record = taskRuntimeRecordOrNull(unitOfWork.workflowStates, workflowId) ?: return@read null
    goalReviewArtifacts(decodeArtifacts(record.artifactsJson))?.state
  }

  override fun unemittedGoalReviewPasses(workflowId: String): List<GoalSubtaskReviewPassResult> =
    database.read { unitOfWork ->
      val record = taskRuntimeRecordOrNull(unitOfWork.workflowStates, workflowId) ?: return@read emptyList()
      val artifacts = decodeArtifacts(record.artifactsJson)
      if (GOAL_SUBTASK_REVIEW_STATE_ARTIFACT_KEY !in artifacts) return@read emptyList()
      val review = goalReviewArtifacts(artifacts) ?: return@read emptyList()
      validatedGoalReviewPasses(review, phaseOutputValidator, unitOfWork)
        .drop(review.state.emittedPassCount)
    }

  override fun acknowledgeGoalReviewPass(workflowId: String, passNumber: Int): Boolean =
    database.transaction { unitOfWork ->
      val record = taskRuntimeRecordOrNull(unitOfWork.workflowStates, workflowId) ?: return@transaction false
      val artifacts = decodeArtifacts(record.artifactsJson)
      val review = goalReviewArtifacts(artifacts) ?: return@transaction false
      val state = review.state
      validatedGoalReviewPasses(review, phaseOutputValidator, unitOfWork)
      if (passNumber != state.emittedPassCount + 1 || passNumber > state.completedPassCount) {
        return@transaction false
      }
      val updated = engine.updateRecord(
        WorkflowFamily.TASK_RUNTIME.definition,
        record,
        WorkflowUpdateInput(
          workflowStatus = record.workflowStatus,
          currentStepId = record.currentStepId,
          stepUpdates = null,
          artifactsPatch = WorkflowArtifactPatch.from(
            mapOf(
              GOAL_SUBTASK_REVIEW_STATE_ARTIFACT_KEY to
                state.acknowledgeSummariesThrough(passNumber).toPersistenceWire(),
            ),
          ),
          sessionId = record.sessionId.orEmpty(),
        ),
      )
      WorkflowFamily.TASK_RUNTIME.save(unitOfWork.workflowStates, updated)
      true
    }
}

internal class WorkflowGoalRunnerReconcileBridge(
  private val database: DatabaseSessionFactory,
  private val outcomeReconcile: WorkflowGoalRunnerOutcomeReconcile,
) {
  fun reconcileAuthoritativeOutcomes(
    issueKey: String,
    activeWorkflowIds: Set<String>,
    gate: GoalRunnerReconcileGate,
    repoRoot: Path?,
  ): Map<Int, GoalRunnerStoredOutcome> = database.transaction { unitOfWork ->
    outcomeReconcile.reconcileAuthoritativeOutcomesInTransaction(
      unitOfWork,
      issueKey,
      activeWorkflowIds,
      gate,
      repoRoot,
    )
  }

  fun authoritativeOutcomes(issueKey: String): Map<Int, GoalRunnerStoredOutcome> = database.read { unitOfWork ->
    outcomeReconcile.loadContinuationCandidates(unitOfWork.workflowStates, issueKey.trim(), repoRoot = null)
      .authoritativeOutcomesBySubtask()
  }
}

internal class WorkflowGoalRunnerBlockBridge(
  private val database: DatabaseSessionFactory,
  private val blockWrites: WorkflowGoalRunnerBlockWrites,
) {
  fun markBlocked(
    workflowId: String,
    blockedReason: String,
    lastResumableStep: String,
    supervisionEvent: GoalRunnerSupervisionEvent?,
  ): String? = database.transaction { unitOfWork ->
    blockWrites.markBlocked(
      workflowId,
      blockedReason,
      lastResumableStep,
      supervisionEvent,
      unitOfWork.workflowStates,
    )
  }

  fun reopenBlockedPhaseForOperatorResume(workflowId: String, preferredPhaseId: String, reason: String): Boolean =
    database.transaction { unitOfWork ->
      blockWrites.reopenBlockedPhaseForOperatorResume(unitOfWork, workflowId, preferredPhaseId, reason)
    }
}

internal class WorkflowGoalRunnerChildRepairBridge(
  private val database: DatabaseSessionFactory,
  private val childRepair: GoalRunnerChildRepairRunnerPort,
  private val decompositionManifestValidator: DecompositionManifestValidator,
  private val decompositionManifestStore: DecompositionManifestStore,
  private val decompositionManifestWriter: DecompositionManifestProjectionWriter,
  private val engine: WorkflowEngine,
) : GoalRunnerChildRepairStore {
  override fun diagnoseChildWedges(request: GoalRunnerChildWedgeDiagnosisRequest): GoalRunnerChildWedgeDiagnosis =
    database.read { unitOfWork ->
      childRepair.diagnose(
        workflowStates = unitOfWork.workflowStates,
        workflowId = request.workflowId,
        issueKey = request.issueKey,
        subtaskId = request.subtaskId,
        repoRoot = request.repoRoot,
      )
    }

  override fun applyChildWedgeRepairs(request: GoalRunnerChildWedgeRepairRequest): GoalRunnerChildRepairApplyResult {
    val result = database.transaction { unitOfWork ->
      childRepair.apply(
        GoalRunnerChildRepairApplyRequest(
          unitOfWork = unitOfWork,
          workflowId = request.workflowId,
          issueKey = request.issueKey,
          subtaskId = request.subtaskId,
          wedgeClasses = request.wedgeClasses,
          repoRoot = request.repoRoot,
          wedgeFindings = request.wedgeFindings,
        ),
      )
    }
    result.manifestProjectionArtifactsJson?.let { artifactsJson ->
      when (
        val outcome = decompositionManifestWriter.writeProjectionFromWorkflowState(
          repoRoot = request.repoRoot,
          artifactsJson = artifactsJson,
          validator = decompositionManifestValidator,
          fileStore = decompositionManifestStore,
        )
      ) {
        is DecompositionManifestProjectionOutcome.Failed ->
          database.transaction { unitOfWork ->
            persistDecompositionManifestProjectionFailure(
              engine,
              unitOfWork,
              request.workflowId,
              outcome,
            )
          }
        is DecompositionManifestProjectionOutcome.Written ->
          database.transaction { unitOfWork ->
            clearDecompositionManifestProjectionFailure(engine, unitOfWork, request.workflowId)
          }
        DecompositionManifestProjectionOutcome.Absent -> Unit
      }
    }
    return result
  }
}
