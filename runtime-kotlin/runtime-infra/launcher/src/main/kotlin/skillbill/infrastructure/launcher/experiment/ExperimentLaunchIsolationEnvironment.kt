package skillbill.infrastructure.launcher.experiment

import skillbill.contracts.experiment.launcher.ExperimentLauncherCapabilityWire
import java.io.File
import java.nio.file.Path

data class ExperimentLaunchIsolationRequest(
  val treatmentEnabled: Boolean,
  val treatmentCapabilities: Set<String>,
  val treatmentCapabilitiesDenied: Set<String>,
  val requiredLauncherCapabilities: Set<String>,
  val repoRoot: Path,
  val managedToolsBin: Path? = null,
  val graphIndexDirectory: Path? = null,
  val inheritedPath: String? = null,
)

data class ExperimentLaunchIsolationResult(
  val environment: Map<String, String>,
  val inheritEnvironment: Boolean,
  val stripInheritedMcpServers: Boolean,
)

object ExperimentLaunchIsolationEnvironment {
  private const val CODEGRAPH_CAPABILITY: String = "codegraph"

  fun apply(request: ExperimentLaunchIsolationRequest): ExperimentLaunchIsolationResult {
    val capabilities = request.requiredLauncherCapabilities
    val treatmentActive = request.treatmentEnabled && request.treatmentCapabilitiesDenied.isEmpty()
    val graphControl = CODEGRAPH_CAPABILITY in request.treatmentCapabilitiesDenied
    val stripMcp = (
      capabilities.contains(
        ExperimentLauncherCapabilityWire.STRIP_INHERITED_MCP_SERVERS,
      ) || graphControl
      ) &&
      !treatmentActive
    val pathIsolation = capabilities.contains(ExperimentLauncherCapabilityWire.PATH_ISOLATION)
    val managedCodeGraph = request.treatmentCapabilities.contains(CODEGRAPH_CAPABILITY) ||
      request.treatmentCapabilitiesDenied.contains(CODEGRAPH_CAPABILITY) ||
      capabilities.contains(ExperimentLauncherCapabilityWire.DISTINCT_GRAPH_INDEX)
    val overrides = linkedMapOf<String, String>()
    if (managedCodeGraph) {
      overrides["CODEGRAPH_TELEMETRY"] = "0"
      overrides["DO_NOT_TRACK"] = "1"
    }
    if (stripMcp) {
      val isolationRoot = isolationRoot(request)
      overrides["CLAUDE_CONFIG_DIR"] = isolationRoot.resolve("claude").toString()
      overrides["CODEX_HOME"] = isolationRoot.resolve("codex").toString()
    }
    var inherit = !stripMcp
    if (pathIsolation) {
      val filteredPath = filteredPath(
        request.inheritedPath,
        request.managedToolsBin,
        treatmentActive,
      )
      if (filteredPath != null) {
        overrides["PATH"] = filteredPath
        inherit = false
      }
    } else if (treatmentActive && request.managedToolsBin != null) {
      val prefix = request.managedToolsBin.toString()
      val base = request.inheritedPath?.trim()?.takeIf { it.isNotEmpty() } ?: ""
      overrides["PATH"] = if (base.isEmpty()) prefix else "$prefix${File.pathSeparator}$base"
      inherit = false
    }
    return ExperimentLaunchIsolationResult(
      environment = overrides,
      inheritEnvironment = inherit,
      stripInheritedMcpServers = stripMcp,
    )
  }

  private fun isolationRoot(request: ExperimentLaunchIsolationRequest): Path =
    request.graphIndexDirectory?.parent?.parent
      ?.resolve(".skill-bill")
      ?.resolve("experiment-isolation")
      ?: request.repoRoot.resolve(".skill-bill").resolve("experiment-isolation")

  private fun filteredPath(inheritedPath: String?, managedToolsBin: Path?, treatmentEnabled: Boolean): String? {
    val path = inheritedPath?.trim().orEmpty()
    if (path.isEmpty() && (!treatmentEnabled || managedToolsBin == null)) return null
    val toolsPrefix = managedToolsBin?.parent?.parent?.toString()
    val segments = path.split(File.pathSeparator).filter { segment ->
      val normalized = segment.trim()
      if (normalized.isEmpty()) return@filter false
      if (!treatmentEnabled && normalized.contains("codegraph", ignoreCase = true)) return@filter false
      if (!treatmentEnabled && toolsPrefix != null && normalized.startsWith(toolsPrefix)) return@filter false
      true
    }
    val filtered = segments.joinToString(File.pathSeparator)
    return if (treatmentEnabled && managedToolsBin != null) {
      listOf(managedToolsBin.toString(), filtered)
        .filter(String::isNotBlank)
        .joinToString(File.pathSeparator)
    } else {
      filtered
    }
  }
}
