package skillbill.application

import skillbill.application.decomposition.decompositionPlanningResult
import skillbill.application.decomposition.decompositionPlanningSubtask
import skillbill.contracts.decomposition.DecompositionPlanningResult
import skillbill.contracts.decomposition.DecompositionPlanningStackBranchWire
import java.nio.file.Path

fun decompositionPlanningPlan(parentSpecPath: Path): DecompositionPlanningResult = decompositionPlanningResult(
  parentSpecPath = parentSpecPath.toString(),
  recommendedFirstSubtaskId = 1,
  subtasks = listOf(
    decompositionPlanningSubtask(
      id = 1,
      name = "Foundation",
      specPath = parentSpecPath.parent.resolve("spec_subtask_1_foundation.md").toString(),
      scope = "Create contract foundation",
    ),
    decompositionPlanningSubtask(
      id = 2,
      name = "Runtime",
      specPath = parentSpecPath.parent.resolve("spec_subtask_2_runtime.md").toString(),
      dependsOn = listOf(1),
      scope = "Wire runtime writer",
    ),
  ),
)

fun stackedDecompositionPlanningPlan(
  parentSpecPath: Path = Path.of(".feature-specs/SKILL-51-decomposition/spec.md"),
): DecompositionPlanningResult = decompositionPlanningResult(
  parentSpecPath = parentSpecPath.toString(),
  recommendedFirstSubtaskId = 1,
  executionModelWire = "stacked_branches",
  stackBranches = listOf(
    DecompositionPlanningStackBranchWire(1, "feature/SKILL-51-01-foundation", "main"),
    DecompositionPlanningStackBranchWire(2, "feature/SKILL-51-02-runtime", "feature/SKILL-51-01-foundation"),
  ),
  subtasks = listOf(
    decompositionPlanningSubtask(
      id = 1,
      name = "Foundation",
      specPath = parentSpecPath.parent.resolve("spec_subtask_1_foundation.md").toString(),
    ),
    decompositionPlanningSubtask(
      id = 2,
      name = "Runtime",
      specPath = parentSpecPath.parent.resolve("spec_subtask_2_runtime.md").toString(),
      dependsOn = listOf(1),
    ),
  ),
)
