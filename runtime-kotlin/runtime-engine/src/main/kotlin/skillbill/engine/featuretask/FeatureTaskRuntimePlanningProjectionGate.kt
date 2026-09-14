package skillbill.engine.featuretask

import skillbill.contracts.SharedPayloadKeys
import skillbill.error.InvalidGoalPlanningPreparationSchemaError
import skillbill.workflow.model.WorkflowStepStatus
import skillbill.workflow.model.workflowStepStatus
import skillbill.workflow.taskruntime.FeatureTaskRuntimePlanningProjectionValidator
import skillbill.workflow.taskruntime.FeatureTaskRuntimeWorkflowArtifactMap
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimePlanningProjectionContract

internal fun producerProjectionGateReason(
  phaseId: String,
  outputMap: FeatureTaskRuntimeWorkflowArtifactMap,
  planningProjectionValidator: FeatureTaskRuntimePlanningProjectionValidator,
): String? {
  if ((outputMap[SharedPayloadKeys.STATUS] as? String).workflowStepStatus() != WorkflowStepStatus.COMPLETED) return null
  val expectedKind = FeatureTaskRuntimePlanningProjectionContract.producedProjectionKindFor(phaseId)
    ?: return null
  return unresolvedProducerProjectionKindReason(phaseId, expectedKind, planningProjectionValidator)
}

internal fun requireValidPlanningProjection(
  envelope: FeatureTaskRuntimeWorkflowArtifactMap,
  phaseId: String,
  sourceLabel: String,
  planningProjectionValidator: FeatureTaskRuntimePlanningProjectionValidator,
  fieldPath: String = "${phaseId}_payload",
) {
  producerProjectionGateReason(phaseId, envelope, planningProjectionValidator)?.let { reason ->
    throw InvalidGoalPlanningPreparationSchemaError(
      sourceLabel = sourceLabel,
      fieldPath = fieldPath,
      reason = boundedSchemaGateDetail(reason),
    )
  }
}

private fun unresolvedProducerProjectionKindReason(
  phaseId: String,
  expectedKind: String,
  planningProjectionValidator: FeatureTaskRuntimePlanningProjectionValidator,
): String {
  val validatorLabel = planningProjectionValidator::class.qualifiedName
    ?: planningProjectionValidator::class.java.name
  return "Phase '$phaseId' reported 'completed' but producedProjectionKindFor names '$expectedKind' " +
    "while no producer-side planning projection parser is wired for that kind " +
    "(validator=$validatorLabel)."
}
