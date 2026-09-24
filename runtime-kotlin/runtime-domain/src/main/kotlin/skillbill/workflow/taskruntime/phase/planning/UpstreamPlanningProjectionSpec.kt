package skillbill.workflow.taskruntime.phase.planning

import skillbill.workflow.taskruntime.model.handoff.PhaseHandoffProjectionDelivery
import skillbill.workflow.taskruntime.model.handoff.task.FeatureTaskRuntimeHandoffSourceRef
import skillbill.workflow.taskruntime.phase.task.FeatureTaskRuntimePhaseWorkflowDefinition

internal data class UpstreamPlanningProjectionSpec(
  val consumerPhaseId: String,
  val sourceRef: FeatureTaskRuntimeHandoffSourceRef,
  val projectionName: String,
  val projectionContractId: String,
  val declaredFieldNames: List<String>,
  val delivery: PhaseHandoffProjectionDelivery,
  val projectionContractVersion: String = FeatureTaskRuntimePhaseWorkflowDefinition.PhaseProjectionContract.VERSION,
)
