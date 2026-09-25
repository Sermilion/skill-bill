package skillbill.engine.goalrunner.planning.context

import skillbill.error.shellcontent.InvalidGoalPlanningPreparationSchemaError
import kotlin.test.Test
import kotlin.test.assertFailsWith

class GoalPlanningSharedContextPacketTypedErrorTest {
  @Test
  fun `malformed shared context catalog raises the planning schema error`() {
    assertFailsWith<InvalidGoalPlanningPreparationSchemaError> {
      GoalPlanningSharedContextPacketValidation.requireValidCatalog("not-an-object")
    }
  }

  @Test
  fun `unsupported shared context version raises the planning schema error`() {
    assertFailsWith<InvalidGoalPlanningPreparationSchemaError> {
      GoalPlanningSharedContextPacket.migrate(
        mapOf(GoalPlanningSharedContextPacketPayloadKeys.PACKET_VERSION to "0.9"),
      )
    }
  }
}
