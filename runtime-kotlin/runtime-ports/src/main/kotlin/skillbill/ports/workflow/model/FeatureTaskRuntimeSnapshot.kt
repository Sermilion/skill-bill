package skillbill.ports.workflow.model

import skillbill.workflow.model.FeatureTaskExecutionIdentity

data class FeatureTaskRuntimeSnapshot(
  val workflow: WorkflowStateRecord,
  val identity: FeatureTaskExecutionIdentity? = null,
)
