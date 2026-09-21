package skillbill.infrastructure.launcher.codegraph

import skillbill.codegraph.model.CodeGraphDegradation
import skillbill.codegraph.model.CodeGraphLifecycleObservation
import skillbill.contracts.codegraph.CodeGraphDegradationReason
import skillbill.contracts.codegraph.CodeGraphLifecycleState
import skillbill.infrastructure.host.jvm.JdkHostPlatformPort
import skillbill.ports.codegraph.CodeGraphExecutablePort
import skillbill.ports.codegraph.CodeGraphLifecycleStore
import skillbill.ports.codegraph.CodeGraphMcpProcess
import skillbill.ports.codegraph.CodeGraphMcpProcessPort
import skillbill.ports.codegraph.CodeGraphSessionPort
import skillbill.ports.codegraph.model.CodeGraphSessionPreparation
import skillbill.ports.codegraph.model.CodeGraphSessionRequest
import skillbill.ports.system.HostPlatformPort
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import kotlin.coroutines.cancellation.CancellationException

internal class FileSystemCodeGraphSession(
  private val executable: CodeGraphExecutablePort,
  private val lifecycleStoreFactory: (Path) -> CodeGraphLifecycleStore,
  private val mcp: CodeGraphMcpProcessPort = JvmCodeGraphMcpProcess(),
  private val hostPlatform: HostPlatformPort = JdkHostPlatformPort,
) : CodeGraphSessionPort {
  override fun prepare(request: CodeGraphSessionRequest): CodeGraphSessionPreparation {
    val store = lifecycleStoreFactory(request.repositoryRoot)
    val initial =
      CodeGraphLifecycleObservation(
        UUID.randomUUID().toString(),
        CodeGraphLifecycleState.PREPARING,
        request.configuration,
      )
    persistCodeGraphObservation(store, initial)
    val setupFailure = try {
      prepareGraph(request)
    } catch (cancellation: CancellationException) {
      persistCodeGraphObservation(store, initial.copy(state = CodeGraphLifecycleState.CANCELLED))
      throw cancellation
    } catch (interrupted: InterruptedException) {
      persistCodeGraphObservation(store, initial.copy(state = CodeGraphLifecycleState.CANCELLED))
      Thread.currentThread().interrupt()
      throw interrupted
    } catch (_: Exception) {
      CodeGraphDegradationReason.INITIALIZATION_FAILURE
    }
    if (setupFailure != null) return fallback(store, initial, setupFailure)
    return startMcp(request, store, initial)
  }

  private fun prepareGraph(request: CodeGraphSessionRequest): CodeGraphDegradationReason? {
    if (!executable.isAvailable()) return CodeGraphDegradationReason.MISSING_CLI
    val graph = request.repositoryRoot.resolve(".codegraph")
    if (!Files.isDirectory(graph)) {
      val initialized = executable.execute(
        CodeGraphCommandBuilder.init(EXECUTABLE, request.repositoryRoot),
        request.repositoryRoot,
        PROCESS_ENVIRONMENT,
      )
      if (initialized.exitCode != 0) {
        return if (CodeGraphReadinessParser.unsupported(initialized.stdout + initialized.stderr)) {
          CodeGraphDegradationReason.UNSUPPORTED_COMMAND
        } else {
          CodeGraphDegradationReason.INITIALIZATION_FAILURE
        }
      }
      if (!Files.isDirectory(graph)) return CodeGraphDegradationReason.MISSING_GRAPH
    }
    val status = executable.execute(
      CodeGraphCommandBuilder.status(EXECUTABLE, request.repositoryRoot),
      request.repositoryRoot,
      PROCESS_ENVIRONMENT,
    )
    return (
      CodeGraphReadinessParser.parse(
        status.exitCode,
        status.stdout,
        status.stderr,
        Files.isDirectory(graph),
      ) as? CodeGraphReadiness.Degraded
      )?.reason
  }

  private fun startMcp(
    request: CodeGraphSessionRequest,
    store: CodeGraphLifecycleStore,
    initial: CodeGraphLifecycleObservation,
  ): CodeGraphSessionPreparation {
    var process: CodeGraphMcpProcess? = null
    var endpoint: CodeGraphMcpEndpoint? = null
    var configuration: CodeGraphSessionConfigurationFile? = null
    var owner: CodeGraphSessionLeaseOwner? = null
    return runCatching {
      val started = mcp.start(CodeGraphCommandBuilder.serve(EXECUTABLE), request.repositoryRoot, PROCESS_ENVIRONMENT)
      process = started
      val server =
        CodeGraphMcpEndpoint(
          started,
          request.repositoryRoot,
          "codegraph_${request.configuration.capability.wireValue}",
        ) { reason ->
          owner?.recordDegradation(reason, reason.wireValue)
        }
      endpoint = server
      server.start()
      val config = CodeGraphSessionConfigurationFile.create(request, server.url, hostPlatform.resolveUserHome())
      configuration = config
      val ready = initial.copy(state = CodeGraphLifecycleState.READY)
      val lease = CodeGraphSessionLeaseOwner(config.configuration, ready, store, started, server, config::close)
      owner = lease
      persistCodeGraphObservation(store, ready)
      lease.observeLifetime()
      CodeGraphSessionPreparation.Ready(lease)
    }.getOrElse { failure ->
      finishFailedStartup(
        store,
        initial,
        failure,
        listOf({ process?.close() }, { endpoint?.close() }, { configuration?.close() }),
      )
    }
  }

  private fun finishFailedStartup(
    store: CodeGraphLifecycleStore,
    initial: CodeGraphLifecycleObservation,
    failure: Throwable,
    cleanupActions: List<() -> Unit>,
  ): CodeGraphSessionPreparation.Fallback {
    var cleanupFailed = false
    cleanupActions.forEach { cleanup ->
      try {
        cleanup()
      } catch (_: Exception) {
        cleanupFailed = true
      }
    }
    if (failure is CancellationException || failure is InterruptedException) {
      persistCodeGraphObservation(
        store,
        initial.copy(
          state = CodeGraphLifecycleState.CANCELLED,
          cleanupAttempted = true,
          cleanupFailure = if (cleanupFailed) "CodeGraph cleanup failed." else null,
        ),
      )
      if (failure is InterruptedException) Thread.currentThread().interrupt()
      throw failure
    }
    if (failure !is Exception) throw failure
    val reason = if (failure is CodeGraphCapabilityUnavailable) {
      CodeGraphDegradationReason.UNAVAILABLE_CAPABILITY
    } else {
      CodeGraphDegradationReason.MCP_STARTUP_FAILURE
    }
    return fallback(
      store,
      initial.copy(
        cleanupAttempted = true,
        cleanupFailure = if (cleanupFailed) "CodeGraph cleanup failed." else null,
      ),
      reason,
    )
  }

  private fun fallback(
    store: CodeGraphLifecycleStore,
    initial: CodeGraphLifecycleObservation,
    reason: CodeGraphDegradationReason,
  ): CodeGraphSessionPreparation.Fallback {
    val observation = initial.copy(
      state = CodeGraphLifecycleState.FALLBACK,
      degradation = CodeGraphDegradation(reason, "CodeGraph ${reason.wireValue}. Use ordinary file tools."),
    )
    persistCodeGraphObservation(store, observation)
    return CodeGraphSessionPreparation.Fallback(observation)
  }

  companion object {
    const val EXECUTABLE = "codegraph"
    val PROCESS_ENVIRONMENT =
      mapOf(
        "CODEGRAPH_TELEMETRY" to "0",
        "DO_NOT_TRACK" to "1",
        "CODEGRAPH_NO_DAEMON" to "1",
        "CODEGRAPH_MCP_TOOLS" to "explore",
      )
  }
}
