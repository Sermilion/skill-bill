package skillbill.infrastructure.launcher.codegraph

import skillbill.codegraph.model.CodeGraphDegradation
import skillbill.codegraph.model.CodeGraphLifecycleObservation
import skillbill.contracts.codegraph.CodeGraphDegradationReason
import skillbill.contracts.codegraph.CodeGraphLifecycleState
import skillbill.ports.codegraph.CodeGraphLifecycleStore
import skillbill.ports.codegraph.CodeGraphMcpProcess
import skillbill.ports.codegraph.CodeGraphSessionLease
import skillbill.ports.codegraph.model.CodeGraphMcpLaunchConfiguration
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

private const val PROCESS_MONITOR_INTERVAL_MILLIS = 100L

internal class CodeGraphSessionLeaseOwner(
  override val launchConfiguration: CodeGraphMcpLaunchConfiguration,
  private var observation: CodeGraphLifecycleObservation,
  private val store: CodeGraphLifecycleStore,
  private val process: CodeGraphMcpProcess,
  private val endpoint: CodeGraphMcpEndpoint,
  private val cleanupConfiguration: () -> Unit,
) : CodeGraphSessionLease {
  private var closed = false
  private var processClosed = false
  private var processFailed = false
  private val monitor = Executors.newSingleThreadScheduledExecutor { task ->
    Thread(task, "skill-bill-codegraph-monitor").apply { isDaemon = true }
  }
  private val hook =
    Thread({ closeWithTerminalState(null, CodeGraphLifecycleState.FAILED) }, "skill-bill-codegraph-shutdown")
  override val activeObservation: CodeGraphLifecycleObservation
    @Synchronized get() = observation

  fun observeLifetime() {
    Runtime.getRuntime().addShutdownHook(hook)
    monitor.scheduleWithFixedDelay({
      if (!process.alive) processCrashed()
    }, PROCESS_MONITOR_INTERVAL_MILLIS, PROCESS_MONITOR_INTERVAL_MILLIS, TimeUnit.MILLISECONDS)
  }

  @Synchronized
  private fun processCrashed() {
    if (closed || processFailed) return
    processFailed = true
    val cleanupFailure = closeProcess()
    update(
      observation.copy(
        state = if (cleanupFailure == null) CodeGraphLifecycleState.FAILED else CodeGraphLifecycleState.CLEANUP_FAILED,
        degradation = CodeGraphDegradation(
          CodeGraphDegradationReason.QUERY_FAILURE,
          "CodeGraph process exited. Use ordinary file tools.",
        ),
        cleanupAttempted = true,
        cleanupFailure = cleanupFailure,
        primaryFailure = CodeGraphLifecycleState.FAILED.wireValue,
      ),
    )
  }

  @Synchronized
  override fun activate(): CodeGraphLifecycleObservation {
    if (!closed && !processFailed) update(observation.copy(state = CodeGraphLifecycleState.ACTIVE))
    return observation
  }

  @Synchronized
  override fun recordDegradation(reason: CodeGraphDegradationReason, detail: String): CodeGraphLifecycleObservation {
    if (!closed) {
      update(
        observation.copy(
          degradation = CodeGraphDegradation(reason, "CodeGraph ${reason.wireValue}. Use ordinary file tools."),
        ),
      )
    }
    return observation
  }

  override fun close(primaryFailure: Throwable?): CodeGraphLifecycleObservation = closeWithTerminalState(
    primaryFailure,
    if (primaryFailure == null) CodeGraphLifecycleState.EXITED else CodeGraphLifecycleState.FAILED,
  )

  @Synchronized
  override fun closeWithTerminalState(
    primaryFailure: Throwable?,
    terminalState: CodeGraphLifecycleState,
  ): CodeGraphLifecycleObservation {
    if (closed) return observation
    if (!process.alive) processCrashed()
    val finalState = if (processFailed && terminalState == CodeGraphLifecycleState.EXITED) {
      CodeGraphLifecycleState.FAILED
    } else {
      terminalState
    }
    closed = true
    monitor.shutdown()
    val interrupted = Thread.interrupted()
    try {
      try {
        Runtime.getRuntime().removeShutdownHook(hook)
      } catch (_: IllegalStateException) {
        System.err.println("CodeGraph cleanup is running during JVM shutdown.")
      }
      val processCleanupFailure = closeProcess()
      var cleanupFailure = observation.cleanupFailure ?: processCleanupFailure
      listOf<() -> Unit>({ endpoint.close() }, cleanupConfiguration).forEach { cleanup ->
        try {
          cleanup()
        } catch (_: Exception) {
          cleanupFailure = "CodeGraph cleanup failed."
        }
      }
      update(
        observation.copy(
          state = if (cleanupFailure == null) finalState else CodeGraphLifecycleState.CLEANUP_FAILED,
          cleanupAttempted = true,
          cleanupFailure = cleanupFailure,
          primaryFailure = if (primaryFailure != null || finalState != CodeGraphLifecycleState.EXITED) {
            finalState.wireValue
          } else {
            null
          },
        ),
      )
    } finally {
      if (interrupted) Thread.currentThread().interrupt()
    }
    return observation
  }

  private fun closeProcess(): String? {
    if (processClosed) return null
    return try {
      process.close()
      processClosed = true
      null
    } catch (_: Exception) {
      processClosed = !process.alive
      "CodeGraph cleanup failed."
    }
  }

  private fun update(next: CodeGraphLifecycleObservation) {
    observation = next
    persistCodeGraphObservation(store, next)
  }
}
