package skillbill.engine.featuretask

import skillbill.application.diagnostics.model.FeatureTaskRuntimeRejectedOutputWrite
import skillbill.application.diagnostics.model.RejectedOutputDiagnosticRequest
import skillbill.engine.featuretask.model.AppendCheckpointIdentityArgs
import skillbill.engine.featuretask.model.FeatureTaskRuntimePhaseLaunchBriefing
import skillbill.engine.featuretask.model.FeatureTaskRuntimePhaseLedgerRequest
import skillbill.engine.featuretask.model.FeatureTaskRuntimePhaseStateRequest
import skillbill.engine.featuretask.model.FeatureTaskRuntimeProducerOutputRead
import skillbill.engine.featuretask.model.FeatureTaskRuntimeProjectionRejection
import skillbill.engine.featuretask.model.GoalReviewPhaseCompletionRequest
import skillbill.engine.featuretask.model.ProducerOutputQueryArgs
import skillbill.error.InvalidFeatureTaskRuntimeHandoffProjectionError
import skillbill.goalrunner.model.UnaddressedFinding
import skillbill.ports.diagnostics.model.ProducerOutputEvidence
import skillbill.ports.featuretask.model.FeatureTaskRuntimeWorkerOwnership
import skillbill.ports.workflow.model.FeatureTaskWorkflowMode
import skillbill.review.model.ReviewFindingVerdict
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeCheckpointIdentity
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeDeliveredProjectionRecord
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeDiagnosticSignal
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeFindingVerificationDisposition
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeImplementationAttempt
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeOperatorBlockRetry
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimePhaseLedgerEntry
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimePhaseRecord
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeQualityGateSelection
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeQuarantineEntry
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeResolvedBranch
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeSharedEvidenceMeasurement
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeValidationGateProgress
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeVerificationBoundaryHeadingProvenance
import skillbill.workflow.taskruntime.model.PhaseHandoffProjectionDeclaration

interface FeatureTaskRuntimePhaseWorkflowApi {
  fun existingWorkflowMode(workflowId: String): FeatureTaskWorkflowMode?
  fun workerOwnership(workflowId: String): FeatureTaskRuntimeWorkerOwnership?
  fun ensureWorkflowOpen(workflowId: String, sessionId: String, issueKey: String? = null): Boolean
}

interface FeatureTaskRuntimePhaseRejectedApi {
  fun recordRejectedOutput(
    request: RejectedOutputDiagnosticRequest,
    producerGeneration: Int = 0,
  ): FeatureTaskRuntimeRejectedOutputWrite
  fun retainProducerOutput(evidence: ProducerOutputEvidence)
  fun producerOutput(args: ProducerOutputQueryArgs): FeatureTaskRuntimeProducerOutputRead
  fun loadDiagnosticSignals(workflowId: String): List<FeatureTaskRuntimeDiagnosticSignal>
}

interface FeatureTaskRuntimePhaseStateApi {
  fun recordPhaseState(request: FeatureTaskRuntimePhaseStateRequest): Boolean
  fun recordCompletedPhase(request: FeatureTaskRuntimePhaseStateRequest): Boolean
  fun recordIncompleteImplementationAttempt(request: FeatureTaskRuntimePhaseStateRequest): Boolean
  fun loadImplementationAttempts(workflowId: String): List<FeatureTaskRuntimeImplementationAttempt>?
  fun clearBackwardEdgeContext(workflowId: String, phaseIds: Collection<String>): Boolean
  fun loadPhaseRecords(workflowId: String): Map<String, FeatureTaskRuntimePhaseRecord>?
  fun loadOperatorBlockRetry(workflowId: String): FeatureTaskRuntimeOperatorBlockRetry?
  fun loadPhaseLedger(workflowId: String): List<FeatureTaskRuntimePhaseLedgerEntry>?
}

interface FeatureTaskRuntimePhaseReviewApi {
  fun completeGoalReviewPhase(completion: GoalReviewPhaseCompletionRequest): Boolean
}

interface FeatureTaskRuntimePhaseReviewGenerationApi {
  fun persistReviewGenerationInvalidation(workflowId: String): Int?
  fun reconcileReviewGeneration(workflowId: String): Int
  fun invalidateQuarantinedProducerRecord(
    workflowId: String,
    producerPhaseId: String,
    loopId: String,
    edgeIteration: Int,
  ): Boolean
  fun recordedFindingVerdicts(output: Map<String, Any?>): List<ReviewFindingVerdict>
  fun fetchUnaddressedLedger(workflowId: String): List<UnaddressedFinding>
  fun appendRejectedVerificationFindings(workflowId: String, passNumber: Int, rejected: List<UnaddressedFinding>)
}

