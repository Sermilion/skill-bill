package skillbill.contracts.telemetry

import skillbill.contracts.JsonPayloadContract
import skillbill.contracts.SharedPayloadKeys

private const val STATUS_OK = "ok"
private const val STATUS_SKIPPED = "skipped"
private const val STATUS_ERROR = "error"
private const val ORCHESTRATED_MODE = "orchestrated"
private const val ORCHESTRATED_SKIPPED = "skipped_in_orchestrated_mode"

data class LifecycleOkContract(
  val sessionId: String,
) : JsonPayloadContract {
  override fun toPayload(): Map<String, Any?> =
    linkedMapOf(
      SharedPayloadKeys.STATUS to STATUS_OK,
      LifecycleTelemetryPayloadKeys.SESSION_ID to sessionId,
    )
}

data class LifecycleSkippedContract(
  val sessionId: String,
) : JsonPayloadContract {
  override fun toPayload(): Map<String, Any?> =
    linkedMapOf(
      SharedPayloadKeys.STATUS to STATUS_SKIPPED,
      LifecycleTelemetryPayloadKeys.SESSION_ID to sessionId,
    )
}

data class LifecycleErrorContract(
  val sessionId: String,
  val error: String,
) : JsonPayloadContract {
  override fun toPayload(): Map<String, Any?> =
    linkedMapOf(
      SharedPayloadKeys.STATUS to STATUS_ERROR,
      LifecycleTelemetryPayloadKeys.SESSION_ID to sessionId,
      LifecycleTelemetryPayloadKeys.ERROR to error,
    )
}

data class LifecycleOrchestratedStartedSkippedContract(
  val mode: String = ORCHESTRATED_MODE,
  val status: String = ORCHESTRATED_SKIPPED,
) : JsonPayloadContract {
  override fun toPayload(): Map<String, Any?> =
    linkedMapOf(
      LifecycleTelemetryPayloadKeys.MODE to mode,
      SharedPayloadKeys.STATUS to status,
    )
}

data class LifecycleOrchestratedFinishedContract(
  val telemetryPayload: JsonPayloadContract,
  val mode: String = ORCHESTRATED_MODE,
) : JsonPayloadContract {
  override fun toPayload(): Map<String, Any?> =
    linkedMapOf(
      LifecycleTelemetryPayloadKeys.MODE to mode,
      LifecycleTelemetryPayloadKeys.TELEMETRY_PAYLOAD to telemetryPayload.toPayload(),
    )
}
