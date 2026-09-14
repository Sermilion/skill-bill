package skillbill.infrastructure.sqlite.goalrunner
import skillbill.contracts.JsonCodec
import skillbill.contracts.SharedPayloadKeys
import skillbill.error.InvalidGoalSubtaskReviewStateSchemaError
import skillbill.error.InvalidWorkflowStateSchemaError
import skillbill.goalrunner.asGoalRunnerIntOrNull
import skillbill.goalrunner.model.GoalContinuation
import skillbill.goalrunner.subtaskreview.GoalSubtaskReviewStructuredFindingsParse
import skillbill.goalrunner.subtaskreview.GoalSubtaskReviewSummaryReducer
import skillbill.ports.goalrunner.GoalRunnerPersistenceSession
import skillbill.ports.workflow.WorkflowStateRepository
import skillbill.ports.workflow.get
import skillbill.ports.workflow.model.WorkflowFamily
import skillbill.ports.workflow.model.toSnapshot
import skillbill.workflow.engine.model.WorkflowStateSnapshot
import skillbill.workflow.goal.model.GOAL_SUBTASK_REVIEW_STATE_ARTIFACT_KEY
import skillbill.workflow.goal.model.GoalSubtaskReviewArtifactDecoder
import skillbill.workflow.goal.model.GoalSubtaskReviewArtifacts
import skillbill.workflow.goal.model.GoalSubtaskReviewPassResult
import skillbill.workflow.taskruntime.FeatureTaskRuntimePhaseOutputValidator
import skillbill.workflow.taskruntime.FeatureTaskRuntimePhaseWorkflowDefinition
import skillbill.workflow.taskruntime.model.requireAcceptedOutput

fun goalContinuation(artifacts: Map<String, Any?>): GoalContinuation? =
  (artifacts["goal_continuation"] as? Map<*, *>)?.let { payload ->
    val issueKey = payload[SharedPayloadKeys.ISSUE_KEY]?.toString()?.takeIf(String::isNotBlank)
    val subtaskId = payload[SharedPayloadKeys.SUBTASK_ID].asGoalRunnerIntOrNull()
    if (issueKey == null || subtaskId == null) {
      null
    } else {
      GoalContinuation(
        issueKey = issueKey,
        subtaskId = subtaskId,
        suppressPr = payload["suppress_pr"] == true,
        goalBranch = payload["goal_branch"]?.toString()?.takeIf(String::isNotBlank),
      )
    }
  }

fun goalReviewArtifacts(artifacts: Map<String, Any?>): GoalSubtaskReviewArtifacts? =
  GoalSubtaskReviewArtifactDecoder.decode(artifacts)

fun validatedGoalReviewPasses(
  review: GoalSubtaskReviewArtifacts,
  phaseOutputValidator: FeatureTaskRuntimePhaseOutputValidator,
  unitOfWork: GoalRunnerPersistenceSession,
): List<GoalSubtaskReviewPassResult> {
  review.state.passResults.forEach { pass ->
    val rawResult = review.rawResults.getValue(pass.passNumber.toString())
    val output = JsonCodec.anyToStringAnyMap(goalReviewEmissionEnvelope(rawResult, phaseOutputValidator))
      ?: emptyMap()
    val recordedVerdicts = GoalSubtaskReviewStructuredFindingsParse.recordedVerdicts(
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

fun goalReviewEmissionEnvelope(rawResult: String, phaseOutputValidator: FeatureTaskRuntimePhaseOutputValidator): Any {
  if (JsonCodec.parseObjectOrNull(rawResult.trim()) == null) return emptyMap<String, Any?>()
  return phaseOutputValidator
    .validatePhaseOutput(rawResult, FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_REVIEW)
    .requireAcceptedOutput(FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_REVIEW)
    .normalizedOutput
    .envelopePayload()
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
