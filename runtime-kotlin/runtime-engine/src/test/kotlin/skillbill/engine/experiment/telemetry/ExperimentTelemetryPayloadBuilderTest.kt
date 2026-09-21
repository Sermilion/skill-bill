package skillbill.engine.experiment.telemetry

import skillbill.contracts.experiment.ExperimentTelemetryPayloadKeys
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ExperimentTelemetryPayloadBuilderTest {
  @Test
  fun `consent gates local payload and redacts sensitive nested values`() {
    assertNull(
      ExperimentTelemetryPayloadBuilder.build(
        ExperimentTelemetryConsent.OFF,
        "pair",
        "goal",
        emptyMap(),
      ),
    )
    val payload = requireNotNull(
      ExperimentTelemetryPayloadBuilder.build(
        ExperimentTelemetryConsent.ANONYMOUS,
        "pair",
        "goal",
        mapOf(
          "cost" to 10,
          "source_path" to "/private/src",
          "details" to mapOf("query" to "secret query", "count" to 2),
        ),
      ),
    )

    assertEquals("goal", payload[ExperimentTelemetryPayloadKeys.COHORT])
    assertTrue(payload[ExperimentTelemetryPayloadKeys.PAIR_ID] != "pair")
    val metrics = payload[ExperimentTelemetryPayloadKeys.METRICS] as Map<*, *>
    assertEquals(10, metrics["cost"])
    assertEquals(null, metrics["source_path"])
    assertEquals(null, (metrics["details"] as Map<*, *>)["query"])
  }
}
