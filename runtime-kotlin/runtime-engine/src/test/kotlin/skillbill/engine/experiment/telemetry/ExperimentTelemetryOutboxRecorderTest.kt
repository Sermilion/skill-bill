package skillbill.engine.experiment.telemetry

import skillbill.contracts.JsonCodec
import skillbill.contracts.experiment.ExperimentTelemetryPayloadKeys
import skillbill.ports.telemetry.transport.TelemetryConfigStore
import skillbill.telemetry.model.TelemetryConfigDocument
import skillbill.telemetry.model.TelemetryOpenDocument
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ExperimentTelemetryOutboxRecorderTest {
  @Test
  fun `consent writes redacted payload to the existing outbox and off writes nothing`() {
    val events = mutableListOf<Pair<String, String>>()
    val recorder = ExperimentTelemetryOutboxRecorder(
      configStore = configStore("anonymous"),
      outbox = ExperimentTelemetryOutboxSink { eventName, payloadJson ->
        events += eventName to payloadJson
      },
    )

    assertTrue(recorder.record("pair-1", "goal", mapOf("cost" to 2, "source_path" to "/private")))

    val payload = JsonCodec.parseObjectOrNull(events.single().second)!!
    assertEquals(ExperimentTelemetryPayloadKeys.EXPERIMENT_COMPLETED_EVENT, events.single().first)
    assertEquals(null, payload[ExperimentTelemetryPayloadKeys.METRICS].toString().takeIf { it.contains("private") })

    val offRecorder = ExperimentTelemetryOutboxRecorder(
      configStore = configStore("off"),
      outbox = ExperimentTelemetryOutboxSink { _, _ -> error("off consent must not enqueue") },
    )
    assertFalse(offRecorder.record("pair-2", "goal", emptyMap()))
  }

  private fun configStore(level: String): TelemetryConfigStore = object : TelemetryConfigStore {
    private val document = TelemetryConfigDocument(
      TelemetryOpenDocument.from(mapOf(ExperimentTelemetryPayloadKeys.TELEMETRY_LEVEL to level)),
    )

    override fun stateDir(): Path = Path.of(".")
    override fun configPath(): Path = Path.of("telemetry.yaml")
    override fun read(): TelemetryConfigDocument = document
    override fun ensure(): TelemetryConfigDocument = document
    override fun write(document: TelemetryConfigDocument) = Unit
  }
}
