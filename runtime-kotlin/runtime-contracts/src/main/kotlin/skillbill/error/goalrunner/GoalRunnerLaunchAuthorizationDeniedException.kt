package skillbill.error.goalrunner

import skillbill.error.core.SkillBillRuntimeException
import skillbill.error.core.error
class GoalRunnerLaunchAuthorizationDeniedException(
  val pauseReason: String?,
) : SkillBillRuntimeException("Goal runner launch authorization was denied by a durable pause boundary.")
