package skillbill.infrastructure.sqlite.workflow.featuretask
import skillbill.infrastructure.sqlite.workflow.workflow.defaultContractVersion
import skillbill.infrastructure.sqlite.workflow.workflow.defaultImplementationSkill
import skillbill.infrastructure.sqlite.workflow.workflow.getFeatureTaskWorkflowRowAsMode
import skillbill.infrastructure.sqlite.workflow.workflow.getFeatureTaskWorkflowRows
import skillbill.infrastructure.sqlite.workflow.workflow.listFeatureTaskWorkflowRows
import skillbill.infrastructure.sqlite.workflow.workflow.upsertFeatureTaskWorkflowRow
import skillbill.ports.workflow.FeatureTaskRuntimeWorkflowStateRepository
import skillbill.ports.workflow.model.WorkflowStateRecord
import skillbill.workflow.model.FeatureTaskWorkflowMode
import java.sql.Connection
import java.time.Clock

internal class FeatureTaskRuntimeWorkflowStateStore(
  private val connection: Connection,
  private val clock: Clock,
) : FeatureTaskRuntimeWorkflowStateRepository {
  override fun saveFeatureTaskRuntimeWorkflow(row: WorkflowStateRecord) {
    connection.upsertFeatureTaskWorkflowRow(
      row = row,
      mode = FeatureTaskWorkflowMode.RUNTIME,
      implementationSkill = row.implementationSkill.orEmpty().ifBlank {
        FeatureTaskWorkflowMode.RUNTIME.defaultImplementationSkill
      },
      defaultContractVersion = FeatureTaskWorkflowMode.RUNTIME.defaultContractVersion,
      clock = clock,
    )
  }

  override fun getFeatureTaskRuntimeWorkflow(workflowId: String): WorkflowStateRecord? =
    connection.getFeatureTaskWorkflowRowAsMode(workflowId, FeatureTaskWorkflowMode.RUNTIME)

  override fun getFeatureTaskRuntimeWorkflows(workflowIds: Set<String>): Map<String, WorkflowStateRecord> =
    connection.getFeatureTaskWorkflowRows(FeatureTaskWorkflowMode.RUNTIME, workflowIds)

  override fun listFeatureTaskRuntimeWorkflows(limit: Int): List<WorkflowStateRecord> =
    connection.listFeatureTaskWorkflowRows(FeatureTaskWorkflowMode.RUNTIME, limit)

  override fun latestFeatureTaskRuntimeWorkflow(): WorkflowStateRecord? =
    listFeatureTaskRuntimeWorkflows(1).firstOrNull()
}
