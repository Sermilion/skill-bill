package skillbill.goalrunner.planning

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GoalPlanningExcludedPathsTest {
  @Test
  fun `the platform-packs root denies its agent memory at every depth`() {
    assertTrue(GoalPlanningExcludedPaths.isExcluded("platform-packs/kmp/agent/history.md"))
    assertTrue(GoalPlanningExcludedPaths.isExcluded("platform-packs"))
    assertTrue(GoalPlanningExcludedPaths.isExcluded("platform-packs\\kmp\\agent\\history.md"))
    assertFalse(GoalPlanningExcludedPaths.isExcluded("platform-packsX/agent/history.md"))
  }

  @Test
  fun `excluded directory names deny at any depth`() {
    assertTrue(GoalPlanningExcludedPaths.isExcluded("build/generated/agent/history.md"))
    assertTrue(GoalPlanningExcludedPaths.isExcluded("runtime-kotlin/runtime-contracts/build/classes/agent"))
    assertTrue(GoalPlanningExcludedPaths.isExcluded("tooling/web/node_modules/pkg/agent/history.md"))
    assertTrue(GoalPlanningExcludedPaths.isExcluded("runtime-kotlin/.gradle/caches"))
    assertTrue(GoalPlanningExcludedPaths.isExcluded("services/api/.venv/lib/agent/history.md"))
    assertFalse(GoalPlanningExcludedPaths.isExcluded("runtime-kotlin/buildSrc/agent/history.md"))
    assertFalse(GoalPlanningExcludedPaths.isExcluded("runtime-kotlin/agent/history.md"))
    assertFalse(GoalPlanningExcludedPaths.isExcluded(""))
  }

  @Test
  fun `interior dot segments cannot dress an excluded root up as an allowed one`() {
    assertTrue(GoalPlanningExcludedPaths.isExcluded("runtime-kotlin/../platform-packs/kmp/agent/history.md"))
    assertTrue(GoalPlanningExcludedPaths.isExcluded("./platform-packs/./kmp/agent/history.md"))
    assertTrue(GoalPlanningExcludedPaths.isExcluded("a/b/../../build/agent/history.md"))
    assertFalse(GoalPlanningExcludedPaths.isExcluded("platform-packs/../runtime-kotlin/agent/history.md"))
  }

  @Test
  fun `a path escaping the repository root is denied rather than allowed`() {
    assertTrue(GoalPlanningExcludedPaths.isExcluded("../outside/agent/history.md"))
    assertTrue(GoalPlanningExcludedPaths.isExcluded("runtime-kotlin/../../outside/agent/history.md"))
  }
}
