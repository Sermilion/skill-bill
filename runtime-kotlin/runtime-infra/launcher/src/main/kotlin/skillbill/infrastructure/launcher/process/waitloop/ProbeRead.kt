package skillbill.infrastructure.launcher.process.waitloop
import skillbill.infrastructure.launcher.process.launch.AgentRunActivityProbe
import skillbill.infrastructure.launcher.process.launch.ProcessRunDegradationRecorder
import skillbill.ports.agentrun.model.AgentRunDeclaredProgressProbe
import skillbill.ports.agentrun.model.AgentRunDeclaredProgressSnapshot
import skillbill.ports.agentrun.model.AgentRunMcpStartupProbe
import skillbill.ports.agentrun.model.AgentRunProgressProbe
import kotlin.coroutines.cancellation.CancellationException

internal data class ProbeRead<T>(
  val value: T?,
  val failed: Boolean,
)

internal fun AgentRunProgressProbe.readProgressToken(recorder: ProcessRunDegradationRecorder): ProbeRead<String> =
  readProbe(recorder, "progress_token") { progressToken() }

internal fun AgentRunProgressProbe.readProgressLabel(recorder: ProcessRunDegradationRecorder): ProbeRead<String> =
  readProbe(recorder, "progress_label") { progressLabel() }

internal fun AgentRunActivityProbe.readActivityToken(recorder: ProcessRunDegradationRecorder): ProbeRead<String> =
  readProbe(recorder, "activity_token") { activityToken() }

internal fun AgentRunActivityProbe.readActivityLabel(recorder: ProcessRunDegradationRecorder): ProbeRead<String> =
  readProbe(recorder, "activity_label") { activityLabel() }

internal fun AgentRunDeclaredProgressProbe.readDeclaredProgress(
  recorder: ProcessRunDegradationRecorder,
): ProbeRead<AgentRunDeclaredProgressSnapshot> = readProbe(recorder, "declared_progress") { latestDeclaredProgress() }

internal fun AgentRunMcpStartupProbe.readStartupObserved(recorder: ProcessRunDegradationRecorder): ProbeRead<Boolean> =
  readProbe(recorder, "mcp_startup") { startupObserved() }

private inline fun <T> readProbe(
  recorder: ProcessRunDegradationRecorder,
  seam: String,
  read: () -> T?,
): ProbeRead<T> =
  runCatching { read() }.fold(
    onSuccess = { value -> ProbeRead(value = value, failed = false) },
    onFailure = { failure ->
      if (failure is CancellationException) throw failure
      recorder.recordProbeFailure(seam, failure)
      ProbeRead(value = null, failed = true)
    },
  )
