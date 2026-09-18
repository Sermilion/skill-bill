package skillbill.infrastructure.http

import skillbill.contracts.JsonCodec
import skillbill.contracts.telemetry.TelemetryProxyBatchEvent
import skillbill.contracts.telemetry.TelemetryProxyBatchPayload
import skillbill.contracts.telemetry.TelemetryProxyPayloadKeys
import skillbill.ports.telemetry.model.TelemetryOutboxRecord
import skillbill.telemetry.model.TelemetrySettings

internal fun telemetryProxyBatchPayload(
  settings: TelemetrySettings,
  rows: List<TelemetryOutboxRecord>,
): TelemetryProxyBatchPayload = TelemetryProxyBatchPayload(
  batch =
  rows.map { row ->
    TelemetryProxyBatchEvent(
      event = row.eventName,
      distinctId = settings.installId,
      properties = telemetryProperties(row, settings.installId),
      timestamp = row.createdAt,
    )
  },
)

private fun telemetryProperties(row: TelemetryOutboxRecord, installId: String): MutableMap<String, Any?> = (
  JsonCodec.parseObjectOrNull(row.payloadJson)?.let {
    JsonCodec.anyToStringAnyMap(JsonCodec.jsonElementToValue(it))
  } ?: emptyMap()
  ).toMutableMap().apply {
  this["install_id"] = installId
  this["\$process_person_profile"] = false
  if (!row.skillBillVersion.isNullOrBlank()) {
    this["skill_bill_version"] = row.skillBillVersion
  }
  if (row.eventUuid.isNotBlank()) {
    this[TelemetryProxyPayloadKeys.EVENT_DEDUPLICATION_ID] = row.eventUuid
  }
}
