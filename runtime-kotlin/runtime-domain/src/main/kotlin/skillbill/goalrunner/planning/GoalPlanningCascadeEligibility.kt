package skillbill.goalrunner.planning
import skillbill.workflow.decomposition.model.DecompositionSubtask
import skillbill.workflow.model.DecompositionStatus
import skillbill.workflow.model.decompositionStatus

fun isTerminalWithCommitPlan(subtask: DecompositionSubtask): Boolean =
  subtask.status.decompositionStatus() == DecompositionStatus.COMPLETE && !subtask.commitSha.isNullOrBlank()

fun isTerminalWithCommitPlan(
  status: String,
  commitSha: String?,
): Boolean = status.decompositionStatus() == DecompositionStatus.COMPLETE && !commitSha.isNullOrBlank()

fun cascadeEligiblePlanSubtaskIds(
  plannedIds: Collection<Int>,
  subtasks: Collection<DecompositionSubtask>,
): List<Int> {
  val byId = subtasks.associateBy { it.id }
  return plannedIds.filter { id ->
    val subtask = byId[id]
    subtask == null || !isTerminalWithCommitPlan(subtask)
  }.distinct().sorted()
}
