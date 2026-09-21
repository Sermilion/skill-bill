package skillbill.ports.experiment.isolation.model

import skillbill.experiment.model.ExperimentArmId

data class ExperimentArmIsolationContext(
  val pairId: String,
  val armId: ExperimentArmId,
  val checkpointNamespacePrefix: String,
  val treatmentEnabled: Boolean,
  val statePaths: ExperimentArmStatePaths = ExperimentArmStatePaths(),
)
