package skillbill.infrastructure.sqlite.review

import skillbill.error.ShellContentContractException

class InvalidGoalTelemetryRowError(
  val rowIdentity: String,
  val reason: String,
  cause: Throwable? = null,
) : ShellContentContractException(
  "Goal telemetry row $rowIdentity is malformed: $reason",
  cause,
)
