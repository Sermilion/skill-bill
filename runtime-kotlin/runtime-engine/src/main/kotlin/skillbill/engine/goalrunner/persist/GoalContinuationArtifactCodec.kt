package skillbill.engine.goalrunner.persist
import skillbill.application.workflow.model.WorkflowFamily
import skillbill.contracts.JsonCodec
import skillbill.contracts.SharedPayloadKeys
import skillbill.contracts.workflow.identity.task.FeatureTaskRuntimeGoalContinuationArtifactPayloadKeys
import skillbill.engine.goalrunner.model.GoalContinuation
import skillbill.error.shellcontent.InvalidGoalSubtaskReviewStateSchemaError
import skillbill.error.shellcontent.InvalidWorkflowStateSchemaError
import skillbill.goalrunner.asGoalRunnerIntOrNull
import skillbill.goalrunner.subtaskreview.GoalSubtaskReviewSummaryReducer
import skillbill.goalrunner.subtaskreview.recordedVerdicts
import skillbill.ports.persistence.UnitOfWork
import skillbill.ports.workflow.WorkflowStateRepository
import skillbill.ports.workflow.get
import skillbill.ports.workflow.model.toSnapshot
import skillbill.workflow.engine.model.WorkflowStateSnapshot
import skillbill.workflow.goal.model.GOAL_SUBTASK_REVIEW_STATE_ARTIFACT_KEY
import skillbill.workflow.goal.model.GoalSubtaskReviewArtifactDecoder
import skillbill.workflow.goal.model.GoalSubtaskReviewArtifacts
import skillbill.workflow.goal.model.GoalSubtaskReviewPassResult
import skillbill.workflow.taskruntime.artifact.envelopeWireMap
import skillbill.workflow.taskruntime.model.persistence.task.runtime.persistence.FEATURE_TASK_RUNTIME_GOAL_CONTINUATION_ARTIFACT_KEY
import skillbill.workflow.taskruntime.model.phase.requireAcceptedOutput
import skillbill.workflow.taskruntime.phase.task.FeatureTaskRuntimePhaseOutputValidator
import skillbill.workflow.taskruntime.phase.task.FeatureTaskRuntimePhaseWorkflowDefinition
fun goalContinuation(artifacts: Map<String, Any?>): GoalContinuation? =
  (artifacts[FEATURE_TASK_RUNTIME_GOAL_CONTINUATION_ARTIFACT_KEY] as? Map<*, *>)?.let { payload ->
    val issueKey = payload[SharedPayloadKeys.ISSUE_KEY]?.toString()?.takeIf(String::isNotBlank)
    val subtaskId = payload[SharedPayloadKeys.SUBTASK_ID].asGoalRunnerIntOrNull()
    if (issueKey == null || subtaskId == null) {
      null
    } else {
      GoalContinuation(
        issueKey = issueKey,
        subtaskId = subtaskId,
        suppressPr = payload[FeatureTaskRuntimeGoalContinuationArtifactPayloadKeys.SUPPRESS_PR] == true,
        goalBranch = payload[FeatureTaskRuntimeGoalContinuationArtifactPayloadKeys.GOAL_BRANCH]
          ?.toString()
          ?.takeIf(String::isNotBlank),
      )
    }
  }

fun goalReviewArtifacts(artifacts: Map<String, Any?>): GoalSubtaskReviewArtifacts? =
  GoalSubtaskReviewArtifactDecoder.decode(artifacts)

fun validatedGoalReviewPasses(
  review: GoalSubtaskReviewArtifacts,
  phaseOutputValidator: FeatureTaskRuntimePhaseOutputValidator,
  unitOfWork: UnitOfWork,
): List<GoalSubtaskReviewPassResult> {
  review.state.passResults.forEach { pass ->
    val rawResult = review.rawResults.getValue(pass.passNumber.toString())
    val output = goalReviewEmissionEnvelope(rawResult, phaseOutputValidator)
    val recordedVerdicts = GoalSubtaskReviewSummaryReducer.recordedVerdicts(
      unitOfWork.reviews::fetchFindingVerdicts,
      output,
    )
    val findings = GoalSubtaskReviewSummaryReducer.fromOutput(output, recordedVerdicts)
    val outcome = GoalSubtaskReviewSummaryReducer.outcomeFor(output, findings)
    if (
      pass.verdict != outcome.verdict ||
      pass.unresolvedFindingCount != outcome.unresolvedFindingCount ||
      pass.findings != findings
    ) {
      throw InvalidGoalSubtaskReviewStateSchemaError(
        sourceLabel = GOAL_SUBTASK_REVIEW_STATE_ARTIFACT_KEY,
        fieldPath = "pass_results.${pass.passNumber}",
        reason =
        "must exactly match the verdict, unresolved count, and compact findings derived from " +
          "its durable raw review result.",
      )
    }
  }
  return review.state.passResults
}

fun goalReviewEmissionEnvelope(
  rawResult: String,
  phaseOutputValidator: FeatureTaskRuntimePhaseOutputValidator,
): Map<String, Any?> {
  if (JsonCodec.parseObjectOrNull(rawResult.trim()) == null) return emptyMap()
  return phaseOutputValidator
    .validatePhaseOutput(rawResult, FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_REVIEW)
    .requireAcceptedOutput(FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_REVIEW)
    .normalizedOutput
    .envelopeWireMap()
}

fun taskRuntimeRecordOrNull(workflowStates: WorkflowStateRepository, workflowId: String): WorkflowStateSnapshot? = try {
  WorkflowFamily.TASK_RUNTIME.get(workflowStates, workflowId)
} catch (error: InvalidWorkflowStateSchemaError) {
  if (error.message.orEmpty().contains("mode='")) {
    null
  } else {
    throw error
  }
}

fun featureTaskRecordForLegacyControls(
  workflowStates: WorkflowStateRepository,
  workflowId: String,
): WorkflowStateSnapshot? = workflowStates.getFeatureTaskWorkflow(workflowId)?.toSnapshot()
