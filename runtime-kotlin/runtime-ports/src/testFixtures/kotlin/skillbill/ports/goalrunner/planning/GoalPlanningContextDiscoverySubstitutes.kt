package skillbill.ports.goalrunner.planning

import skillbill.ports.goalrunner.planning.model.GoalPlanningContext
import skillbill.ports.goalrunner.verification.model.GoalVerificationBoundaryDiscovery
import java.nio.file.Path

private val EMPTY_PLANNING_CONTEXT =
  GoalPlanningContext(boundaryCatalog = emptyList(), boundaryCatalogTruncated = false, validationGuidance = "")

private val EMPTY_BOUNDARY_DISCOVERY =
  GoalVerificationBoundaryDiscovery(
    boundaryCatalog = emptyList(),
    boundaryCatalogTruncated = false,
    boundaryContextUnavailable = true,
  )

val EMPTY_GOAL_PLANNING_CONTEXT_DISCOVERY: GoalPlanningContextDiscovery =
  object : GoalPlanningContextDiscovery {
    override fun loadPlanningContext(repoRoot: Path): GoalPlanningContext = EMPTY_PLANNING_CONTEXT

    override fun discoverForFindingPaths(
      repoRoot: Path,
      findingPaths: List<String>,
      loudFailOnCapExceeded: Boolean,
    ): GoalVerificationBoundaryDiscovery = EMPTY_BOUNDARY_DISCOVERY
  }
