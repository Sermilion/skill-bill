package skillbill.engine.experiment.codegraph

import skillbill.contracts.experiment.ExperimentObservationPayloadKeys
import skillbill.contracts.telemetry.TelemetryMeasurementAvailability
import skillbill.experiment.model.ExperimentArmId
import skillbill.infrastructure.host.experiment.codegraph.InMemoryCodeGraphUsageLedger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class CodeGraphExperimentArmMeasurementTest {
  @Test
  fun `treatment measurement preserves setup phases query use and evidence bytes`() {
    val ledger = InMemoryCodeGraphUsageLedger()
    ledger.recordSetup("pair-1", 11)
    ledger.recordIndex("pair-1", 22)
    ledger.recordSync("pair-1", 33)
    ledger.recordQuery("pair-1", 44)

    val measurement = CodeGraphExperimentArmMeasurement(ledger).measure(
      pairId = "pair-1",
      armId = ExperimentArmId.TREATMENT.wireValue,
      workflowId = "workflow-1",
    )

    assertEquals(66.0, measurement.setupCost.quantity)
    assertEquals(1.0, measurement.usage.quantity)
    assertEquals(44.0, measurement.cost.quantity)
    assertEquals(
      setOf(
        ExperimentObservationPayloadKeys.CODEGRAPH_INSTALL_DURATION_METRIC_ID,
        ExperimentObservationPayloadKeys.CODEGRAPH_INDEX_DURATION_METRIC_ID,
        ExperimentObservationPayloadKeys.CODEGRAPH_SYNC_DURATION_METRIC_ID,
        ExperimentObservationPayloadKeys.CODEGRAPH_QUERY_COUNT_METRIC_ID,
        ExperimentObservationPayloadKeys.CODEGRAPH_EVIDENCE_BYTES_METRIC_ID,
      ),
      measurement.additionalMeasurements.keys,
    )
  }

  @Test
  fun `degraded treatment keeps measured setup but makes savings cost unavailable`() {
    val ledger = InMemoryCodeGraphUsageLedger()
    ledger.recordSetup("pair-1", 11)
    ledger.recordIndex("pair-1", 22)
    ledger.markDegraded("pair-1", "query timed out")

    val measurement = CodeGraphExperimentArmMeasurement(ledger).measure(
      pairId = "pair-1",
      armId = ExperimentArmId.TREATMENT.wireValue,
      workflowId = "workflow-1",
    )

    assertEquals(33.0, measurement.setupCost.quantity)
    assertNotEquals(TelemetryMeasurementAvailability.MEASURED.wireValue, measurement.cost.availability)
  }
}
