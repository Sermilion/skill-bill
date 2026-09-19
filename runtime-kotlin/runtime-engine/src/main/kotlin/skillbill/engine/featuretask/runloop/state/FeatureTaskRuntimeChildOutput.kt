package skillbill.engine.featuretask.runloop.state
import skillbill.ports.agentrun.model.AgentRunLaunchFacts

const val FEATURE_TASK_RUNTIME_PROCESS_FAILURE_RULE: String = "process-failure"

internal data class FeatureTaskRuntimeChildOutput(
  val stdout: String,
  val stderr: String,
  val exitStatus: Int?,
  val timedOut: Boolean,
  val interrupted: Boolean,
  val spawnFailed: Boolean,
) {

  fun storedBody(): String = buildString {
    appendLine("### child process failure diagnostic")
    appendLine("### exit_status=${exitStatus ?: "none"} timed_out=$timedOut interrupted=$interrupted")
    appendLine("### spawn_failed=$spawnFailed")
    appendLine("### --- stdout (${stdout.length} chars) ---")
    appendLine(stdout.ifEmpty { "<empty>" })
    appendLine("### --- stderr (${stderr.length} chars) ---")
    appendLine(stderr.ifEmpty { "<empty>" })
    appendLine("### end of child process failure diagnostic")
  }

  val isEmpty: Boolean get() = stdout.isEmpty() && stderr.isEmpty()
}

internal fun featureTaskRuntimeChildOutput(facts: AgentRunLaunchFacts): FeatureTaskRuntimeChildOutput? =
  FeatureTaskRuntimeChildOutput(
    stdout = facts.stdout,
    stderr = facts.stderr,
    exitStatus = facts.exitStatus,
    timedOut = facts.timedOut,
    interrupted = facts.interrupted,
    spawnFailed = facts.spawnFailed,
  ).takeUnless(FeatureTaskRuntimeChildOutput::isEmpty)
