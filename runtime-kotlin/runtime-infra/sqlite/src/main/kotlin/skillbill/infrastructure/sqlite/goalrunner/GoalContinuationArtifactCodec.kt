package skillbill.infrastructure.sqlite.goalrunner
import skillbill.contracts.JsonCodec
import skillbill.contracts.SharedPayloadKeys
import skillbill.contracts.decomposition.DecompositionManifestPayloadKeys
import skillbill.contracts.workflow.FeatureTaskRuntimeGoalContinuationArtifactPayloadKeys
import skillbill.error.InvalidGoalSubtaskReviewStateSchemaError
import skillbill.error.InvalidWorkflowStateSchemaError
import skillbill.goalrunner.GOAL_OUT_OF_BAND_ACCEPTANCE_ARTIFACT_KEY
import skillbill.goalrunner.GOAL_REVIEW_POLICY_ARTIFACT_KEY
import skillbill.goalrunner.asGoalRunnerIntOrNull
import skillbill.goalrunner.model.GoalContinuation
import skillbill.goalrunner.subtaskreview.GoalSubtaskReviewStructuredFindingsParse
import skillbill.goalrunner.subtaskreview.GoalSubtaskReviewSummaryReducer
import skillbill.ports.goalrunner.GoalRunnerPersistenceSession
import skillbill.ports.goalrunner.runner.model.GoalRunnerOutOfBandAcceptance
import skillbill.ports.goalrunner.runner.model.GoalRunnerReviewPolicy
import skillbill.ports.workflow.WorkflowStateRepository
import skillbill.ports.workflow.get
import skillbill.ports.workflow.model.WorkflowFamily
import skillbill.review.context.model.CodeReviewExecutionMode
import skillbill.workflow.engine.model.WorkflowStateSnapshot
import skillbill.workflow.goal.model.GOAL_SUBTASK_REVIEW_STATE_ARTIFACT_KEY
import skillbill.workflow.goal.model.GoalSubtaskReviewArtifactDecoder
import skillbill.workflow.goal.model.GoalSubtaskReviewArtifacts
import skillbill.workflow.goal.model.GoalSubtaskReviewPassResult
import skillbill.workflow.taskruntime.FeatureTaskRuntimePhaseOutputValidator
import skillbill.workflow.taskruntime.FeatureTaskRuntimePhaseWorkflowDefinition
import skillbill.workflow.taskruntime.model.requireAcceptedOutput

internal fun goalContinuation(artifacts: Map<String, Any?>): GoalContinuation? =
  (artifacts["goal_continuation"] as? Map<*, *>)?.let { payload ->
    val issueKey = payload[FeatureTaskRuntimeGoalContinuationArtifactPayloadKeys.ISSUE_KEY]
      ?.toString()
      ?.takeIf(String::isNotBlank)
    val subtaskId =
      payload[FeatureTaskRuntimeGoalContinuationArtifactPayloadKeys.SUBTASK_ID].asGoalRunnerIntOrNull()
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

internal fun goalReviewArtifacts(artifacts: Map<String, Any?>): GoalSubtaskReviewArtifacts? =
  GoalSubtaskReviewArtifactDecoder.decode(artifacts)

internal fun validatedGoalReviewPasses(
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

internal fun goalReviewEmissionEnvelope(
  rawResult: String,
  phaseOutputValidator: FeatureTaskRuntimePhaseOutputValidator,
): Any {
  if (JsonCodec.parseObjectOrNull(rawResult.trim()) == null) return emptyMap<String, Any?>()
  return phaseOutputValidator
    .validatePhaseOutput(rawResult, FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_REVIEW)
    .requireAcceptedOutput(FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_REVIEW)
    .normalizedOutput
    .envelopePayload()
}

internal fun taskRuntimeRecordOrNull(
  workflowStates: WorkflowStateRepository,
  workflowId: String,
): WorkflowStateSnapshot? = try {
  WorkflowFamily.TASK_RUNTIME.get(workflowStates, workflowId)
} catch (error: InvalidWorkflowStateSchemaError) {
  if (error.message.orEmpty().contains("mode='")) {
    null
  } else {
    throw error
  }
}

internal fun reviewPolicyFromLegacyArtifacts(artifacts: Map<String, Any?>): GoalRunnerReviewPolicy? {
  val raw = artifacts[GOAL_REVIEW_POLICY_ARTIFACT_KEY] ?: return null
  val policy = JsonCodec.anyToStringAnyMap(raw)
    ?: error("Goal review policy artifact '$GOAL_REVIEW_POLICY_ARTIFACT_KEY' must be a map.")
  val allowedKeys = setOf(
    FeatureTaskRuntimeGoalContinuationArtifactPayloadKeys.CODE_REVIEW_MODE,
    FeatureTaskRuntimeGoalContinuationArtifactPayloadKeys.PARALLEL_REVIEW_AGENT,
    FeatureTaskRuntimeGoalContinuationArtifactPayloadKeys.AGENT_ADDON_SELECTION,
  )
  policy.keys.forEach { key ->
    require(key in allowedKeys) {
      "Goal review policy artifact '$GOAL_REVIEW_POLICY_ARTIFACT_KEY' has unsupported field '$key'."
    }
  }
  val mode = policy[FeatureTaskRuntimeGoalContinuationArtifactPayloadKeys.CODE_REVIEW_MODE] as? String
    ?: error("Goal review policy artifact '$GOAL_REVIEW_POLICY_ARTIFACT_KEY' is missing code_review_mode.")
  val codeReviewMode = try {
    CodeReviewExecutionMode.fromWire(mode)
  } catch (error: IllegalArgumentException) {
    throw IllegalStateException("Goal review policy artifact has invalid code_review_mode '$mode'.", error)
  }
  val agentAddonSelection =
    decodeGoalAgentAddonSelection(policy[FeatureTaskRuntimeGoalContinuationArtifactPayloadKeys.AGENT_ADDON_SELECTION])
  return GoalRunnerReviewPolicy(codeReviewMode, agentAddonSelection)
}

internal fun outOfBandAcceptancesFromLegacyArtifacts(
  artifacts: Map<String, Any?>,
): Map<Int, GoalRunnerOutOfBandAcceptance> {
  val raw = artifacts[GOAL_OUT_OF_BAND_ACCEPTANCE_ARTIFACT_KEY] ?: return emptyMap()
  val entries = raw as? List<*>
    ?: error("Goal acceptance artifact '$GOAL_OUT_OF_BAND_ACCEPTANCE_ARTIFACT_KEY' must be a list.")
  return entries.associate { element ->
    val entry = JsonCodec.anyToStringAnyMap(element)
      ?: error("Goal acceptance artifact '$GOAL_OUT_OF_BAND_ACCEPTANCE_ARTIFACT_KEY' entries must be maps.")
    val acceptance = GoalRunnerOutOfBandAcceptance(
      subtaskId = (entry[SharedPayloadKeys.SUBTASK_ID] as? Number)?.toInt()
        ?: error("Goal acceptance artifact entry is missing a numeric subtask_id."),
      commitSha = entry[DecompositionManifestPayloadKeys.COMMIT_SHA] as? String
        ?: error("Goal acceptance artifact entry is missing commit_sha."),
      reason = entry["reason"] as? String
        ?: error("Goal acceptance artifact entry is missing reason."),
      acceptedAt = entry["accepted_at"] as? String
        ?: error("Goal acceptance artifact entry is missing accepted_at."),
    )
    acceptance.subtaskId to acceptance
  }
}
