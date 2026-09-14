package skillbill.application.workflow

import skillbill.contracts.JsonPayloadContract
import skillbill.contracts.SharedPayloadKeys
import skillbill.contracts.review.ReviewVerificationSignalKeys
import skillbill.contracts.workflow.WorkflowContracts
import skillbill.contracts.workflow.WorkflowWirePayloadKeys
import skillbill.workflow.engine.model.WorkflowCompactContinueView
import skillbill.workflow.engine.model.WorkflowContinuationArtifactSummary
import skillbill.workflow.engine.model.WorkflowContinueView
import skillbill.workflow.engine.model.WorkflowInputProjection
import skillbill.workflow.engine.model.WorkflowResumeView
import skillbill.workflow.engine.model.WorkflowSnapshotView
import skillbill.workflow.engine.model.WorkflowStepState
import skillbill.workflow.engine.model.WorkflowSummaryView
import skillbill.workflow.engine.model.WorkflowUpdateAcknowledgementView

object WorkflowWireProjections {
  fun snapshotMap(view: WorkflowSnapshotView): JsonPayloadContract = payload(
    WorkflowContracts.fullWorkflowPayload(
      linkedMapOf(
        SharedPayloadKeys.WORKFLOW_ID to view.workflowId,
        WorkflowWirePayloadKeys.SESSION_ID to view.sessionId,
        WorkflowWirePayloadKeys.WORKFLOW_NAME to view.workflowName,
        WorkflowWirePayloadKeys.MODE to view.mode,
        SharedPayloadKeys.CONTRACT_VERSION to view.contractVersion,
        WorkflowWirePayloadKeys.WORKFLOW_STATUS to view.workflowStatus,
        WorkflowWirePayloadKeys.CURRENT_STEP_ID to view.currentStepId,
        WorkflowWirePayloadKeys.STEPS to view.steps.map(::workflowStepWireMap),
        WorkflowWirePayloadKeys.ARTIFACTS to view.artifacts.toMap(),
        WorkflowWirePayloadKeys.STARTED_AT to view.startedAt,
        WorkflowWirePayloadKeys.UPDATED_AT to view.updatedAt,
        WorkflowWirePayloadKeys.FINISHED_AT to view.finishedAt,
      ),
    ),
  )

  fun summaryMap(view: WorkflowSummaryView): JsonPayloadContract = payload(
    WorkflowContracts.summaryWorkflowPayload(
      linkedMapOf(
        SharedPayloadKeys.WORKFLOW_ID to view.workflowId,
        WorkflowWirePayloadKeys.SESSION_ID to view.sessionId,
        WorkflowWirePayloadKeys.WORKFLOW_NAME to view.workflowName,
        WorkflowWirePayloadKeys.MODE to view.mode,
        SharedPayloadKeys.CONTRACT_VERSION to view.contractVersion,
        WorkflowWirePayloadKeys.WORKFLOW_STATUS to view.workflowStatus,
        WorkflowWirePayloadKeys.CURRENT_STEP_ID to view.currentStepId,
        WorkflowWirePayloadKeys.STARTED_AT to view.startedAt,
        WorkflowWirePayloadKeys.UPDATED_AT to view.updatedAt,
        WorkflowWirePayloadKeys.FINISHED_AT to view.finishedAt,
      ),
    ),
  )

  fun resumeMap(view: WorkflowResumeView): JsonPayloadContract = payload(
    WorkflowContracts.resumePayload(
      snapshotMap(view.snapshot).toPayload(),
      linkedMapOf(
        WorkflowWirePayloadKeys.RESUME_MODE to view.resumeMode.wireValue,
        WorkflowWirePayloadKeys.RESUME_STEP_ID to view.resumeStepId,
        WorkflowWirePayloadKeys.LAST_COMPLETED_STEP_ID to view.lastCompletedStepId,
        WorkflowWirePayloadKeys.AVAILABLE_ARTIFACTS to view.availableArtifacts,
        WorkflowWirePayloadKeys.REQUIRED_ARTIFACTS to view.requiredArtifacts,
        WorkflowWirePayloadKeys.MISSING_ARTIFACTS to view.missingArtifacts,
        WorkflowWirePayloadKeys.CAN_RESUME to view.canResume,
        WorkflowWirePayloadKeys.NEXT_ACTION to view.nextAction,
      ),
    ),
  )

  fun continueMap(view: WorkflowContinueView): JsonPayloadContract = payload(
    WorkflowContracts.continuePayload(
      resumeMap(view.resume).toPayload(),
      linkedMapOf(
        WorkflowWirePayloadKeys.SKILL_NAME to view.skillName,
        WorkflowWirePayloadKeys.WORKFLOW_STATUS_BEFORE_CONTINUE to view.workflowStatusBeforeContinue,
        WorkflowWirePayloadKeys.CONTINUE_STATUS to view.continueStatus.wireValue,
        WorkflowWirePayloadKeys.CONTINUE_STEP_ID to view.continueStepId,
        WorkflowWirePayloadKeys.CONTINUE_STEP_LABEL to view.continueStepLabel,
        WorkflowWirePayloadKeys.CONTINUE_STEP_DIRECTIVE to view.continueStepDirective,
        WorkflowWirePayloadKeys.REFERENCE_SECTIONS to view.referenceSections,
        WorkflowWirePayloadKeys.STEP_ARTIFACT_KEYS to view.stepArtifactKeys,
        WorkflowWirePayloadKeys.STEP_ARTIFACTS to view.stepArtifacts.toMap(),
        WorkflowWirePayloadKeys.SESSION_SUMMARY to view.sessionSummary.toPayload(),
        WorkflowWirePayloadKeys.CONTINUATION_BRIEF to view.continuationBrief,
        WorkflowWirePayloadKeys.CONTINUATION_ENTRY_PROMPT to view.continuationEntryPrompt,
        WorkflowWirePayloadKeys.EXTRA_FIELDS to view.extraFields.toMap(),
      ),
    ),
  )

  fun compactContinueMap(view: WorkflowCompactContinueView): JsonPayloadContract = payload(
    linkedMapOf(
      SharedPayloadKeys.WORKFLOW_ID to view.workflowId,
      WorkflowWirePayloadKeys.SKILL_NAME to view.skillName,
      WorkflowWirePayloadKeys.WORKFLOW_STATUS_BEFORE_CONTINUE to view.workflowStatusBeforeContinue,
      WorkflowWirePayloadKeys.STARTED_AT to view.startedAt,
      WorkflowWirePayloadKeys.UPDATED_AT to view.updatedAt,
      WorkflowWirePayloadKeys.CONTINUE_STATUS to view.continueStatus.wireValue,
      WorkflowWirePayloadKeys.RESUME_STEP_ID to view.resumeStepId,
      WorkflowWirePayloadKeys.RESUME_STEP_LABEL to view.resumeStepLabel,
      WorkflowWirePayloadKeys.CONTINUE_STEP_ID to view.resumeStepId,
      WorkflowWirePayloadKeys.CONTINUE_STEP_LABEL to view.resumeStepLabel,
      WorkflowWirePayloadKeys.CONTINUE_STEP_DIRECTIVE to view.continueStepDirective,
      WorkflowWirePayloadKeys.REFERENCE_SECTIONS to view.referenceSections,
      WorkflowWirePayloadKeys.REQUIRED_ARTIFACT_KEYS to view.requiredArtifactKeys,
      WorkflowWirePayloadKeys.AVAILABLE_ARTIFACT_KEYS to view.availableArtifactKeys,
      WorkflowWirePayloadKeys.MISSING_ARTIFACT_KEYS to view.missingArtifactKeys,
      WorkflowWirePayloadKeys.REQUIRED_ARTIFACTS to view.requiredArtifactKeys,
      WorkflowWirePayloadKeys.AVAILABLE_ARTIFACTS to view.availableArtifactKeys,
      WorkflowWirePayloadKeys.MISSING_ARTIFACTS to view.missingArtifactKeys,
      WorkflowWirePayloadKeys.CURRENT_STEP_ARTIFACTS to view.currentStepArtifacts.map(::artifactSummaryMap),
      WorkflowWirePayloadKeys.OMITTED_ARTIFACT_KEYS to view.omittedArtifactKeys,
      WorkflowWirePayloadKeys.CONTINUATION_BRIEF to view.continuationBrief,
      WorkflowWirePayloadKeys.CONTINUATION_ENTRY_PROMPT to view.continuationEntryPrompt,
      WorkflowWirePayloadKeys.READ_ONLY_FULL_STATE_GUIDANCE to view.readOnlyFullStateGuidance,
    ),
  )

  fun updateAcknowledgementMap(view: WorkflowUpdateAcknowledgementView): JsonPayloadContract = payload(
    linkedMapOf(
      SharedPayloadKeys.STATUS to view.status,
      SharedPayloadKeys.WORKFLOW_ID to view.workflowId,
      WorkflowWirePayloadKeys.WORKFLOW_NAME to view.workflowName,
      WorkflowWirePayloadKeys.WORKFLOW_STATUS to view.workflowStatus,
      WorkflowWirePayloadKeys.CURRENT_STEP_ID to view.currentStepId,
      WorkflowWirePayloadKeys.UPDATED_STEP_IDS to view.updatedStepIds,
      WorkflowWirePayloadKeys.UPDATED_ARTIFACT_KEYS to view.updatedArtifactKeys,
      WorkflowWirePayloadKeys.READ_ONLY_FULL_STATE_GUIDANCE to view.readOnlyFullStateGuidance,
    ),
  )

  fun inputProjectionMap(projection: WorkflowInputProjection): JsonPayloadContract = payload(
    linkedMapOf(
      SharedPayloadKeys.STEP_ID to projection.stepId,
      WorkflowWirePayloadKeys.PRODUCER_ITERATION to projection.producerIteration,
      ReviewVerificationSignalKeys.REPOSITORY_CHECKPOINT to projection.repositoryCheckpoint,
      WorkflowWirePayloadKeys.ARTIFACTS to projection.artifacts.toMap(),
      WorkflowWirePayloadKeys.UTF8_BYTES to projection.utf8Bytes,
    ),
  )

  private fun payload(map: Map<String, Any?>): JsonPayloadContract = WorkflowWirePayload(map)
}

private fun artifactSummaryMap(summary: WorkflowContinuationArtifactSummary): Map<String, Any?> = linkedMapOf(
  WorkflowWirePayloadKeys.KEY to summary.key,
  WorkflowWirePayloadKeys.PRESENT to summary.present,
  WorkflowWirePayloadKeys.INLINE to summary.inline,
  WorkflowWirePayloadKeys.SIZE_BYTES to summary.sizeBytes,
  SharedPayloadKeys.VALUE to summary.value.raw,
  WorkflowWirePayloadKeys.PREVIEW to summary.preview,
  WorkflowWirePayloadKeys.TRUNCATED to summary.truncated,
  WorkflowWirePayloadKeys.OMITTED to summary.omitted,
  WorkflowWirePayloadKeys.OMISSION_REASON to summary.omissionReason,
)

private class WorkflowWirePayload(
  private val payload: Map<String, Any?>,
) : JsonPayloadContract {
  override fun toPayload(): Map<String, Any?> = payload
}

private fun workflowStepWireMap(step: WorkflowStepState): Map<String, Any?> = linkedMapOf(
  SharedPayloadKeys.STEP_ID to step.stepId,
  SharedPayloadKeys.STATUS to step.status,
  WorkflowWirePayloadKeys.ATTEMPT_COUNT to step.attemptCount,
)
