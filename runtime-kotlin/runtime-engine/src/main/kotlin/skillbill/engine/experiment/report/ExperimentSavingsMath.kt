package skillbill.engine.experiment.report
import skillbill.contracts.telemetry.TelemetryMeasurementAvailability

private const val PERCENT_MULTIPLIER = 100.0

data class QuantityWithAvailability(
  val availability: TelemetryMeasurementAvailability,
  val quantity: Double? = null,
  val reason: String? = null,
)

data class MetricSavings(
  val absolute: QuantityWithAvailability,
  val percent: QuantityWithAvailability,
)

object ExperimentSavingsMath {
  fun lowerIsBetterSavings(control: QuantityWithAvailability, treatment: QuantityWithAvailability): MetricSavings {
    val controlQuantity = control.quantity
    val treatmentQuantity = treatment.quantity
    val absolute = when {
      control.availability != TelemetryMeasurementAvailability.MEASURED ||
        treatment.availability != TelemetryMeasurementAvailability.MEASURED ->
        QuantityWithAvailability(
          TelemetryMeasurementAvailability.UNAVAILABLE_INCOMPLETE,
          reason = control.reason ?: treatment.reason ?: "measurement unavailable",
        )
      controlQuantity == null || treatmentQuantity == null ->
        QuantityWithAvailability(
          TelemetryMeasurementAvailability.UNAVAILABLE_INCOMPLETE,
          reason = "quantity unavailable",
        )
      else -> QuantityWithAvailability(
        TelemetryMeasurementAvailability.MEASURED,
        controlQuantity - treatmentQuantity,
      )
    }
    val percent = when {
      absolute.availability != TelemetryMeasurementAvailability.MEASURED ->
        QuantityWithAvailability(
          TelemetryMeasurementAvailability.UNAVAILABLE_INCOMPLETE,
          reason = absolute.reason,
        )
      controlQuantity == null || treatmentQuantity == null ->
        QuantityWithAvailability(
          TelemetryMeasurementAvailability.UNAVAILABLE_INCOMPLETE,
          reason = "quantity unavailable",
        )
      controlQuantity == 0.0 ->
        QuantityWithAvailability(
          TelemetryMeasurementAvailability.UNAVAILABLE_INCOMPLETE,
          reason = "control is zero",
        )
      else -> QuantityWithAvailability(
        TelemetryMeasurementAvailability.MEASURED,
        PERCENT_MULTIPLIER * (controlQuantity - treatmentQuantity) / controlQuantity,
      )
    }
    return MetricSavings(absolute, percent)
  }

  fun lowerIsBetterSetupInclusiveSavings(
    controlExecution: QuantityWithAvailability,
    treatmentExecution: QuantityWithAvailability,
    controlSetup: QuantityWithAvailability,
    treatmentSetup: QuantityWithAvailability,
  ): MetricSavings = lowerIsBetterSavings(
    combine(controlExecution, controlSetup),
    combine(treatmentExecution, treatmentSetup),
  )

  private fun combine(execution: QuantityWithAvailability, setup: QuantityWithAvailability): QuantityWithAvailability {
    if (
      execution.availability != TelemetryMeasurementAvailability.MEASURED ||
      setup.availability != TelemetryMeasurementAvailability.MEASURED
    ) {
      return QuantityWithAvailability(
        availability = execution.availability.takeUnless {
          it == TelemetryMeasurementAvailability.MEASURED
        } ?: setup.availability,
        reason = execution.reason ?: setup.reason ?: "setup or execution measurement unavailable",
      )
    }
    if (execution.quantity == null || setup.quantity == null) {
      return QuantityWithAvailability(
        availability = TelemetryMeasurementAvailability.UNAVAILABLE_INCOMPLETE,
        reason = "setup or execution quantity unavailable",
      )
    }
    return QuantityWithAvailability(
      availability = TelemetryMeasurementAvailability.MEASURED,
      quantity = execution.quantity + setup.quantity,
    )
  }
}
