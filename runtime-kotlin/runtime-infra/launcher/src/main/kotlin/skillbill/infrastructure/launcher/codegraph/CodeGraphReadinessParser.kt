package skillbill.infrastructure.launcher.codegraph

import skillbill.contracts.codegraph.CodeGraphDegradationReason

internal sealed interface CodeGraphReadiness {
  data object Ready : CodeGraphReadiness

  data class Degraded(
    val reason: CodeGraphDegradationReason,
    val detail: String,
  ) : CodeGraphReadiness
}

internal object CodeGraphReadinessParser {
  fun pending(output: String): Boolean {
    val text = output.lowercase()
    return text.contains("pending sync") || text.contains("pending synchronization") ||
      text.contains("still-pending") || text.contains("not yet synced") || text.contains("index is stale") ||
      text.contains("pending index") || text.contains("indexing in progress") ||
      text.contains("entries may be stale") || text.contains("auto-sync disabled") ||
      text.contains("auto-sync is disabled") || text.contains("index is frozen")
  }

  fun unsupported(output: String): Boolean = output.lowercase().let {
    it.contains("unknown command") || it.contains("unrecognized") || it.contains("unknown option")
  }

  fun parse(exitCode: Int?, stdout: String, stderr: String, graphPresent: Boolean): CodeGraphReadiness {
    val output = "$stdout\n$stderr".trim()
    val normalized = output.lowercase()
    if (pending(output)) {
      return CodeGraphReadiness.Degraded(
        CodeGraphDegradationReason.PENDING_SYNCHRONIZATION,
        "CodeGraph reported pending synchronization.",
      )
    }
    val missingGraph = !graphPresent || normalized.contains("not initialized") || normalized.contains("no index")
    if (exitCode == 0 && !missingGraph && Regex("(?im)^\\s*Nodes:\\s*\\d+").containsMatchIn(output)) {
      return CodeGraphReadiness.Ready
    }
    if (missingGraph) {
      return CodeGraphReadiness.Degraded(
        CodeGraphDegradationReason.MISSING_GRAPH,
        "CodeGraph has no prepared repository graph.",
      )
    }
    val reason = if (unsupported(output)) {
      CodeGraphDegradationReason.UNSUPPORTED_COMMAND
    } else {
      CodeGraphDegradationReason.UNAVAILABLE_CAPABILITY
    }
    return CodeGraphReadiness.Degraded(reason, "CodeGraph status did not report a ready graph.")
  }
}
