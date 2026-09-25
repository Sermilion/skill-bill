package skillbill.infrastructure.sqlite.workflow

import skillbill.infrastructure.sqlite.workflow.featuretask.FeatureImplementWorkflowStateStore
import skillbill.infrastructure.sqlite.workflow.featuretask.FeatureTaskExecutionLookupStore
import skillbill.infrastructure.sqlite.workflow.featuretask.FeatureTaskRuntimeWorkerStore
import skillbill.infrastructure.sqlite.workflow.featuretask.FeatureTaskRuntimeWorkflowStateStore
import skillbill.infrastructure.sqlite.workflow.featuretask.FeatureTaskWorkflowRowStore
import skillbill.infrastructure.sqlite.workflow.featuretask.FeatureVerifyWorkflowStateStore
import skillbill.infrastructure.sqlite.workflow.goalrunner.child.GoalChildWorkflowStore
import skillbill.ports.workflow.FeatureImplementWorkflowStateRepository
import skillbill.ports.workflow.FeatureTaskExecutionLookupRepository
import skillbill.ports.workflow.FeatureTaskRuntimeWorkerRepository
import skillbill.ports.workflow.FeatureTaskRuntimeWorkflowStateRepository
import skillbill.ports.workflow.FeatureTaskWorkflowStateRepository
import skillbill.ports.workflow.FeatureVerifyWorkflowStateRepository
import skillbill.ports.workflow.GoalChildWorkflowStateRepository
import skillbill.ports.workflow.WorkflowSnapshotValidator
import skillbill.ports.workflow.WorkflowStateRepository
import skillbill.ports.workflow.model.WorkflowStateRecord
import skillbill.workflow.model.FeatureTaskWorkflowMode
import java.sql.Connection
import java.time.Clock

internal const val DELETE_GOAL_CHILD_FIRST_STATUS_INDEX: Int = 2
internal const val MINIMUM_OWNER_TOKEN_LENGTH: Int = 16

internal class WorkflowStateStore private constructor(
  connection: Connection,
  clock: Clock,
  workflowSnapshotValidator: WorkflowSnapshotValidator,
  featureTaskStore: FeatureTaskWorkflowStateStore,
) : WorkflowStateRepository,
  FeatureTaskWorkflowStateRepository by featureTaskStore,
  GoalChildWorkflowStateRepository by featureTaskStore,
  FeatureTaskRuntimeWorkerRepository by featureTaskStore,
  FeatureImplementWorkflowStateRepository by FeatureImplementWorkflowStateStore(connection),
  FeatureVerifyWorkflowStateRepository by FeatureVerifyWorkflowStateStore(connection, clock, workflowSnapshotValidator),
  FeatureTaskRuntimeWorkflowStateRepository by
  FeatureTaskRuntimeWorkflowStateStore(connection, clock, workflowSnapshotValidator) {
  constructor(
    connection: Connection,
    clock: Clock,
    workflowSnapshotValidator: WorkflowSnapshotValidator,
  ) : this(
    connection,
    clock,
    workflowSnapshotValidator,
    FeatureTaskWorkflowStateStore(connection, clock, workflowSnapshotValidator),
  )
}

internal class FeatureTaskWorkflowStateStore(
  connection: Connection,
  clock: Clock,
  workflowSnapshotValidator: WorkflowSnapshotValidator,
) : FeatureTaskWorkflowStateRepository,
  FeatureTaskExecutionLookupRepository by FeatureTaskExecutionLookupStore(connection),
  GoalChildWorkflowStateRepository by GoalChildWorkflowStore(connection),
  FeatureTaskRuntimeWorkerRepository by FeatureTaskRuntimeWorkerStore(connection) {
  private val rows = FeatureTaskWorkflowRowStore(connection, clock, workflowSnapshotValidator)

  override fun terminalizeLegacyProseFeatureTaskWorkflow(row: WorkflowStateRecord) =
    rows.terminalizeLegacyProseFeatureTaskWorkflow(row)

  override fun saveFeatureTaskWorkflow(
    row: WorkflowStateRecord,
    mode: FeatureTaskWorkflowMode,
  ) = rows.saveFeatureTaskWorkflow(row, mode)

  override fun getFeatureTaskWorkflow(workflowId: String): WorkflowStateRecord? =
    rows.getFeatureTaskWorkflow(workflowId)

  override fun getFeatureTaskWorkflowAsMode(
    workflowId: String,
    mode: FeatureTaskWorkflowMode,
  ): WorkflowStateRecord? = rows.getFeatureTaskWorkflowAsMode(workflowId, mode)

  override fun listFeatureTaskWorkflows(
    mode: FeatureTaskWorkflowMode,
    limit: Int,
  ): List<WorkflowStateRecord> = rows.listFeatureTaskWorkflows(mode, limit)

  override fun latestFeatureTaskWorkflow(mode: FeatureTaskWorkflowMode): WorkflowStateRecord? =
    rows.latestFeatureTaskWorkflow(mode)
}
