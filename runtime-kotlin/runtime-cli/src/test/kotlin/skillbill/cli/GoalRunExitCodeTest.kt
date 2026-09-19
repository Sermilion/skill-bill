package skillbill.cli

import skillbill.cli.goal.core.goalRunExitCode
import kotlin.test.Test
import kotlin.test.assertEquals
class GoalRunExitCodeTest {
  @Test
  fun `complete exits 0`() {
    assertEquals(0, goalRunExitCode("complete", reason = null))
  }

  @Test
  fun `paused exits 2 not 1`() {
    assertEquals(2, goalRunExitCode("stopped", "paused"))
  }

  @Test
  fun `failed exits 1`() {
    assertEquals(1, goalRunExitCode("stopped", "failed"))
  }

  @Test
  fun `timeout classifies as failed exit 1`() {
    assertEquals(1, goalRunExitCode("stopped", "timeout"))
  }

  @Test
  fun `blocked exits 3`() {
    assertEquals(3, goalRunExitCode("stopped", "blocked"))
  }

  @Test
  fun `policy_blocked exits 3`() {
    assertEquals(3, goalRunExitCode("stopped", "policy_blocked"))
  }
}
