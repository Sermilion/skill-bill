package skillbill.application.workflow.model

import skillbill.ports.db.DatabaseSessionFactory
import skillbill.ports.workflow.model.WorkflowFamily
import skillbill.ports.workflow.WorkflowSnapshotValidator
import skillbill.workflow.engine.WorkflowEngine
import skillbill.workflow.model.FeatureTaskExecutionIdentity

internal data class PersistOpenedWorkflowArgs(
  val family: WorkflowFamily,
  val workflowId: String,
  val effectiveSessionId: String,
  val stepId: String,
  val issueKey: String?,
  val executionIdentity: FeatureTaskExecutionIdentity?,
  val engine: WorkflowEngine,
  val workflowSnapshotValidator: WorkflowSnapshotValidator,
  val repositoryCheckpointIdentity: () -> String = { "" },
  val database: DatabaseSessionFactory,
)
