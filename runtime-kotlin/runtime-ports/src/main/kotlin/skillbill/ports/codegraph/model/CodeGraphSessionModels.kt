package skillbill.ports.codegraph.model

import skillbill.codegraph.model.CodeGraphLifecycleObservation
import skillbill.codegraph.model.CodeGraphSessionConfiguration
import skillbill.ports.codegraph.CodeGraphSessionLease
import java.nio.file.Path

data class CodeGraphSessionRequest(
  val repositoryRoot: Path,
  val childSessionId: String,
  val agentId: String,
  val launchDirectory: Path = repositoryRoot,
  val configuration: CodeGraphSessionConfiguration = CodeGraphSessionConfiguration(
    repository = repositoryRoot.toAbsolutePath().normalize().toString(),
  ),
) {
  init {
    require(childSessionId.isNotBlank()) { "childSessionId is required." }
    require(agentId.isNotBlank()) { "agentId is required." }
    require(repositoryRoot.isAbsolute) { "repositoryRoot must be absolute." }
    require(configuration.telemetryDisabled) { "CodeGraph session telemetry must be disabled." }
    require(configuration.repository == repositoryRoot.toAbsolutePath().normalize().toString()) {
      "CodeGraph configuration must name the requested repository root."
    }
  }
}

data class CodeGraphMcpLaunchConfiguration(
  val mcpConfigPath: Path,
  val endpoint: String,
  val declaredCapability: String,
) {
  init {
    require(mcpConfigPath.isAbsolute) { "mcpConfigPath must be absolute." }
    require(endpoint.startsWith("http://127.0.0.1:")) { "CodeGraph endpoint must be local." }
    require(declaredCapability.isNotBlank()) { "CodeGraph capability is required." }
  }
}

sealed interface CodeGraphSessionPreparation {
  data class Ready(val lease: CodeGraphSessionLease) : CodeGraphSessionPreparation

  data class Fallback(val observation: CodeGraphLifecycleObservation) : CodeGraphSessionPreparation
}
