package skillbill.ports.workflow.model

import skillbill.workflow.model.FeatureTaskExecutionIdentity

data class FeatureTaskWorkflowCandidate(
  val identity: FeatureTaskExecutionIdentity?,
  val workflow: WorkflowStateRecord,
)
