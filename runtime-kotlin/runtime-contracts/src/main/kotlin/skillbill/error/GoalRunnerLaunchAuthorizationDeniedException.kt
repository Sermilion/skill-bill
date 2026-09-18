package skillbill.error

class GoalRunnerLaunchAuthorizationDeniedException(
  val pauseReason: String?,
) : SkillBillRuntimeException("Goal runner launch authorization was denied by a durable pause boundary.")
