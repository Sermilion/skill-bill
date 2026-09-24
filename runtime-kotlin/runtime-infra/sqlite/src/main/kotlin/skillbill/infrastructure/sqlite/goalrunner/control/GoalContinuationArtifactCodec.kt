package skillbill.infrastructure.sqlite.goalrunner.control
import skillbill.contracts.JsonCodec
import skillbill.contracts.SharedPayloadKeys
import skillbill.contracts.decomposition.DecompositionManifestPayloadKeys
import skillbill.contracts.workflow.identity.task.FeatureTaskRuntimeGoalContinuationArtifactPayloadKeys
import skillbill.error.shellcontent.InvalidWorkflowStateSchemaError
import skillbill.goalrunner.goalReviewArtifacts
import skillbill.goalrunner.validatedGoalReviewPasses
import skillbill.ports.goalrunner.runner.model.GoalRunnerOutOfBandAcceptance
import skillbill.ports.goalrunner.runner.model.GoalRunnerReviewPolicy
import skillbill.ports.workflow.WorkflowStateRepository
import skillbill.ports.workflow.get
import skillbill.ports.workflow.model.WorkflowFamily
import skillbill.review.context.model.launch.CodeReviewExecutionMode
import skillbill.workflow.engine.model.WorkflowStateSnapshot
import skillbill.workflow.engine.model.DurableWorkflowArtifactFamily
import skillbill.workflow.taskruntime.model.phase.requireAcceptedOutput
import skillbill.ports.taskruntime.FeatureTaskRuntimePhaseOutputValidator
import skillbill.workflow.taskruntime.phase.task.FeatureTaskRuntimePhaseWorkflowDefinition

internal fun goalReviewEmissionEnvelope(
  rawResult: String,
  phaseOutputValidator: FeatureTaskRuntimePhaseOutputValidator,
): Map<String, Any?> {
  if (JsonCodec.parseObjectOrNull(rawResult.trim()) == null) return emptyMap<String, Any?>()
  return JsonCodec.anyToStringAnyMap(
    phaseOutputValidator
      .validatePhaseOutput(rawResult, FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_REVIEW)
      .requireAcceptedOutput(FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_REVIEW)
      .normalizedOutput
      .envelopePayload(),
  ) ?: error("Normalized review output was not a string-keyed object.")
}

internal fun taskRuntimeRecordOrNull(
  workflowStates: WorkflowStateRepository,
  workflowId: String,
): WorkflowStateSnapshot? =
  try {
    WorkflowFamily.TASK_RUNTIME.get(workflowStates, workflowId)
  } catch (error: InvalidWorkflowStateSchemaError) {
    if (error.message.orEmpty().contains("mode='")) {
      null
    } else {
      throw error
    }
  }

internal fun reviewPolicyFromLegacyArtifacts(artifacts: Map<String, Any?>): GoalRunnerReviewPolicy? {
  val artifactFamily = DurableWorkflowArtifactFamily.GOAL_REVIEW_POLICY
  val raw = artifactFamily.value(artifacts) ?: return null
  val policy =
    JsonCodec.anyToStringAnyMap(raw)
      ?: error("Goal review policy artifact '${artifactFamily.label()}' must be a map.")
  val allowedKeys =
    setOf(
      FeatureTaskRuntimeGoalContinuationArtifactPayloadKeys.CODE_REVIEW_MODE,
      FeatureTaskRuntimeGoalContinuationArtifactPayloadKeys.PARALLEL_REVIEW_AGENT,
      FeatureTaskRuntimeGoalContinuationArtifactPayloadKeys.AGENT_ADDON_SELECTION,
    )
  policy.keys.forEach { key ->
    require(key in allowedKeys) {
      "Goal review policy artifact '${artifactFamily.label()}' has unsupported field '$key'."
    }
  }
  val mode =
    policy[FeatureTaskRuntimeGoalContinuationArtifactPayloadKeys.CODE_REVIEW_MODE] as? String
      ?: error("Goal review policy artifact '${artifactFamily.label()}' is missing code_review_mode.")
  val codeReviewMode =
    try {
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
  val artifactFamily = DurableWorkflowArtifactFamily.GOAL_OUT_OF_BAND_ACCEPTANCE
  val raw = artifactFamily.value(artifacts) ?: return emptyMap()
  val entries =
    raw as? List<*>
      ?: error("Goal acceptance artifact '${artifactFamily.label()}' must be a list.")
  return entries.associate { element ->
    val entry =
      JsonCodec.anyToStringAnyMap(element)
        ?: error("Goal acceptance artifact '${artifactFamily.label()}' entries must be maps.")
    val acceptance =
      GoalRunnerOutOfBandAcceptance(
        subtaskId =
          (entry[SharedPayloadKeys.SUBTASK_ID] as? Number)?.toInt()
            ?: error("Goal acceptance artifact entry is missing a numeric subtask_id."),
        commitSha =
          entry[DecompositionManifestPayloadKeys.COMMIT_SHA] as? String
            ?: error("Goal acceptance artifact entry is missing commit_sha."),
        reason =
          entry["reason"] as? String
            ?: error("Goal acceptance artifact entry is missing reason."),
        acceptedAt =
          entry["accepted_at"] as? String
            ?: error("Goal acceptance artifact entry is missing accepted_at."),
      )
    acceptance.subtaskId to acceptance
  }
}
