package skillbill.ports.experiment.measurement.model

data class ExperimentMeasuredValue(
  val quantity: Double? = null,
  val availability: String,
  val reason: String? = null,
)

data class ExperimentArmMeasurement(
  val setupCost: ExperimentMeasuredValue,
  val usage: ExperimentMeasuredValue,
  val cost: ExperimentMeasuredValue,
  val additionalMeasurements: Map<String, ExperimentMeasuredValue> = emptyMap(),
)
