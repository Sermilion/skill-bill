package skillbill.infrastructure.launcher.codegraph

import skillbill.codegraph.model.CodeGraphLifecycleObservation
import skillbill.contracts.JsonCodec
import skillbill.contracts.codegraph.CodeGraphSessionPayloadKeys
import skillbill.infrastructure.host.jvm.atomicWriteString
import skillbill.ports.codegraph.CodeGraphLifecycleStore
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID

internal class FileSystemCodeGraphLifecycleStore(
  private val repositoryRoot: Path,
) : CodeGraphLifecycleStore {
  override fun persist(observation: CodeGraphLifecycleObservation) {
    val directory = repositoryRoot.resolve(".skill-bill").resolve("runtime").resolve("codegraph-sessions")
    Files.createDirectories(directory)
    val target = directory.resolve("${observation.sessionId}.json")
    val payload = linkedMapOf<String, Any?>(
      CodeGraphSessionPayloadKeys.CONTRACT_VERSION to observation.contractVersion,
      CodeGraphSessionPayloadKeys.SESSION_ID to observation.sessionId,
      CodeGraphSessionPayloadKeys.REPOSITORY to "<repository>",
      CodeGraphSessionPayloadKeys.CAPABILITY to observation.configuration.capability.wireValue,
      CodeGraphSessionPayloadKeys.TELEMETRY_DISABLED to observation.configuration.telemetryDisabled,
      CodeGraphSessionPayloadKeys.LIFECYCLE_STATE to observation.state.wireValue,
      CodeGraphSessionPayloadKeys.DEGRADATION_REASON to observation.degradation?.reason?.wireValue,
      CodeGraphSessionPayloadKeys.DETAIL to observation.degradation?.detail,
      CodeGraphSessionPayloadKeys.CLEANUP_ATTEMPTED to observation.cleanupAttempted,
      CodeGraphSessionPayloadKeys.CLEANUP_FAILURE to observation.cleanupFailure,
      CodeGraphSessionPayloadKeys.PRIMARY_FAILURE to observation.primaryFailure,
    )
    val encoded = JsonCodec.mapToJsonString(payload) + "\n"
    val events = directory.resolve(observation.sessionId)
    Files.createDirectories(events)
    atomicWriteString(events.resolve("${UUID.randomUUID()}.json"), encoded)
    atomicWriteString(target, encoded)
  }
}

internal fun persistCodeGraphObservation(store: CodeGraphLifecycleStore, observation: CodeGraphLifecycleObservation) {
  val interrupted = Thread.interrupted()
  try {
    store.persist(observation)
  } catch (_: Exception) {
    System.err.println(
      "CodeGraph lifecycle persistence failed: ${observation.state.wireValue}; " +
        "ordinary tools remain available.",
    )
  } finally {
    if (interrupted) Thread.currentThread().interrupt()
  }
}
