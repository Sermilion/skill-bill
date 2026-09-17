package skillbill.engine.featuretask

import skillbill.engine.featuretask.model.AppendCheckpointIdentityArgs
import skillbill.engine.featuretask.model.FeatureTaskRuntimePhaseLaunchBriefing
import skillbill.engine.featuretask.model.FeatureTaskRuntimePhaseLedgerRequest
import skillbill.engine.featuretask.model.FeatureTaskRuntimeProjectionRejection
import skillbill.error.InvalidFeatureTaskRuntimeHandoffProjectionError
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeCheckpointIdentity
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeDeliveredProjectionRecord
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeFindingVerificationDisposition
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeQualityGateSelection
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeQuarantineEntry
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeResolvedBranch
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeSharedEvidenceMeasurement
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeValidationGateProgress
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeVerificationBoundaryHeadingProvenance
import skillbill.workflow.taskruntime.model.PhaseHandoffProjectionDeclaration

interface FeatureTaskRuntimePhaseFindingVerificationApi {
  fun loadFindingVerificationCheckpoint(workflowId: String): List<FeatureTaskRuntimeFindingVerificationDisposition>?
  fun loadFindingVerificationBoundarySelection(
    workflowId: String,
  ): Map<String, List<FeatureTaskRuntimeVerificationBoundaryHeadingProvenance>>?
  fun persistFindingVerificationBoundarySelection(
    workflowId: String,
    selections: Map<String, List<FeatureTaskRuntimeVerificationBoundaryHeadingProvenance>>,
  ): Boolean
  fun loadFindingVerificationDispositions(workflowId: String): List<FeatureTaskRuntimeFindingVerificationDisposition>?
  fun persistFindingVerificationCheckpoint(
    workflowId: String,
    dispositions: List<FeatureTaskRuntimeFindingVerificationDisposition>,
  ): Boolean
  fun clearFindingVerificationCheckpoint(workflowId: String): Boolean
}

interface FeatureTaskRuntimePhaseReviewCheckpointApi :
  FeatureTaskRuntimePhaseReviewGenerationApi,
  FeatureTaskRuntimePhaseFindingVerificationApi

interface FeatureTaskRuntimePhaseBriefingApi {
  fun recordPhaseBriefing(
    workflowId: String,
    briefing: FeatureTaskRuntimePhaseLaunchBriefing,
    sharedEvidenceMeasurement: FeatureTaskRuntimeSharedEvidenceMeasurement? = null,
  ): Boolean
  fun recordProjectionRejection(
    workflowId: String,
    consumerPhaseId: String,
    error: InvalidFeatureTaskRuntimeHandoffProjectionError,
    repositoryCheckpointFingerprint: String?,
  ): Boolean
  fun recordProjectionRejection(rejection: FeatureTaskRuntimeProjectionRejection): Boolean
  fun validateHandoffDeclarations(declarations: List<PhaseHandoffProjectionDeclaration>)
  fun loadPhaseBriefings(workflowId: String): Map<String, FeatureTaskRuntimePhaseLaunchBriefing>?
  fun loadDeliveredProjections(workflowId: String): Map<String, FeatureTaskRuntimeDeliveredProjectionRecord>?
}

interface FeatureTaskRuntimePhaseGateApi {
  fun loadValidationGateProgress(workflowId: String): FeatureTaskRuntimeValidationGateProgress?
  fun persistValidationGateProgress(workflowId: String, progress: FeatureTaskRuntimeValidationGateProgress)
  fun loadBuildGateProgress(workflowId: String): FeatureTaskRuntimeValidationGateProgress?
  fun loadGoalContinuationQualityGateSelection(workflowId: String): FeatureTaskRuntimeQualityGateSelection?
  fun persistBuildGateProgress(workflowId: String, progress: FeatureTaskRuntimeValidationGateProgress)
}

interface FeatureTaskRuntimePhaseEvidenceApi {
  fun appendLedgerEntry(request: FeatureTaskRuntimePhaseLedgerRequest): Boolean
  fun appendQuarantineEntry(workflowId: String, entry: FeatureTaskRuntimeQuarantineEntry): Boolean
  fun loadQuarantinedRecords(workflowId: String): List<FeatureTaskRuntimeQuarantineEntry>?
  fun recordResolvedBranch(workflowId: String, resolvedBranch: FeatureTaskRuntimeResolvedBranch): Boolean
  fun loadResolvedBranch(workflowId: String): FeatureTaskRuntimeResolvedBranch?
  fun appendCheckpointIdentity(args: AppendCheckpointIdentityArgs): Boolean
  fun loadCheckpointIdentities(workflowId: String): List<FeatureTaskRuntimeCheckpointIdentity>?
  fun quarantineCheckpointIdentities(workflowId: String): Boolean
  fun recordWorkflowOwnedPaths(workflowId: String, ownedPaths: List<String>): Boolean
}
