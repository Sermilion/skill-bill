package skillbill.experiment
import skillbill.config.model.EffectiveExperimentAvailability
import skillbill.config.model.ExperimentAvailabilityPolicy

object ExperimentAvailabilityResolver {
  fun resolve(
    machinePolicy: ExperimentAvailabilityPolicy?,
    repoPolicy: ExperimentAvailabilityPolicy?,
  ): EffectiveExperimentAvailability {
    val machineDeclared = machinePolicy != null && machinePolicy !is ExperimentAvailabilityPolicy.InheritMachine
    val repoDeclared = repoPolicy != null && repoPolicy !is ExperimentAvailabilityPolicy.InheritMachine
    val effective = when {
      repoPolicy is ExperimentAvailabilityPolicy.ExplicitNames ||
        repoPolicy is ExperimentAvailabilityPolicy.Disabled -> repoPolicy
      machinePolicy is ExperimentAvailabilityPolicy.ExplicitNames ||
        machinePolicy is ExperimentAvailabilityPolicy.Disabled -> machinePolicy
      else -> ExperimentAvailabilityPolicy.InheritMachine
    }
    return EffectiveExperimentAvailability(
      policy = effective,
      machineDeclared = machineDeclared,
      repoDeclared = repoDeclared,
    )
  }

  fun isNameAvailable(name: String, availability: EffectiveExperimentAvailability): Boolean =
    when (val policy = availability.policy) {
      ExperimentAvailabilityPolicy.InheritMachine -> true
      ExperimentAvailabilityPolicy.Disabled -> false
      is ExperimentAvailabilityPolicy.ExplicitNames -> name in policy.names
    }

  fun isExplicitlyListed(name: String, policy: ExperimentAvailabilityPolicy?): Boolean =
    policy is ExperimentAvailabilityPolicy.ExplicitNames && name in policy.names

  fun listedNames(policy: ExperimentAvailabilityPolicy?): List<String> =
    (policy as? ExperimentAvailabilityPolicy.ExplicitNames)?.names.orEmpty()
}
