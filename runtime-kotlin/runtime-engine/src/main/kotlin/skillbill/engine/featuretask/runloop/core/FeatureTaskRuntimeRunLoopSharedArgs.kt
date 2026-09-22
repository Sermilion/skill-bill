package skillbill.engine.featuretask.runloop.core

import skillbill.application.review.model.ParallelCodeReviewResult
import skillbill.engine.featuretask.lifecycle.continuation.FeatureTaskRuntimeGoalContinuationRecorder
import skillbill.engine.featuretask.model.core.FeatureTaskRuntimeRunRequest
import skillbill.engine.featuretask.model.phase.ValidationFindingSetProjection
import skillbill.engine.featuretask.model.subtask.FeatureTaskRuntimeSubtaskCommitIdentity
import skillbill.engine.featuretask.phase.core.FeatureTaskRuntimePhaseFileManifest
import skillbill.engine.featuretask.phase.core.FeatureTaskRuntimePhaseGates
import skillbill.engine.featuretask.phase.prompt.directives.PriorAttemptCorrection
import skillbill.engine.featuretask.phase.record.FeatureTaskRuntimePhaseRecorder
import skillbill.engine.featuretask.runloop.observability.FeatureTaskRuntimeRunObservability
import skillbill.engine.featuretask.runloop.phase.FeatureTaskRuntimeRunLoopPhaseAttempts
import skillbill.engine.featuretask.runloop.state.FeatureTaskRuntimeRunState
import skillbill.ports.diagnostics.RuntimeDiagnostics
import skillbill.ports.diagnostics.model.ProducerOutputEvidence
import skillbill.ports.workflow.gitops.model.GoalSubtaskReviewInput
import skillbill.review.context.model.launch.CodeReviewExecutionMode
import skillbill.workflow.decomposition.model.SpecSource
import skillbill.workflow.taskruntime.model.core.FeatureTaskRuntimeRepositoryCheckpoint
import skillbill.workflow.taskruntime.model.handoff.task.FeatureTaskRuntimeProducerIteration
import skillbill.workflow.taskruntime.model.handoff.task.FeatureTaskRuntimeProjectionFailureClassification
import skillbill.workflow.taskruntime.model.handoff.task.NormalizedFeatureTaskRuntimePhaseOutput
import skillbill.workflow.taskruntime.model.phase.AcceptedFeatureTaskRuntimePhaseOutput
import skillbill.workflow.taskruntime.model.phase.FeatureTaskRuntimeFailureDisposition
import skillbill.workflow.taskruntime.model.phase.FeatureTaskRuntimeNextPhase
import skillbill.workflow.taskruntime.model.phase.FeatureTaskRuntimePhaseLedgerEntry
import skillbill.workflow.taskruntime.model.phase.FeatureTaskRuntimePhaseOutputRepairEvidence
import skillbill.workflow.taskruntime.model.phase.FeatureTaskRuntimePhaseRecord
import skillbill.workflow.taskruntime.model.phase.FeatureTaskRuntimeTransitionDeclaration
import skillbill.workflow.taskruntime.model.review.FeatureTaskRuntimeReviewFinding
import skillbill.workflow.taskruntime.model.validation.FeatureTaskRuntimeVerdict
import skillbill.workflow.taskruntime.phase.task.FeatureTaskRuntimePhaseOutputValidator
import java.time.Clock

internal data class PhaseAttemptContext(
  val run: PhaseRun,
  val state: FeatureTaskRuntimeRunState,
  val iteration: Int,
  val observability: FeatureTaskRuntimeRunObservability,
  val outputGateFailuresBefore: Int? = null,
)

internal data class PhaseAttemptAccumulatorContext(
  val attempt: PhaseAttemptContext,
)

internal data class TerminalOutputAttemptArgs(
  val run: PhaseRun,
  val iteration: Int,
  val reason: String,
  val normalizedOutput: NormalizedFeatureTaskRuntimePhaseOutput,
  val repairEvidence: FeatureTaskRuntimePhaseOutputRepairEvidence?,
  val observability: FeatureTaskRuntimeRunObservability,
  val fileManifest: FeatureTaskRuntimePhaseFileManifest,
)

internal data class UnattributableRecordRejectionArgs(
  val context: PhaseAttemptContext,
  val rejection: RecordRejection,
  val producer: String?,
)

internal data class WriteUnattributableRejectedEvidenceArgs(
  val state: FeatureTaskRuntimeRunState,
  val recorder: FeatureTaskRuntimePhaseRecorder,
  val run: PhaseRun,
  val rejection: RecordRejection,
  val detail: String,
  val evidence: ProducerOutputEvidence,
)

internal data class RecordRejectionAttemptArgs(
  val context: PhaseAttemptContext,
  val priorCorrection: PriorAttemptCorrection?,
)

internal data class ProducerEvidenceRecordRejectionArgs(
  val context: PhaseAttemptContext,
  val producer: String,
  val consumer: String,
)

internal data class QuarantineRecordRejectionArgs(
  val context: PhaseAttemptContext,
  val rejection: RecordRejection,
  val regeneration: FeatureTaskRuntimeRunLoopPhaseAttempts.RecordRejectionRegenerationEdge,
  val producerEvidence: ProducerOutputEvidence,
)

internal data class ValidationGateCycleRequestArgs(
  val context: PhaseAttemptAccumulatorContext,
  val checkpoint: String,
)

internal data class FixLoopOutcomeArgs(
  val context: PhaseAttemptAccumulatorContext,
  val loop: PhaseAttemptLoopState,
  val agentId: String,
)

internal data class LaunchPreparationRejectedArgs(
  val run: PhaseRun,
  val state: FeatureTaskRuntimeRunState,
  val classification: FeatureTaskRuntimeProjectionFailureClassification,
  val sourceLabel: String,
  val measurement: LaunchRejectionMeasurementContext,
  val message: String,
)

internal data class LaunchSeamRejectionArgs(
  val run: PhaseRun,
  val state: FeatureTaskRuntimeRunState,
  val classification: FeatureTaskRuntimeProjectionFailureClassification,
  val sourceLabel: String,
  val fallbackProducerIteration: FeatureTaskRuntimeProducerIteration,
  val repositoryCheckpoint: FeatureTaskRuntimeRepositoryCheckpoint?,
)

internal data class CompletionProjectionRejectionArgs(
  val run: PhaseRun,
  val iteration: Int,
  val normalizedOutput: NormalizedFeatureTaskRuntimePhaseOutput,
  val repairEvidence: FeatureTaskRuntimePhaseOutputRepairEvidence?,
  val repositoryFingerprint: String?,
)

internal data class RepositoryCheckpointResolutionArgs(
  val recorder: FeatureTaskRuntimePhaseRecorder,
  val goalContinuationRecorder: FeatureTaskRuntimeGoalContinuationRecorder,
  val phaseGates: FeatureTaskRuntimePhaseGates,
  val session: FeatureTaskRuntimeRunLoopSession,
  val run: PhaseRun,
)

internal data class PersistAcceptedOutputArgs(
  val run: PhaseRun,
  val iteration: Int,
  val normalizedOutput: NormalizedFeatureTaskRuntimePhaseOutput,
  val repairEvidence: FeatureTaskRuntimePhaseOutputRepairEvidence?,
  val observability: FeatureTaskRuntimeRunObservability,
  val fileManifest: FeatureTaskRuntimePhaseFileManifest,
  val repositoryFingerprint: String?,
)

internal data class PersistStandardAcceptedOutputArgs(
  val accepted: PersistAcceptedOutputArgs,
  val outputText: String,
)

internal data class BlockAndPersistArgs(
  val run: PhaseRun,
  val attemptCount: Int,
  val reason: String,
  val observability: FeatureTaskRuntimeRunObservability,
  val loopId: String?,
  val edgeIteration: Int?,
  val failureDisposition: FeatureTaskRuntimeFailureDisposition,
  val payload: BlockAndPersistPayload,
)

internal data class BlockAndPersistPayload(
  val fileManifest: FeatureTaskRuntimePhaseFileManifest? = null,
  val outputArtifact: String? = null,
  val normalizedOutput: NormalizedFeatureTaskRuntimePhaseOutput? = null,
  val repairEvidence: FeatureTaskRuntimePhaseOutputRepairEvidence? = null,
  val rejectedOutput: String? = null,
  val childNeverLaunched: Boolean = false,
)

internal data class BlockAndPersistInPhaseArgs(
  val run: PhaseRun,
  val attemptCount: Int,
  val reason: String,
  val observability: FeatureTaskRuntimeRunObservability,
  val failureDisposition: FeatureTaskRuntimeFailureDisposition,
  val payload: BlockAndPersistPayload,
)

internal data class BlockOnCapExhaustionArgs(
  val request: FeatureTaskRuntimeRunRequest,
  val state: FeatureTaskRuntimeRunState,
  val recorder: FeatureTaskRuntimePhaseRecorder,
  val observability: FeatureTaskRuntimeRunObservability,
  val session: FeatureTaskRuntimeRunLoopSession,
  val goalContinuationRecorder: FeatureTaskRuntimeGoalContinuationRecorder,
  val specSource: SpecSource,
  val phaseId: String,
  val transition: FeatureTaskRuntimeNextPhase.TerminalBlock,
)

internal data class PauseAtArgs(
  val request: FeatureTaskRuntimeRunRequest,
  val state: FeatureTaskRuntimeRunState,
  val session: FeatureTaskRuntimeRunLoopSession,
  val phaseId: String,
  val reason: String,
  val resumableStep: String,
)

internal data class CapExhaustionReasonArgs(
  val request: FeatureTaskRuntimeRunRequest,
  val recorder: FeatureTaskRuntimePhaseRecorder,
  val loopId: String,
  val edgeIteration: Int,
  val verdict: FeatureTaskRuntimeVerdict,
  val unresolvedFindings: List<FeatureTaskRuntimeReviewFinding>,
)

internal data class UnownedWorktreeCommitShaArgs(
  val request: FeatureTaskRuntimeRunRequest,
  val outputValidator: FeatureTaskRuntimePhaseOutputValidator,
  val diagnostics: RuntimeDiagnostics,
  val phaseGates: FeatureTaskRuntimePhaseGates,
  val run: PhaseRun,
  val normalizedOutput: NormalizedFeatureTaskRuntimePhaseOutput,
)

internal data class PersistRejectedVerificationFindingsArgs(
  val state: FeatureTaskRuntimeRunState,
  val recorder: FeatureTaskRuntimePhaseRecorder,
  val goalContinuationRecorder: FeatureTaskRuntimeGoalContinuationRecorder,
  val diagnostics: RuntimeDiagnostics,
  val run: PhaseRun,
  val verifyOutput: Map<String, Any?>,
)

internal data class MissingProducerAgentResolutionArgs(
  val request: FeatureTaskRuntimeRunRequest,
  val state: FeatureTaskRuntimeRunState,
  val recorder: FeatureTaskRuntimePhaseRecorder,
  val run: PhaseRun,
  val iteration: Int,
  val consumer: String,
  val producer: String,
  val observability: FeatureTaskRuntimeRunObservability,
)

internal data class RetainRuntimeOwnedReviewEvidenceArgs(
  val request: FeatureTaskRuntimeRunRequest,
  val recorder: FeatureTaskRuntimePhaseRecorder,
  val clock: Clock,
  val run: PhaseRun,
  val state: FeatureTaskRuntimeRunState,
  val iteration: Int,
  val outputText: String,
)

internal data class PersistReviewCompletionOutcomeArgs(
  val request: FeatureTaskRuntimeRunRequest,
  val state: FeatureTaskRuntimeRunState,
  val recorder: FeatureTaskRuntimePhaseRecorder,
  val observability: FeatureTaskRuntimeRunObservability,
  val goalContinuationRecorder: FeatureTaskRuntimeGoalContinuationRecorder,
  val outcome: PhaseReviewCompletionOutcomeArgs,
)

internal data class RunPhaseArgs(
  val phaseId: String,
  val request: FeatureTaskRuntimeRunRequest,
  val state: FeatureTaskRuntimeRunState,
  val observability: FeatureTaskRuntimeRunObservability,
  val specSource: SpecSource,
  val reentry: PendingReentry?,
)

internal data class ImplementFixRepairReceiptArgs(
  val request: FeatureTaskRuntimeRunRequest,
  val state: FeatureTaskRuntimeRunState,
  val recorder: FeatureTaskRuntimePhaseRecorder,
  val goalContinuationRecorder: FeatureTaskRuntimeGoalContinuationRecorder,
  val diagnostics: RuntimeDiagnostics,
  val run: PhaseRun,
  val normalizedOutput: NormalizedFeatureTaskRuntimePhaseOutput,
  val reject: (String, String) -> AttemptResult,
  val iteration: Int,
  val observability: FeatureTaskRuntimeRunObservability,
  val fileManifest: FeatureTaskRuntimePhaseFileManifest,
)

internal data class CompletedImplementationOutputArgs(
  val run: PhaseRun,
  val normalizedOutput: NormalizedFeatureTaskRuntimePhaseOutput,
  val reject: (String, String) -> AttemptResult,
  val iteration: Int,
  val observability: FeatureTaskRuntimeRunObservability,
  val fileManifest: FeatureTaskRuntimePhaseFileManifest,
)

internal data class CommitCheckpointArgs(
  val precedingPhaseId: String,
  val branch: String,
  val loopId: String?,
  val intent: String,
  val ownedPaths: List<String>,
  val blockedReason: (String, String) -> String,
)

internal data class RecordCheckpointIdentityArgs(
  val precedingPhaseId: String,
  val branch: String,
  val loopId: String?,
  val ownedPaths: List<String>,
  val parentSha: String?,
  val commitSha: String,
  val blockedReason: (String, String) -> String,
)

internal data class ValidationGateTriageArgs(
  val context: PhaseAttemptAccumulatorContext,
  val findings: ValidationFindingSetProjection,
)

internal data class ValidationGateRepairArgs(
  val context: PhaseAttemptAccumulatorContext,
  val findings: ValidationFindingSetProjection,
  val repairTurn: Int,
  val triagePlan: String?,
)

internal data class SettleValidatedOutputPauseArgs(
  val capture: ValidatedOutputCapture,
  val attested: NormalizedFeatureTaskRuntimePhaseOutput,
  val repairEvidence: FeatureTaskRuntimePhaseOutputRepairEvidence?,
  val observability: FeatureTaskRuntimeRunObservability,
  val repositoryFingerprint: String?,
)

internal data class ReconstructFixLoopBudgetBasesArgs(
  val transitions: FeatureTaskRuntimeTransitionDeclaration,
  val edgeIterationByLoop: Map<String, Int>,
  val initialRecords: Map<String, FeatureTaskRuntimePhaseRecord>,
  val initialLedger: List<FeatureTaskRuntimePhaseLedgerEntry>,
  val completed: Set<String>,
  val gateInvalidatedPhases: Set<String>,
  val nextIteration: (String) -> Int,
)

internal data class RejectedOutputTargetingArgs(
  val run: PhaseRun,
  val phaseId: String,
  val agentId: String,
  val model: String,
  val path: String,
  val repairTurn: Int,
)

internal data class SettleValidatedOutputAfterFingerprintArgs(
  val capture: ValidatedOutputCapture,
  val attested: NormalizedFeatureTaskRuntimePhaseOutput,
  val repairEvidence: FeatureTaskRuntimePhaseOutputRepairEvidence?,
  val observability: FeatureTaskRuntimeRunObservability,
  val repositoryFingerprint: String?,
  val reject: (String, String) -> AttemptResult,
)

internal data class RejectedOutputTargetingOverrides(
  val phaseId: String? = null,
  val agentId: String? = null,
  val model: String? = null,
  val path: String? = null,
  val repairTurn: Int? = null,
)

internal fun defaultRejectedOutputTargetingArgs(
  run: PhaseRun,
  overrides: RejectedOutputTargetingOverrides = RejectedOutputTargetingOverrides(),
): RejectedOutputTargetingArgs {
  val phaseId = overrides.phaseId ?: run.phaseId
  return RejectedOutputTargetingArgs(
    run = run,
    phaseId = phaseId,
    agentId = overrides.agentId ?: run.resolvedAgent.resolvedAgentId,
    model = overrides.model ?: run.modelDirective?.model ?: "unspecified",
    path = overrides.path ?: "/",
    repairTurn = overrides.repairTurn ?: if (phaseId == run.phaseId) run.validationGateRepairTurn else 0,
  )
}

internal data class PhaseBlockRequest(
  val run: PhaseRun,
  val attemptCount: Int,
  val reason: String,
  val observability: FeatureTaskRuntimeRunObservability,
  val payload: BlockAndPersistPayload = BlockAndPersistPayload(),
  val failureDisposition: FeatureTaskRuntimeFailureDisposition =
    FeatureTaskRuntimeFailureDisposition.NEEDS_USER_ACTION,
)

internal data class CheckpointCommitMessageArgs(
  val branch: String,
  val phaseId: String,
  val loopId: String?,
  val identity: FeatureTaskRuntimeSubtaskCommitIdentity,
  val intent: String,
)

internal data class DeclaredLaunchArgs(
  val run: PhaseRun,
  val state: FeatureTaskRuntimeRunState,
  val priorCorrection: PriorAttemptCorrection?,
  val context: LaunchRejectionMeasurementContext,
)

internal data class PauseAndPersistInPhaseArgs(
  val run: PhaseRun,
  val attemptCount: Int,
  val reason: String,
  val observability: FeatureTaskRuntimeRunObservability,
  val fileManifest: FeatureTaskRuntimePhaseFileManifest?,
)

internal data class SettleRecordRejectionArgs(
  val run: PhaseRun,
  val state: FeatureTaskRuntimeRunState,
  val iteration: Int,
  val observability: FeatureTaskRuntimeRunObservability,
  val rejection: RecordRejection,
)

internal data class MissingProducerAgentBlockArgs(
  val run: PhaseRun,
  val iteration: Int,
  val consumer: String,
  val producer: String,
  val observability: FeatureTaskRuntimeRunObservability,
)

internal data class WriteQuarantineRejectedOutputArgs(
  val run: PhaseRun,
  val producingIteration: Int,
  val rejection: RecordRejection,
  val producer: String,
  val producerEvidence: ProducerOutputEvidence,
)

internal data class RuntimeOwnedReviewDriverRequestArgs(
  val run: PhaseRun,
  val input: GoalSubtaskReviewInput,
  val passNumber: Int,
  val pinnedMode: CodeReviewExecutionMode,
  val reviewRunId: String,
)

internal data class ReviewBlockerDispositionsArgs(
  val run: PhaseRun,
  val passNumber: Int,
  val result: ParallelCodeReviewResult,
  val reviewRunId: String,
  val resolvedTier: CodeReviewExecutionMode,
)

internal data class SettleRuntimeOwnedReviewArgs(
  val run: PhaseRun,
  val iteration: Int,
  val outputText: String,
  val observability: FeatureTaskRuntimeRunObservability,
  val fileManifest: FeatureTaskRuntimePhaseFileManifest,
)

internal data class CompleteRuntimeOwnedReviewPhaseArgs(
  val run: PhaseRun,
  val iteration: Int,
  val observability: FeatureTaskRuntimeRunObservability,
  val normalizedOutput: NormalizedFeatureTaskRuntimePhaseOutput,
  val acceptedOutput: AcceptedFeatureTaskRuntimePhaseOutput,
)

internal data class FinalizeValidatedOutputAcceptanceArgs(
  val capture: ValidatedOutputCapture,
  val attested: NormalizedFeatureTaskRuntimePhaseOutput,
  val repairEvidence: FeatureTaskRuntimePhaseOutputRepairEvidence?,
  val observability: FeatureTaskRuntimeRunObservability,
  val repositoryFingerprint: String?,
)

internal data class ShouldRetryPersistedBlockArgs(
  val phaseId: String,
  val durable: FeatureTaskRuntimePhaseRecord?,
  val retryReviewPreparation: Boolean,
  val reenterableRecordRejection: Boolean,
  val persistedReason: String,
)

internal data class RecordFinalisedCheckpointIdentityArgs(
  val phaseId: String,
  val branch: String,
  val ledger: SubtaskCommitLedgerState,
  val commitSha: String,
  val stagedPaths: List<String>,
)

internal fun BlockAndPersistInPhaseArgs.withDisposition(
  failureDisposition: FeatureTaskRuntimeFailureDisposition,
): BlockAndPersistInPhaseArgs = copy(failureDisposition = failureDisposition)

internal fun phaseBlockArgs(
  run: PhaseRun,
  attemptCount: Int,
  reason: String,
  observability: FeatureTaskRuntimeRunObservability,
  payload: BlockAndPersistPayload = BlockAndPersistPayload(),
): BlockAndPersistInPhaseArgs =
  BlockAndPersistInPhaseArgs(
    run = run,
    attemptCount = attemptCount,
    reason = reason,
    observability = observability,
    failureDisposition = FeatureTaskRuntimeFailureDisposition.NEEDS_USER_ACTION,
    payload = payload,
  )

internal fun recordRejectionAttemptArgs(
  context: PhaseAttemptContext,
  priorCorrection: PriorAttemptCorrection? = null,
): RecordRejectionAttemptArgs =
  RecordRejectionAttemptArgs(
    context = context,
    priorCorrection = priorCorrection,
  )

internal fun phaseAttemptAccumulatorContext(
  run: PhaseRun,
  state: FeatureTaskRuntimeRunState,
  iteration: Int,
  observability: FeatureTaskRuntimeRunObservability,
): PhaseAttemptAccumulatorContext =
  PhaseAttemptAccumulatorContext(
    attempt = PhaseAttemptContext(run, state, iteration, observability),
  )
