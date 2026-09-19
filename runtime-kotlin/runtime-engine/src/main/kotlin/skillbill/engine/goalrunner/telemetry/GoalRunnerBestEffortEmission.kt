package skillbill.engine.goalrunner.telemetry

import skillbill.engine.diagnostics.RuntimeDiagnosticsBestEffortWarning
import skillbill.ports.diagnostics.RuntimeDiagnostics
import kotlin.coroutines.cancellation.CancellationException

internal object GoalRunnerBestEffortEmission {
  const val MAX_DIAGNOSTIC_MESSAGE_LENGTH: Int = 240

  fun boundedMessage(message: String): String = message.take(MAX_DIAGNOSTIC_MESSAGE_LENGTH)

  inline fun <T> runCancellable(block: () -> T): Result<T> = runCatching(block)

  inline fun record(
    diagnostics: RuntimeDiagnostics,
    write: () -> Boolean,
    missingMessage: () -> String,
    failureMessage: (Throwable) -> String,
  ) {
    val result = runCancellable(write)
    when (val failure = result.exceptionOrNull()) {
      null -> if (!result.getOrThrow()) {
        recordWarning(diagnostics, missingMessage())
      }
      else -> {
        rethrowIfCancellation(failure)
        recordWarning(diagnostics, failureMessage(failure), failure)
      }
    }
  }

  fun rethrowIfCancellation(failure: Throwable): Nothing? {
    when (failure) {
      is CancellationException -> throw failure
      is InterruptedException -> {
        Thread.currentThread().interrupt()
        throw failure
      }
      else -> return null
    }
  }

  fun recordWarning(diagnostics: RuntimeDiagnostics, message: String, error: Throwable? = null) {
    RuntimeDiagnosticsBestEffortWarning.record(diagnostics, message, error)
  }
}
