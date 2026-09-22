package skillbill.workflow.engine.model

import skillbill.contracts.workflow.session.WorkflowContinueSessionSummary
import skillbill.workflow.model.WorkflowContinueStatus
import skillbill.workflow.model.WorkflowResumeMode
import skillbill.workflow.model.WorkflowStatus

data class WorkflowSnapshotView(
  val workflowId: String,
  val sessionId: String,
  val workflowName: String,
  val contractVersion: String,
  val workflowStatus: WorkflowStatus,
  val currentStepId: String,
  val steps: List<WorkflowStepState>,
  val artifacts: DurableWorkflowArtifacts,
  val startedAt: String,
  val updatedAt: String,
  val finishedAt: String,
  val mode: String? = null,
)

data class WorkflowSummaryView(
  val workflowId: String,
  val sessionId: String,
  val workflowName: String,
  val contractVersion: String,
  val workflowStatus: WorkflowStatus,
  val currentStepId: String,
  val startedAt: String,
  val updatedAt: String,
  val finishedAt: String,
  val mode: String? = null,
)

data class WorkflowUpdateAcknowledgementView(
  val status: String,
  val workflowId: String,
  val workflowName: String,
  val workflowStatus: WorkflowStatus,
  val currentStepId: String,
  val updatedStepIds: List<String>,
  val updatedArtifactKeys: List<String>,
  val readOnlyFullStateGuidance: String,
)

data class WorkflowResumeView(
  val snapshot: WorkflowSnapshotView,
  val resumeMode: WorkflowResumeMode,
  val resumeStepId: String,
  val lastCompletedStepId: String,
  val availableArtifacts: List<String>,
  val requiredArtifacts: List<String>,
  val missingArtifacts: List<String>,
  val canResume: Boolean,
  val nextAction: String,
)

data class WorkflowContinuationArtifactSummary(
  val key: String,
  val present: Boolean,
  val inline: Boolean,
  val sizeBytes: Int?,
  val value: InlineContinuationArtifactValue,
  val preview: String?,
  val truncated: Boolean,
  val omitted: Boolean,
  val omissionReason: String?,
)

data class WorkflowCompactContinueView(
  val workflowId: String,
  val skillName: String,
  val continueStatus: WorkflowContinueStatus,
  val workflowStatusBeforeContinue: WorkflowStatus,
  val startedAt: String,
  val updatedAt: String,
  val resumeStepId: String,
  val resumeStepLabel: String,
  val continueStepDirective: String,
  val referenceSections: List<String>,
  val requiredArtifactKeys: List<String>,
  val availableArtifactKeys: List<String>,
  val missingArtifactKeys: List<String>,
  val currentStepArtifacts: List<WorkflowContinuationArtifactSummary>,
  val omittedArtifactKeys: List<String>,
  val continuationBrief: String,
  val continuationEntryPrompt: String,
  val readOnlyFullStateGuidance: String,
)

data class WorkflowContinueView(
  val resume: WorkflowResumeView,
  val skillName: String,
  val workflowStatusBeforeContinue: WorkflowStatus,
  val continueStatus: WorkflowContinueStatus,
  val continueStepId: String,
  val continueStepLabel: String,
  val continueStepDirective: String,
  val referenceSections: List<String>,
  val stepArtifactKeys: List<String>,
  val stepArtifacts: WorkflowStepArtifactMap,
  val extraFields: WorkflowContinuationFieldMap,
  val sessionSummary: WorkflowContinueSessionSummary,
  val continuationBrief: String,
  val continuationEntryPrompt: String,
  val compact: WorkflowCompactContinueView,
)
