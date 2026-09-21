package skillbill.config.model

sealed interface ExperimentAvailabilityPolicy {
  data object InheritMachine : ExperimentAvailabilityPolicy

  data class ExplicitNames(val names: List<String>) : ExperimentAvailabilityPolicy

  data object Disabled : ExperimentAvailabilityPolicy
}

data class EffectiveExperimentAvailability(
  val policy: ExperimentAvailabilityPolicy,
  val machineDeclared: Boolean,
  val repoDeclared: Boolean,
)
