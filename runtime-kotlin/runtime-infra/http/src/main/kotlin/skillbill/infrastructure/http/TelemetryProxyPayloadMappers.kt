package skillbill.infrastructure.http

import skillbill.contracts.JsonCodec
import skillbill.contracts.telemetry.TelemetryProxyPayloadKeys
import skillbill.ports.telemetry.model.TelemetryOutboxRecord
import skillbill.telemetry.model.TelemetrySettings

fun telemetryProxyBatchPayload(
  settings: TelemetrySettings,
  rows: List<TelemetryOutboxRecord>,
): TelemetryProxyBatchPayload =
  TelemetryProxyBatchPayload(
    batch =
      rows.map { row ->
        TelemetryProxyBatchEvent(
          event = row.eventName,
          distinctId = settings.installId,
          properties = telemetryProperties(row, settings.installId),
          timestamp = row.createdAt,
          eventIdentity = row.eventUuid.ifBlank { null },
        )
      },
  )

private fun telemetryProperties(
  row: TelemetryOutboxRecord,
  installId: String,
): MutableMap<String, Any?> =
  (
    JsonCodec.parseObjectOrNull(row.payloadJson)?.let {
      JsonCodec.anyToStringAnyMap(JsonCodec.jsonElementToValue(it))
    } ?: emptyMap()
  ).toMutableMap().apply {
    this[TelemetryProxyPayloadKeys.INSTALL_ID] = installId
    this[TelemetryProxyPayloadKeys.PROCESS_PERSON_PROFILE] = false
    if (!row.skillBillVersion.isNullOrBlank()) {
      this[TelemetryProxyPayloadKeys.SKILL_BILL_VERSION] = row.skillBillVersion
    }
    if (row.eventUuid.isNotBlank()) {
      this[TelemetryProxyPayloadKeys.EVENT_DEDUPLICATION_ID] = row.eventUuid
    }
  }
