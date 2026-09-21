package skillbill.ports.experiment.descriptor.model

import skillbill.experiment.model.ExperimentExecutionMode

data class ExperimentDescriptorRecord(
  val name: String,
  val descriptorVersion: String,
  val executionMode: ExperimentExecutionMode,
  val requiredLauncherCapabilities: Set<String>,
  val treatmentCapability: String,
  val setupRequirements: List<String> = emptyList(),
  val measurementRequirements: List<String> = emptyList(),
)
