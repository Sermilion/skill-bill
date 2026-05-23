package skillbill.workflow.model

import skillbill.workflow.DECOMPOSITION_MANIFEST_CONTRACT_VERSION

enum class DecompositionExecutionModel(val wireValue: String) {
  SAME_BRANCH_COMMIT_PER_SUBTASK("same_branch_commit_per_subtask"),
  STACKED_BRANCHES("stacked_branches"),
  ;

  companion object {
    fun fromWireValue(value: String): DecompositionExecutionModel? = entries.firstOrNull { it.wireValue == value }
  }
}

data class DecompositionSubtask(
  val id: Int,
  val name: String,
  val specPath: String,
  val status: String = "pending",
  val dependencies: List<DecompositionDependency> = emptyList(),
)

data class DecompositionDependency(
  val subtaskId: Int,
  val optional: Boolean = false,
  val skipped: Boolean = false,
)

data class DecompositionStackBranch(
  val subtaskId: Int,
  val branch: String,
  val baseBranch: String,
)

data class CurrentSubtaskIntent(
  val subtaskId: Int,
  val action: String,
)

data class DecompositionManifest(
  val contractVersion: String = DECOMPOSITION_MANIFEST_CONTRACT_VERSION,
  val issueKey: String,
  val featureName: String,
  val parentSpecPath: String,
  val executionModel: DecompositionExecutionModel = DecompositionExecutionModel.SAME_BRANCH_COMMIT_PER_SUBTASK,
  val baseBranch: String,
  val featureBranch: String?,
  val stackBranches: List<DecompositionStackBranch> = emptyList(),
  val currentSubtaskIntent: CurrentSubtaskIntent,
  val subtasks: List<DecompositionSubtask>,
)
