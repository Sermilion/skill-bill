package skillbill.cli.kernel.cli
import skillbill.application.telemetry.service.TelemetryService
import skillbill.ports.diagnostics.RuntimeDiagnostics

private const val DRAIN_TIMEOUT_MILLIS = 5_000L

internal fun drainTelemetryOnCompletion(telemetryService: TelemetryService, diagnostics: RuntimeDiagnostics) {
  val worker = Thread {
    runCatching { telemetryService.autoSync() }
      .onFailure { error -> diagnostics.warning("telemetry completion drain failed to flush the outbox", error) }
  }
  worker.isDaemon = true
  worker.start()
  try {
    worker.join(DRAIN_TIMEOUT_MILLIS)
    if (worker.isAlive) {
      diagnostics.warning(
        "telemetry completion drain abandoned after ${DRAIN_TIMEOUT_MILLIS}ms; the outbox may not have flushed",
      )
    }
  } catch (interrupted: InterruptedException) {
    diagnostics.warning("telemetry completion drain interrupted before the outbox flushed", interrupted)
    Thread.currentThread().interrupt()
  }
}
