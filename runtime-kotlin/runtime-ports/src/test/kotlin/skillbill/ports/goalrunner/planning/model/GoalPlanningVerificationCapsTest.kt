package skillbill.ports.goalrunner.planning.model

import kotlin.test.Test
import kotlin.test.assertEquals

class GoalPlanningVerificationCapsTest {
  @Test
  fun `verification body resolution caps keep the retired packaged caps values`() {
    assertEquals(
      GoalPlanningBoundaryBodyResolutionCaps(
        maxSelectedBodies = 12,
        maxBodyBytes = 4_096,
        maxTotalBodyBytes = 32_768,
      ),
      GoalPlanningBoundaryBodyResolutionCaps.VERIFICATION,
    )
  }

  @Test
  fun `verification discovery caps keep the retired packaged caps values`() {
    assertEquals(16, GoalPlanningContext.VERIFICATION_MAX_DISCOVERY_FILE_COUNT)
    assertEquals(50, GoalPlanningContext.VERIFICATION_MAX_HEADINGS_PER_FILE)
    assertEquals(128, GoalPlanningContext.VERIFICATION_MAX_CATALOG_HEADINGS)
    assertEquals(30, GoalPlanningContext.VERIFICATION_HISTORY_RECENCY_DAYS)
    assertEquals(131_072L, GoalPlanningContext.MAX_BOUNDARY_FILE_BYTES)
  }
}
