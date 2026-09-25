package skillbill.engine.goalrunner.planning.recovery

import skillbill.error.shellcontent.InvalidGoalPlanningPreparationSchemaError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class GoalPlanningPreplanProseReadTest {
  @Test
  fun `a payload that is not a JSON object fails typed at the root`() {
    val error =
      assertFailsWith<InvalidGoalPlanningPreparationSchemaError> {
        preplanProseValueHash("shared-preplan-discarded")
      }

    assertEquals("", error.fieldPath)
  }

  @Test
  fun `produced_outputs that is not an object fails typed at produced_outputs`() {
    val error =
      assertFailsWith<InvalidGoalPlanningPreparationSchemaError> {
        preplanProseValueHash("""{"phase_id":"preplan","produced_outputs":"not-an-object"}""")
      }

    assertEquals("produced_outputs", error.fieldPath)
  }

  @Test
  fun `a missing value fails typed at produced_outputs value`() {
    val error =
      assertFailsWith<InvalidGoalPlanningPreparationSchemaError> {
        preplanProseValueHash("""{"phase_id":"preplan","produced_outputs":{"prompt":"only"}}""")
      }

    assertEquals("produced_outputs.value", error.fieldPath)
  }

  @Test
  fun `an absent prompt still reads as null`() {
    assertEquals(
      null,
      preplanProsePrompt("""{"phase_id":"preplan","produced_outputs":{"value":"prose","prompt":"  "}}"""),
    )
  }
}
