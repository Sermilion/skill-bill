package skillbill.ports.goalrunner.planning

import skillbill.ports.goalrunner.planning.model.GoalPlanningContext
import skillbill.ports.goalrunner.verification.model.GoalVerificationBoundaryDiscovery
import java.nio.file.Path

interface GoalPlanningContextDiscovery {
  fun loadPlanningContext(repoRoot: Path): GoalPlanningContext

  fun discoverForFindingPaths(
    repoRoot: Path,
    findingPaths: List<String>,
    loudFailOnCapExceeded: Boolean = false,
  ): GoalVerificationBoundaryDiscovery
}
