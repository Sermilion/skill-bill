package skillbill.infrastructure.sqlite.workflow.featuretask
import skillbill.infrastructure.sqlite.workflow.FeatureTaskWorkflowUpsertRequest
import skillbill.infrastructure.sqlite.workflow.defaultContractVersion
import skillbill.infrastructure.sqlite.workflow.defaultImplementationSkill
import skillbill.infrastructure.sqlite.workflow.getFeatureTaskWorkflowRowAsMode
import skillbill.infrastructure.sqlite.workflow.getFeatureTaskWorkflowRows
import skillbill.infrastructure.sqlite.workflow.listFeatureTaskWorkflowRows
import skillbill.infrastructure.sqlite.workflow.upsertFeatureTaskWorkflowRow
import skillbill.ports.workflow.FeatureTaskRuntimeWorkflowStateRepository
import skillbill.ports.workflow.WorkflowSnapshotValidator
import skillbill.ports.workflow.model.WorkflowStateRecord
import skillbill.workflow.model.FeatureTaskWorkflowMode
import java.sql.Connection
import java.time.Clock

internal class FeatureTaskRuntimeWorkflowStateStore(
  private val connection: Connection,
  private val clock: Clock,
  private val workflowSnapshotValidator: WorkflowSnapshotValidator,
) : FeatureTaskRuntimeWorkflowStateRepository {
  override fun saveFeatureTaskRuntimeWorkflow(row: WorkflowStateRecord) {
    connection.upsertFeatureTaskWorkflowRow(
      row = row,
      request =
        FeatureTaskWorkflowUpsertRequest(
          mode = FeatureTaskWorkflowMode.RUNTIME,
          implementationSkill =
            row.implementationSkill.orEmpty().ifBlank {
              FeatureTaskWorkflowMode.RUNTIME.defaultImplementationSkill
            },
          defaultContractVersion = FeatureTaskWorkflowMode.RUNTIME.defaultContractVersion,
          clock = clock,
          workflowSnapshotValidator = workflowSnapshotValidator,
        ),
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
