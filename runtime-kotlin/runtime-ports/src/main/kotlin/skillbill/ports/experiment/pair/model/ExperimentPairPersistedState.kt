package skillbill.ports.experiment.pair.model

import skillbill.experiment.model.ExperimentArmId
import skillbill.experiment.model.ExperimentExecutionMode

data class ExperimentPairPersistedState(
  val pairId: String,
  val executionMode: ExperimentExecutionMode,
  val selectedNames: List<String>,
  val armOrder: List<ExperimentArmId>,
  val randomSeed: String,
  val pairPayload: Map<String, Any?>,
)
