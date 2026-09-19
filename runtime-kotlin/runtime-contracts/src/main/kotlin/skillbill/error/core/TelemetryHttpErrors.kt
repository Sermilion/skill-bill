package skillbill.error.core
class InvalidTelemetryTransportOutcomeError(
  val statusCode: Int,
) : SkillBillRuntimeException(
  "Telemetry transport returned $statusCode, which is not a valid HTTP status code.",
)

class TelemetryProxyRequestFailureError(
  val statusCode: Int,
  val seam: String,
  val detail: String,
  cause: Throwable? = null,
) : SkillBillRuntimeException(
  "Telemetry proxy request failed at $seam with HTTP $statusCode: $detail",
  cause,
)

class TelemetryProxyInvalidResponseError(
  val seam: String,
  val detail: String,
) : SkillBillRuntimeException(
  "Telemetry proxy response invalid at $seam: $detail",
)

class TelemetryRelayUrlUnconfiguredError : SkillBillRuntimeException(
  "Telemetry relay URL is not configured.",
)

class UnresolvedRemoteTransportPortError : SkillBillRuntimeException(
  "RemoteTransportPort is unresolved; provide it from the composition root after bootstrap resolution.",
)
