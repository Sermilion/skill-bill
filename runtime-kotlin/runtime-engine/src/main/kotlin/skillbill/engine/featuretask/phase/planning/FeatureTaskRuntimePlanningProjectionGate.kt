package skillbill.engine.featuretask.phase.planning

import skillbill.contracts.SharedPayloadKeys
import skillbill.engine.featuretask.runner.boundedSchemaGateDetail
import skillbill.error.shellcontent.InvalidGoalPlanningPreparationSchemaError
import skillbill.workflow.model.WorkflowStepStatus
import skillbill.workflow.model.workflowStepStatus
import skillbill.workflow.taskruntime.artifact.FeatureTaskRuntimeWorkflowArtifactMap
import skillbill.workflow.taskruntime.model.core.FeatureTaskRuntimeWireArtifactValidator
import skillbill.workflow.taskruntime.model.phase.FeatureTaskRuntimePlanningProjectionContract

internal fun producerProjectionGateReason(
  phaseId: String,
  outputMap: FeatureTaskRuntimeWorkflowArtifactMap,
  planningProjectionValidator: FeatureTaskRuntimeWireArtifactValidator,
): String? {
  if ((outputMap[SharedPayloadKeys.STATUS] as? String).workflowStepStatus() != WorkflowStepStatus.COMPLETED) return null
  val expectedKind =
    FeatureTaskRuntimePlanningProjectionContract.producedProjectionKindFor(phaseId)
      ?: return null
  return unresolvedProducerProjectionKindReason(phaseId, expectedKind, planningProjectionValidator)
}

internal fun requireValidPlanningProjection(
  envelope: FeatureTaskRuntimeWorkflowArtifactMap,
  phaseId: String,
  sourceLabel: String,
  planningProjectionValidator: FeatureTaskRuntimeWireArtifactValidator,
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
  planningProjectionValidator: FeatureTaskRuntimeWireArtifactValidator,
): String {
  val validatorLabel =
    planningProjectionValidator::class.qualifiedName
      ?: planningProjectionValidator::class.java.name
  return "Phase '$phaseId' reported 'completed' but producedProjectionKindFor names '$expectedKind' " +
    "while no producer-side planning projection parser is wired for that kind " +
    "(validator=$validatorLabel)."
}
