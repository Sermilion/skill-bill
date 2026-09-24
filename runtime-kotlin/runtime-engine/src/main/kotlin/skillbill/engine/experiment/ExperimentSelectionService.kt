package skillbill.engine.experiment

import me.tatarka.inject.annotations.Inject
import skillbill.error.shellcontent.ExperimentDescriptorUnavailableError
import skillbill.error.shellcontent.ExperimentSelectionConflictError
import skillbill.error.shellcontent.InvalidExperimentDescriptorSchemaError
import skillbill.experiment.ExperimentAvailabilityResolver
import skillbill.experiment.ExperimentParameterParser
import skillbill.experiment.model.ExperimentExecutionMode
import skillbill.experiment.model.ResolvedExperimentSelection
import skillbill.experiment.model.validateExperimentName
import skillbill.ports.config.RepoLocalConfigPort
import skillbill.ports.config.model.ReadRepoLocalConfigRequest
import skillbill.ports.experiment.config.MachineExperimentConfigStore
import skillbill.ports.experiment.descriptor.ExperimentDescriptorCatalog
import skillbill.ports.experiment.descriptor.model.ExperimentDescriptorRecord
import skillbill.ports.experiment.selection.ExperimentLaunchSelection
import skillbill.ports.experiment.selection.ExperimentSelectionPort
import java.nio.file.Path

private const val MAX_DESCRIPTOR_VERSION_LENGTH = 32
private const val MAX_DESCRIPTOR_REQUIREMENTS = 16

@Inject
class ExperimentSelectionService(
  private val machineConfig: MachineExperimentConfigStore,
  private val repoLocalConfigPort: RepoLocalConfigPort,
  private val descriptorCatalog: ExperimentDescriptorCatalog?,
) : ExperimentSelectionPort {
  override fun resolveForLaunch(
    repoRoot: Path,
    parameter: String?,
    mode: ExperimentExecutionMode,
    savedSelection: List<String>?,
  ): ExperimentLaunchSelection {
    val parsed = ExperimentParameterParser.parse(parameter)
    val saved =
      savedSelection?.let {
        if (it.isEmpty()) {
          ResolvedExperimentSelection(emptyList(), explicitDisable = true)
        } else {
          ExperimentParameterParser.parse(it.joinToString(","))
        }
      }
    val resolved =
      when {
        saved != null -> saved
        parsed.explicitDisable -> ResolvedExperimentSelection(emptyList(), explicitDisable = true)
        else -> parsed
      }
    validateSavedSelection(parameter, savedSelection, saved)
    val repoConfig = repoLocalConfigPort.readRepoLocalConfig(ReadRepoLocalConfigRequest(repoRoot)).config
    val machinePolicy = machineConfig.readExperimentsAvailability()
    val availability = ExperimentAvailabilityResolver.resolve(machinePolicy, repoConfig.experimentsAvailability)
    val compatibleDescriptors = descriptorCatalog?.listCompatible(mode).orEmpty()
    validateDescriptors(compatibleDescriptors, mode)
    if (resolved.normalizedNames.isEmpty()) {
      return ExperimentLaunchSelection(emptyList(), emptyList(), "no experiments selected")
    }
    val compatible = compatibleDescriptors.map { descriptor -> descriptor.name }.toSet()
    val unavailable =
      resolved.normalizedNames.filter { name ->
        name !in compatible ||
          (savedSelection == null && !ExperimentAvailabilityResolver.isNameAvailable(name, availability))
      }
    if (unavailable.isNotEmpty()) {
      throw ExperimentDescriptorUnavailableError(
        requestedNames = resolved.normalizedNames.toSet(),
        mode = mode.wireValue,
        availableCompatible =
          compatible.filter { name ->
            ExperimentAvailabilityResolver.isNameAvailable(name, availability)
          }.toSet(),
      )
    }
    val descriptors =
      resolved.normalizedNames.map { name ->
        descriptorCatalog?.resolve(name, mode)?.name
          ?: throw ExperimentDescriptorUnavailableError(
            requestedNames = setOf(name),
            mode = mode.wireValue,
            availableCompatible = compatible,
          )
      }
    return ExperimentLaunchSelection(
      normalizedNames = resolved.normalizedNames,
      descriptors = descriptors,
      availabilitySummary = availability.policy.toString(),
      treatmentCapabilities =
        resolved.normalizedNames
          .mapNotNull { name -> compatibleDescriptors.firstOrNull { it.name == name }?.treatmentCapability }
          .toSet(),
    )
  }

  private fun validateDescriptors(
    descriptors: List<ExperimentDescriptorRecord>,
    requestedMode: ExperimentExecutionMode,
  ) {
    val names = mutableSetOf<String>()
    descriptors.forEach { descriptor ->
      val reason = descriptorValidationReason(descriptor, requestedMode, names)
      reason?.let { detail ->
        throw InvalidExperimentDescriptorSchemaError(
          sourceLabel = "descriptor:${descriptor.name}",
          reason = detail,
        )
      }
    }
  }

  private fun validateSavedSelection(
    parameter: String?,
    savedSelection: List<String>?,
    saved: ResolvedExperimentSelection?,
  ) {
    if (savedSelection == null || parameter == null) return
    val requested = ExperimentParameterParser.parse(parameter)
    val requestedNames = if (requested.explicitDisable) emptyList() else requested.normalizedNames
    if (requestedNames.toSet() != saved?.normalizedNames.orEmpty().toSet()) {
      throw ExperimentSelectionConflictError(
        "Requested experiments $requestedNames do not match saved selection ${saved?.normalizedNames.orEmpty()}.",
      )
    }
  }

  private fun descriptorValidationReason(
    descriptor: ExperimentDescriptorRecord,
    requestedMode: ExperimentExecutionMode,
    names: MutableSet<String>,
  ): String? =
    when {
      validateExperimentName(descriptor.name) != descriptor.name ->
        "name must be a unique kebab-case experiment name"
      descriptor.executionMode != requestedMode ->
        "execution mode does not match the requested mode"
      descriptor.descriptorVersion.isBlank() ||
        descriptor.descriptorVersion.length > MAX_DESCRIPTOR_VERSION_LENGTH ->
        "descriptor version must be non-blank and at most 32 characters"
      descriptor.requiredLauncherCapabilities.any { it.isBlank() } ->
        "launcher capabilities must be non-blank"
      descriptor.treatmentCapability.isBlank() ->
        "treatment capability must be non-blank"
      descriptor.setupRequirements.any { it.isBlank() } ->
        "setup requirements must be non-blank"
      descriptor.measurementRequirements.any { it.isBlank() } ->
        "measurement requirements must be non-blank"
      descriptor.setupRequirements.size > MAX_DESCRIPTOR_REQUIREMENTS ||
        descriptor.measurementRequirements.size > MAX_DESCRIPTOR_REQUIREMENTS ->
        "descriptor requirements exceed the supported limit"
      !names.add(descriptor.name) -> "descriptor names must be unique"
      else -> null
    }
}
