package skillbill.ports.telemetry.lifecycle

import skillbill.telemetry.model.PrDescriptionGeneratedRecord

interface PrDescriptionLifecycleTelemetryRepository {
  fun prDescriptionGenerated(
    record: PrDescriptionGeneratedRecord,
    level: String,
  )
}
