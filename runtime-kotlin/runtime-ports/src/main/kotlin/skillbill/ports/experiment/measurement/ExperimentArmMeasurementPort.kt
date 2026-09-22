package skillbill.ports.experiment.measurement

import skillbill.ports.experiment.measurement.model.ExperimentArmMeasurement as ExperimentArmMeasurementModel
import skillbill.ports.experiment.measurement.model.ExperimentMeasuredValue as ExperimentMeasuredValueModel

typealias ExperimentArmMeasurement = ExperimentArmMeasurementModel
typealias ExperimentMeasuredValue = ExperimentMeasuredValueModel

fun interface ExperimentArmMeasurementPort {
  fun measure(
    pairId: String,
    armId: String,
    workflowId: String,
  ): ExperimentArmMeasurement
}
