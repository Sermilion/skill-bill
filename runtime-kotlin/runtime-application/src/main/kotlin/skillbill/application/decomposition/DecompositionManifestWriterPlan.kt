package skillbill.application.decomposition

import skillbill.contracts.decomposition.DecompositionPlanningDependencyWire
import skillbill.contracts.decomposition.DecompositionPlanningResult
import skillbill.contracts.decomposition.DecompositionPlanningStackBranchWire
import skillbill.contracts.decomposition.DecompositionPlanningSubtaskWire
import skillbill.workflow.decomposition.model.DecompositionDependency
import skillbill.workflow.decomposition.model.DecompositionExecutionModel
import skillbill.workflow.decomposition.model.DecompositionStackBranch
import skillbill.workflow.decomposition.model.DecompositionSubtask
import skillbill.workflow.decomposition.model.SpecSource
import skillbill.workflow.decomposition.runtime.invalidManifest
import java.nio.file.Path

fun parseSubtasks(
  planningResult: DecompositionPlanningResult,
  sourceLabel: String,
  specSource: SpecSource = specSource(planningResult, sourceLabel),
): List<DecompositionSubtask> {
  if (planningResult.subtasks.isEmpty()) {
    invalidManifest(sourceLabel, "decomposition planning result must contain at least one subtask.")
  }
  return planningResult.subtasks.mapIndexed { index, item ->
    val name = item.name.takeIf(String::isNotBlank)
      ?: invalidManifest(sourceLabel, "subtasks[$index].name must be nonblank.")
    val specPath = item.specPath.takeIf(String::isNotBlank)
      ?: invalidManifest(sourceLabel, "subtasks[$index].spec_path must be nonblank.")
    DecompositionSubtask(
      id = item.id,
      name = name,
      specPath = specPath,
      status = "pending",
      linearIssueId = linearIssueId(item, index, sourceLabel, specSource),
      dependencies = item.dependencies.map { dependency ->
        DecompositionDependency(
          subtaskId = dependency.subtaskId,
          optional = dependency.optional,
          skipped = dependency.skipped,
        )
      },
    )
  }
}

fun specSource(plan: DecompositionPlanningResult, sourceLabel: String = "<planning-result>"): SpecSource {
  val value = plan.specSourceWire ?: return SpecSource.LOCAL
  if (value.isBlank()) {
    invalidManifest(sourceLabel, "spec_source must be nonblank when present.")
  }
  return SpecSource.fromWireValue(value)
    ?: invalidManifest(sourceLabel, "spec_source '$value' is not supported.")
}

private fun linearIssueId(
  item: DecompositionPlanningSubtaskWire,
  index: Int,
  sourceLabel: String,
  specSource: SpecSource,
): String? {
  val value = item.linearIssueId
  if (value != null && value.isBlank()) {
    invalidManifest(sourceLabel, "subtasks[$index].linear_issue_id must be nonblank when present.")
  }
  if (specSource == SpecSource.LINEAR && value == null) {
    invalidManifest(sourceLabel, "subtasks[$index].linear_issue_id is required for linear spec_source.")
  }
  return value
}

fun parentSpecPath(plan: DecompositionPlanningResult): String {
  plan.parentSpecPath?.takeIf(String::isNotBlank)?.let { return it }
  val firstSubtask = plan.subtasks.firstOrNull()
    ?: invalidManifest("<planning-result>", "decomposition planning result must contain subtasks.")
  if (firstSubtask.specPath.isBlank()) {
    invalidManifest("<planning-result>", "subtasks[0].spec_path must be nonblank.")
  }
  return Path.of(firstSubtask.specPath).parent.resolve("spec.md").toString()
}

fun executionModel(plan: DecompositionPlanningResult): DecompositionExecutionModel {
  val raw = when (val value = plan.executionModelWire) {
    null -> DecompositionExecutionModel.SAME_BRANCH_COMMIT_PER_SUBTASK.wireValue
    else -> value.takeIf(String::isNotBlank)
      ?: invalidManifest("<planning-result>", "execution_model must be nonblank when present.")
  }
  return DecompositionExecutionModel.fromWireValue(raw)
    ?: invalidManifest("<planning-result>", "execution_model '$raw' is not supported.")
}

fun baseBranch(plan: DecompositionPlanningResult, sourceLabel: String): String {
  val value = plan.baseBranch ?: return "main"
  return value.takeIf(String::isNotBlank)
    ?: invalidManifest(sourceLabel, "base_branch must be a nonblank string when present.")
}

fun parseStackBranches(plan: DecompositionPlanningResult): List<DecompositionStackBranch> =
  plan.stackBranches.map { branch ->
    DecompositionStackBranch(
      subtaskId = branch.subtaskId,
      branch = branch.branch.takeIf(String::isNotBlank)
        ?: invalidManifest("<planning-result>", "stack_branches.branch must be nonblank."),
      baseBranch = branch.baseBranch.takeIf(String::isNotBlank)
        ?: invalidManifest("<planning-result>", "stack_branches.base_branch must be nonblank."),
    )
  }

fun DecompositionPlanningResult.currentSubtaskIdOrNull(sourceLabel: String): Int? =
  currentSubtaskId ?: recommendedFirstSubtaskId

fun DecompositionPlanningResult.withSubtasks(subtasks: List<DecompositionPlanningSubtaskWire>): DecompositionPlanningResult =
  copy(subtasks = subtasks)

fun decompositionPlanningSubtask(
  id: Int,
  name: String,
  specPath: String,
  dependsOn: List<Int> = emptyList(),
  linearIssueId: String? = null,
  scope: String? = null,
): DecompositionPlanningSubtaskWire = DecompositionPlanningSubtaskWire(
  id = id,
  name = name,
  specPath = specPath,
  linearIssueId = linearIssueId,
  scope = scope,
  dependencies = dependsOn.map { DecompositionPlanningDependencyWire(subtaskId = it) },
)

fun decompositionPlanningResult(
  parentSpecPath: String,
  subtasks: List<DecompositionPlanningSubtaskWire>,
  recommendedFirstSubtaskId: Int? = subtasks.firstOrNull()?.id,
  executionModelWire: String? = null,
  stackBranches: List<DecompositionPlanningStackBranchWire> = emptyList(),
  baseBranch: String? = null,
  specSourceWire: String? = null,
): DecompositionPlanningResult = DecompositionPlanningResult(
  mode = "decompose",
  parentSpecPath = parentSpecPath,
  specSourceWire = specSourceWire,
  executionModelWire = executionModelWire,
  baseBranch = baseBranch,
  recommendedFirstSubtaskId = recommendedFirstSubtaskId,
  stackBranches = stackBranches,
  subtasks = subtasks,
)
