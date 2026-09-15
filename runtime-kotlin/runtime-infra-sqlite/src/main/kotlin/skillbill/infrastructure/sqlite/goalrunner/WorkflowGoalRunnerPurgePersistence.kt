package skillbill.infrastructure.sqlite.goalrunner

import skillbill.infrastructure.sqlite.SQLiteUnitOfWork
import skillbill.ports.persistence.UnitOfWork
import java.sql.Connection

internal class WorkflowGoalRunnerPurgePersistence(
  private val connection: Connection,
) {
  fun purgeDecomposedGoal(unitOfWork: UnitOfWork, parentWorkflowId: String) {
    val childIds = unitOfWork.workflowStates.listGoalChildWorkflowIdsByParent(parentWorkflowId)
    val workflowIds = buildList {
      add(parentWorkflowId)
      addAll(childIds)
    }
    unitOfWork.goalPlanningPreparations.deleteByGoal(parentWorkflowId)
    connection.prepareStatement(
      "DELETE FROM goal_runner_controls WHERE parent_workflow_id = ?",
    ).use { statement ->
      statement.setString(1, parentWorkflowId)
      statement.executeUpdate()
    }
    deleteByWorkflowIds("goal_run_sessions", workflowIds)
    deleteByWorkflowIds("goal_subtask_events", workflowIds)
    deleteByWorkflowIds("feature_task_phase_settlements", workflowIds)
    connection.prepareStatement(
      "DELETE FROM goal_issue_progress WHERE parent_workflow_id = ?",
    ).use { statement ->
      statement.setString(1, parentWorkflowId)
      statement.executeUpdate()
    }
    unitOfWork.workflowStates.deleteGoalChildWorkflowsByParent(parentWorkflowId)
    connection.prepareStatement(
      "DELETE FROM feature_task_workflows WHERE workflow_id = ?",
    ).use { statement ->
      statement.setString(1, parentWorkflowId)
      statement.executeUpdate()
    }
  }

  private fun deleteByWorkflowIds(table: String, workflowIds: List<String>) {
    if (workflowIds.isEmpty()) return
    val placeholders = workflowIds.joinToString(", ") { "?" }
    connection.prepareStatement(
      "DELETE FROM $table WHERE workflow_id IN ($placeholders)",
    ).use { statement ->
      workflowIds.forEachIndexed { index, workflowId ->
        statement.setString(index + 1, workflowId)
      }
      statement.executeUpdate()
    }
  }
}

internal fun goalRunnerPurgePersistence(unitOfWork: UnitOfWork): WorkflowGoalRunnerPurgePersistence =
  WorkflowGoalRunnerPurgePersistence((unitOfWork as SQLiteUnitOfWork).rawConnection)
