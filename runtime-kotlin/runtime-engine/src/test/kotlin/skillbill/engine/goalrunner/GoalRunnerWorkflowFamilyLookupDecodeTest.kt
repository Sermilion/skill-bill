package skillbill.engine.goalrunner

import org.junit.jupiter.api.Test
import skillbill.error.InvalidAgentAddonSelectionError
import kotlin.test.assertFailsWith

class GoalRunnerWorkflowFamilyLookupDecodeTest {
  @Test
  fun `malformed agent addon selection raises typed error`() {
    assertFailsWith<InvalidAgentAddonSelectionError> {
      decodeGoalAgentAddonSelection("not-a-list")
    }
    assertFailsWith<InvalidAgentAddonSelectionError> {
      decodeGoalAgentAddonSelection(listOf(mapOf("slug" to "only-slug")))
    }
  }
}
