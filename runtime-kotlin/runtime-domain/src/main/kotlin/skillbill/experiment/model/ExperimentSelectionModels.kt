package skillbill.experiment.model

enum class ExperimentExecutionMode {
  GOAL_PAIR,
  NAVIGATION,
  ;

  val wireValue: String
    get() = when (this) {
      GOAL_PAIR -> "goal_pair"
      NAVIGATION -> "navigation"
    }

  companion object {
    fun fromWire(value: String): ExperimentExecutionMode? =
      entries.firstOrNull { mode -> mode.wireValue == value.trim().lowercase() }
  }
}

enum class ExperimentArmId {
  CONTROL,
  TREATMENT,
  ;

  val wireValue: String
    get() = when (this) {
      CONTROL -> "control"
      TREATMENT -> "treatment"
    }

  companion object {
    fun fromWire(value: String): ExperimentArmId? =
      entries.firstOrNull { arm -> arm.wireValue == value.trim().lowercase() }
  }
}

data class ResolvedExperimentSelection(
  val normalizedNames: List<String>,
  val explicitDisable: Boolean,
)
