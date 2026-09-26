package skillbill.infrastructure.sqlite.workflow.goalrunner.planning

import skillbill.infrastructure.sqlite.workflow.goalrunner.shared.GoalSharedPreplanSql
import skillbill.infrastructure.sqlite.workflow.goalrunner.subtask.GoalSubtaskPlanSql
import skillbill.infrastructure.sqlite.workflow.goalrunner.subtask.GoalSubtaskPlanStore
import skillbill.infrastructure.sqlite.workflow.shared.SharedGoalPreplanStore
import skillbill.ports.goalrunner.GoalPlanningPreparationRepository
import skillbill.ports.goalrunner.GoalSubtaskPlanRepository
import skillbill.ports.goalrunner.SharedGoalPreplanRepository
import skillbill.ports.goalrunner.model.GoalPlanningPreparationRecord
import java.sql.Connection

internal class GoalPlanningPreparationStore(
  connection: Connection,
) : GoalPlanningPreparationRepository,
  SharedGoalPreplanRepository by SharedGoalPreplanStore(
    GoalPlanningStatusProjectionSql(connection),
    GoalSharedPreplanSql(connection),
  ),
  GoalSubtaskPlanRepository by GoalSubtaskPlanStore(
    GoalPlanningStatusProjectionSql(connection),
    GoalSubtaskPlanSql(connection, GoalSharedPreplanSql(connection)),
  ) {
  private val sharedPreplan = GoalSharedPreplanSql(connection)
  private val subtaskPlan = GoalSubtaskPlanSql(connection, sharedPreplan)
  internal val preparationRecord = GoalPlanningPreparationRecordSql(connection)

  override fun markPrepared(record: GoalPlanningPreparationRecord) {
    preparationRecord.markPrepared(record)
  }

  override fun deleteByGoal(parentGoalWorkflowId: String): Int {
    val plans = subtaskPlan.deleteAllByGoal(parentGoalWorkflowId)
    val shared = sharedPreplan.deleteAllByGoal(parentGoalWorkflowId)
    return plans + shared + preparationRecord.deletePreparedByGoal(parentGoalWorkflowId)
  }
}
