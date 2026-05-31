package skillbill.featurespec.model

enum class FeatureSpecPreparationMode(val wireValue: String) {
  SINGLE_SPEC("single_spec"),
  DECOMPOSED("decomposed"),
  ;

  companion object {
    fun fromWireValue(value: String): FeatureSpecPreparationMode? =
      entries.firstOrNull { mode -> mode.wireValue == value }
  }
}

data class FeatureSpecPreparationIntake(
  val issueKey: String,
  val intendedOutcome: String,
  val acceptanceCriteria: List<String>,
  val constraints: List<String>,
  val nonGoals: List<String> = emptyList(),
)

data class FeatureSpecPreparationDecision(
  val issueKey: String,
  val intendedOutcome: String,
  val acceptanceCriteria: List<String>,
  val constraints: List<String>,
  val nonGoals: List<String>,
  val mode: FeatureSpecPreparationMode,
)
