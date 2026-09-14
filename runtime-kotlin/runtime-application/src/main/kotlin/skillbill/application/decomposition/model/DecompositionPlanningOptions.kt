package skillbill.application.decomposition.model

import skillbill.contracts.decomposition.DecompositionPlanningStackBranchWire

data class DecompositionPlanningSubtaskOptions(
  val dependsOn: List<Int> = emptyList(),
  val linearIssueId: String? = null,
  val scope: String? = null,
)

data class DecompositionPlanningResultOptions(
  val recommendedFirstSubtaskId: Int? = null,
  val executionModelWire: String? = null,
  val stackBranches: List<DecompositionPlanningStackBranchWire> = emptyList(),
  val baseBranch: String? = null,
  val specSourceWire: String? = null,
)
