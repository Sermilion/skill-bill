package skillbill.ports.experiment.measurement

import skillbill.ports.experiment.measurement.model.ExperimentArmMeasurement

fun interface ExperimentArmMeasurementPort {
  fun measure(
    pairId: String,
    armId: String,
    workflowId: String,
  ): ExperimentArmMeasurement
}
