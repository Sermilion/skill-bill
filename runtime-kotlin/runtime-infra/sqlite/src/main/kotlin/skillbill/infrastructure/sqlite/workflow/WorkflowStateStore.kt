package skillbill.infrastructure.sqlite.workflow

import skillbill.ports.workflow.FeatureImplementWorkflowStateRepository
import skillbill.ports.workflow.FeatureTaskExecutionLookupRepository
import skillbill.ports.workflow.FeatureTaskRuntimeWorkerRepository
import skillbill.ports.workflow.FeatureTaskRuntimeWorkflowStateRepository
import skillbill.ports.workflow.FeatureTaskWorkflowRowRepository
import skillbill.ports.workflow.FeatureTaskWorkflowStateRepository
import skillbill.ports.workflow.FeatureVerifyWorkflowStateRepository
import skillbill.ports.workflow.GoalChildWorkflowStateRepository
import skillbill.ports.workflow.WorkflowStateRepository
import skillbill.ports.workflow.model.WorkflowStateRecord
import java.sql.Connection
import java.time.Clock

internal typealias WorkflowStateRow = WorkflowStateRecord

internal const val DELETE_GOAL_CHILD_FIRST_STATUS_INDEX: Int = 2
internal const val MINIMUM_OWNER_TOKEN_LENGTH: Int = 16

internal class WorkflowStateStore private constructor(
  connection: Connection,
  clock: Clock,
  featureTaskStore: FeatureTaskWorkflowStateStore,
) : WorkflowStateRepository,
  FeatureTaskWorkflowStateRepository by featureTaskStore,
  GoalChildWorkflowStateRepository by featureTaskStore,
  FeatureTaskRuntimeWorkerRepository by featureTaskStore,
  FeatureImplementWorkflowStateRepository by FeatureImplementWorkflowStateStore(connection),
  FeatureVerifyWorkflowStateRepository by FeatureVerifyWorkflowStateStore(connection, clock),
  FeatureTaskRuntimeWorkflowStateRepository by FeatureTaskRuntimeWorkflowStateStore(connection, clock) {
  constructor(connection: Connection, clock: Clock) : this(connection, clock, FeatureTaskWorkflowStateStore(connection, clock))
}

internal class FeatureTaskWorkflowStateStore(
  connection: Connection,
  clock: Clock,
) : FeatureTaskWorkflowStateRepository,
  FeatureTaskExecutionLookupRepository by FeatureTaskExecutionLookupStore(connection),
  FeatureTaskWorkflowRowRepository by FeatureTaskWorkflowRowStore(connection, clock),
  GoalChildWorkflowStateRepository by GoalChildWorkflowStore(connection),
  FeatureTaskRuntimeWorkerRepository by FeatureTaskRuntimeWorkerStore(connection)
