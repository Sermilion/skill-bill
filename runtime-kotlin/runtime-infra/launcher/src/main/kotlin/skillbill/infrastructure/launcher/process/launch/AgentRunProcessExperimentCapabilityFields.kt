package skillbill.infrastructure.launcher.process.launch

import skillbill.error.shellcontent.ExperimentIsolationCapabilityRefusalError

data class AgentRunProcessExperimentCapabilityFields(
  val treatmentCapabilitiesEnabled: Set<String> = emptySet(),
  val treatmentCapabilitiesDenied: Set<String> = emptySet(),
  val denyRemotePublication: Boolean = false,
) {
  fun validate(command: List<String>) {
    val commandText = command.joinToString(" ")
    treatmentCapabilitiesDenied.firstOrNull { capability ->
      command.any { argument -> argument == capability || argument.contains(capability) }
    }?.let { capability ->
      throw ExperimentIsolationCapabilityRefusalError(
        "The current arm is not allowed to invoke treatment capability '$capability'.",
      )
    }
    if (denyRemotePublication && commandText.contains("git push")) {
      throw ExperimentIsolationCapabilityRefusalError(
        "Experiment arms cannot publish remotely; parent delivery owns publication.",
      )
    }
  }
}
