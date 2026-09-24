package skillbill.engine.experiment.report

import skillbill.contracts.telemetry.TelemetryMeasurementAvailability
import kotlin.test.Test
import kotlin.test.assertEquals

class ExperimentSavingsMathTest {
  @Test
  fun `control 100 treatment 80 yields twenty percent savings`() {
    val savings =
      ExperimentSavingsMath.lowerIsBetterSavings(
        QuantityWithAvailability(TelemetryMeasurementAvailability.MEASURED, 100.0),
        QuantityWithAvailability(TelemetryMeasurementAvailability.MEASURED, 80.0),
      )
    assertEquals(20.0, savings.absolute.quantity)
    assertEquals(20.0, savings.percent.quantity)
  }

  @Test
  fun `zero control omits percent`() {
    val savings =
      ExperimentSavingsMath.lowerIsBetterSavings(
        QuantityWithAvailability(TelemetryMeasurementAvailability.MEASURED, 0.0),
        QuantityWithAvailability(TelemetryMeasurementAvailability.MEASURED, 80.0),
      )
    assertEquals(TelemetryMeasurementAvailability.UNAVAILABLE_INCOMPLETE, savings.percent.availability)
  }

  @Test
  fun `available measurement without quantity stays unavailable`() {
    val savings =
      ExperimentSavingsMath.lowerIsBetterSavings(
        QuantityWithAvailability(TelemetryMeasurementAvailability.MEASURED),
        QuantityWithAvailability(TelemetryMeasurementAvailability.MEASURED, 80.0),
      )

    assertEquals(TelemetryMeasurementAvailability.UNAVAILABLE_INCOMPLETE, savings.absolute.availability)
    assertEquals("quantity unavailable", savings.absolute.reason)
  }

  @Test
  fun `setup inclusive savings includes treatment setup and can be negative`() {
    val savings =
      ExperimentSavingsMath.lowerIsBetterSetupInclusiveSavings(
        controlExecution = QuantityWithAvailability(TelemetryMeasurementAvailability.MEASURED, 100.0),
        treatmentExecution = QuantityWithAvailability(TelemetryMeasurementAvailability.MEASURED, 80.0),
        controlSetup = QuantityWithAvailability(TelemetryMeasurementAvailability.MEASURED, 0.0),
        treatmentSetup = QuantityWithAvailability(TelemetryMeasurementAvailability.MEASURED, 30.0),
      )

    assertEquals(-10.0, savings.absolute.quantity)
    assertEquals(-10.0, savings.percent.quantity)
  }
}
