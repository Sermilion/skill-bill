package skillbill.scaffold.policy.scaffold.model

data class PlatformPackPreset(
  val displayName: String,
  val strongSignals: List<String>,
  val tieBreakers: List<String>,
)

data class OptionalSubagents(
  val specialists: List<String>,
  val suppressed: Boolean,
)

data class PlatformPackSelection(
  val selectedAreas: List<String>,
)

data class PlatformPackDefaults(
  val displayName: String,
  val strongSignals: List<String>,
  val tieBreakers: List<String>,
  val presetUsed: Boolean,
)
