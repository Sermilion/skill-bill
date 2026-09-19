package skillbill.infrastructure.sqlite.workflow.goalrunner.goal
import skillbill.infrastructure.sqlite.workflow.decomposition.record
import skillbill.infrastructure.sqlite.workflow.featuretask.workflow
import skillbill.infrastructure.sqlite.workflow.goalrunner.planning.GoalPlanningPreparationRecordSql
import skillbill.infrastructure.sqlite.workflow.goalrunner.planning.deletePreparedByGoal
import skillbill.infrastructure.sqlite.workflow.goalrunner.planning.findByGoalAndSubtask
import skillbill.infrastructure.sqlite.workflow.goalrunner.planning.firstMissingOrIncompleteSubtask
import skillbill.infrastructure.sqlite.workflow.goalrunner.planning.listPreparedByGoalOrdered
import skillbill.infrastructure.sqlite.workflow.goalrunner.planning.markPrepared
import skillbill.infrastructure.sqlite.workflow.goalrunner.planning.parentGoalWorkflowId
import skillbill.infrastructure.sqlite.workflow.goalrunner.planning.preparedCount
import skillbill.infrastructure.sqlite.workflow.goalrunner.planning.preparedStatus
import skillbill.infrastructure.sqlite.workflow.goalrunner.planning.record
import skillbill.infrastructure.sqlite.workflow.goalrunner.planning.subtaskId
import skillbill.infrastructure.sqlite.workflow.goalrunner.runner.subtaskId
import skillbill.infrastructure.sqlite.workflow.goalrunner.subtask.subtaskId
import skillbill.ports.goalrunner.LegacyGoalPlanningPreparationRepository
import skillbill.ports.goalrunner.model.GoalPlanningPreparationRecord
import skillbill.ports.goalrunner.model.GoalPlanningPreparationStatus

internal class LegacyGoalPlanningPreparationStore(
  private val preparationRecord: GoalPlanningPreparationRecordSql,
) : LegacyGoalPlanningPreparationRepository {
  override fun markPrepared(record: GoalPlanningPreparationRecord) {
    preparationRecord.markPrepared(record)
  }

  override fun findByGoalAndSubtask(parentGoalWorkflowId: String, subtaskId: Int): GoalPlanningPreparationRecord? =
    preparationRecord.findByGoalAndSubtask(parentGoalWorkflowId, subtaskId)

  override fun listPreparedByGoalOrdered(parentGoalWorkflowId: String): List<GoalPlanningPreparationRecord> =
    preparationRecord.listPreparedByGoalOrdered(parentGoalWorkflowId)

  override fun preparedCount(parentGoalWorkflowId: String): Int = preparationRecord.preparedCount(parentGoalWorkflowId)

  override fun firstMissingOrIncompleteSubtask(parentGoalWorkflowId: String, orderedSubtaskIds: List<Int>): Int? =
    preparationRecord.firstMissingOrIncompleteSubtask(parentGoalWorkflowId, orderedSubtaskIds)

  override fun preparedStatus(parentGoalWorkflowId: String, subtaskId: Int): GoalPlanningPreparationStatus? =
    preparationRecord.preparedStatus(parentGoalWorkflowId, subtaskId)

  override fun deleteByGoal(parentGoalWorkflowId: String): Int =
    preparationRecord.deletePreparedByGoal(parentGoalWorkflowId)
}
