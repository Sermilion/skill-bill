package skillbill.ports.experiment.selection.model

data class ExperimentLaunchSelection(
  val normalizedNames: List<String>,
  val descriptors: List<String>,
  val availabilitySummary: String,
  val treatmentCapabilities: Set<String> = emptySet(),
)
