package skillbill.engine.goalrunner.status

import skillbill.engine.diagnostics.RuntimeDiagnosticsBestEffortWarning
import skillbill.ports.diagnostics.RuntimeDiagnostics
import kotlin.coroutines.cancellation.CancellationException

internal class GoalRunnerStatusDurableReadTracker(
  private val diagnostics: RuntimeDiagnostics,
) {
  var degraded: Boolean = false
    private set

  fun recordDegradedRead(
    seam: String,
    expected: String,
    used: String,
    error: Throwable? = null,
  ) {
    error?.let(::rethrowControlSignal)
    degraded = true
    RuntimeDiagnosticsBestEffortWarning.record(
      diagnostics,
      "seam=$seam value_expected=$expected value_used=$used",
      error,
    )
  }

  private fun rethrowControlSignal(error: Throwable): Nothing? =
    when (error) {
      is CancellationException -> throw error
      is InterruptedException -> {
        Thread.currentThread().interrupt()
        throw error
      }
      else -> null
    }
}
