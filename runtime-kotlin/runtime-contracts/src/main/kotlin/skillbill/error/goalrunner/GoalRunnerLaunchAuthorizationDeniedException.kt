package skillbill.error.goalrunner

import skillbill.error.core.SkillBillRuntimeException

class GoalRunnerLaunchAuthorizationDeniedException(
  val pauseReason: String?,
) : SkillBillRuntimeException("Goal runner launch authorization was denied by a durable pause boundary.")
