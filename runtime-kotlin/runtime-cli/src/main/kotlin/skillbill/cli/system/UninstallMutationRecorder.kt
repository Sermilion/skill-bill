package skillbill.cli.system

import skillbill.ports.diagnostics.RuntimeDiagnostics

internal class UninstallMutationRecorder(private val diagnostics: RuntimeDiagnostics) {
  private val failures = mutableListOf<String>()

  fun recordFailure(description: String, error: Throwable) {
    diagnostics.error("uninstall mutation failed: $description", error)
    failures += "$description: ${error.message.orEmpty()}"
  }

  fun failureMessages(): List<String> = failures.toList()

  fun failed(): Boolean = failures.isNotEmpty()
}
