package skillbill.engine.experiment.observation

import skillbill.contracts.JsonCodec
import skillbill.ports.experiment.pair.ExperimentPairOwnerPort
import skillbill.ports.experiment.pair.ExperimentPairPayload
import skillbill.ports.experiment.pair.ExperimentPairPersistedState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ExperimentObservationRecorderTest {
  @Test
  fun `records attempts setup usage and cost without converting unavailable values to zero`() {
    var imported: ExperimentPairPayload? = null
    val recorder =
      ExperimentObservationRecorder(
        object : ExperimentPairOwnerPort {
          override fun load(pairId: String) = null

          override fun save(state: ExperimentPairPersistedState) = Unit

          override fun importObservation(payload: ExperimentPairPayload): Boolean {
            imported = payload
            return true
          }
        },
      )

    assertTrue(
      recorder.record(
        ExperimentObservationRecordRequest(
          pairId = "pair",
          armId = "control",
          workflowId = "workflow",
          phaseId = "implement",
          attempt = 2,
          recordedAt = "2026-09-21T00:00:00Z",
          measurements =
            listOf(
              ExperimentObservationMeasurement("attempt_count", 2.0, "measured"),
              ExperimentObservationMeasurement("setup_cost", 30.0, "measured"),
              ExperimentObservationMeasurement(
                "usage",
                null,
                "unavailable_incomplete",
                "provider did not report usage",
              ),
              ExperimentObservationMeasurement(
                "cost",
                null,
                "unavailable_incomplete",
                "provider did not report cost",
              ),
            ),
        ),
      ),
    )

    val measurements = imported!!.toMap()["measurements"] as List<*>
    assertEquals(4, measurements.size)
    assertEquals(
      "unavailable_incomplete",
      (measurements[2] as Map<*, *>)["availability"],
    )
    assertEquals(null, (measurements[2] as Map<*, *>)["quantity"])
  }
}

private fun ExperimentPairPayload.toMap(): Map<String, Any?> =
  JsonCodec.anyToStringAnyMap(
    JsonCodec.jsonElementToValue(requireNotNull(JsonCodec.parseObjectOrNull(toJson()))),
  ) ?: error("Experiment pair payload must decode to an object.")
