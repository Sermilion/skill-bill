package skillbill.engine.diagnostics

import skillbill.ports.diagnostics.RuntimeDiagnostics

internal object RuntimeDiagnosticsBestEffortWarning {
  fun record(diagnostics: RuntimeDiagnostics, message: String, cause: Throwable? = null) {
    runCatching {
      if (cause == null) {
        diagnostics.warning(message)
      } else {
        diagnostics.warning(message, cause)
      }
    }
  }
}
