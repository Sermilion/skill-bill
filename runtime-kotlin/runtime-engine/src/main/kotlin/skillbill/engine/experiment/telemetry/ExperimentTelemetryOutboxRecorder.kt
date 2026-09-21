package skillbill.engine.experiment.telemetry

import skillbill.contracts.JsonCodec
import skillbill.contracts.experiment.ExperimentTelemetryPayloadKeys
import skillbill.ports.telemetry.transport.TelemetryConfigStore

fun interface ExperimentTelemetryOutboxSink {
  fun enqueue(eventName: String, payloadJson: String)
}

fun interface ExperimentTelemetryRecorder {
  fun record(pairId: String, cohort: String, metrics: Map<String, Any?>): Boolean
}

class ExperimentTelemetryOutboxRecorder(
  private val configStore: TelemetryConfigStore,
  private val outbox: ExperimentTelemetryOutboxSink,
) : ExperimentTelemetryRecorder {
  override fun record(pairId: String, cohort: String, metrics: Map<String, Any?>): Boolean {
    val payload = ExperimentTelemetryPayloadBuilder.build(
      consent = consent(configStore.read()?.payload?.get(ExperimentTelemetryPayloadKeys.TELEMETRY_LEVEL)),
      pairId = pairId,
      cohort = cohort,
      metrics = metrics,
    ) ?: return false
    outbox.enqueue(
      ExperimentTelemetryPayloadKeys.EXPERIMENT_COMPLETED_EVENT,
      JsonCodec.mapToJsonString(payload),
    )
    return true
  }

  private fun consent(raw: Any?): ExperimentTelemetryConsent = when (raw?.toString()) {
    "anonymous" -> ExperimentTelemetryConsent.ANONYMOUS
    "full" -> ExperimentTelemetryConsent.FULL
    else -> ExperimentTelemetryConsent.OFF
  }
}
