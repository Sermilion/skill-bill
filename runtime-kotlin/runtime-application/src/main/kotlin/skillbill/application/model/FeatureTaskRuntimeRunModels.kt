package skillbill.application.model

import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeRunInvariants
import java.nio.file.Path
import kotlin.time.Duration

/**
 * SKILL-65 Subtask 3 (AC1, AC4, AC6, AC7, AC8): the request that drives one
 * deterministic feature-task-runtime phase-loop run.
 *
 * It carries only inert values: the issue key and existing runtime workflow id,
 * the run-invariants (layer 1 of every phase handoff), the per-phase agent
 * assignment plus optional override, the resolved invoking agent id (the
 * documented default — no hardcoded `codex`), the optional db path override, and
 * the repo root as an INERT [Path] value (no file IO is performed against it in
 * the application layer). An optional [eventSink] receives observability events.
 */
data class FeatureTaskRuntimeRunRequest(
  val issueKey: String,
  val workflowId: String,
  val sessionId: String,
  val runInvariants: FeatureTaskRuntimeRunInvariants,
  val invokedAgentId: String,
  val agentAssignment: FeatureTaskRuntimeAgentAssignment = FeatureTaskRuntimeAgentAssignment(),
  val environment: Map<String, String> = emptyMap(),
  val dbPathOverride: String? = null,
  val repoRoot: Path,
  /** Optional per-phase wall-clock cap forwarded to each phase agent launch. */
  val timeout: Duration? = null,
  val eventSink: FeatureTaskRuntimeRunEventSink = FeatureTaskRuntimeRunEventSink.NONE,
) {
  init {
    require(issueKey.isNotBlank()) { "FeatureTaskRuntimeRunRequest.issueKey is required." }
    require(workflowId.isNotBlank()) { "FeatureTaskRuntimeRunRequest.workflowId is required." }
    require(invokedAgentId.isNotBlank()) {
      "FeatureTaskRuntimeRunRequest.invokedAgentId is required; it is the documented default agent."
    }
  }
}

/**
 * SKILL-65 Subtask 3 (AC3, AC4, AC7): the terminal report of one phase-loop run.
 *
 * [Completed] means every ordered phase produced schema-valid output and was
 * marked complete. [Blocked] means the run halted loudly at [lastIncompletePhase]
 * (schema-gate failure, exhausted fix-loop budget, or a missing required
 * upstream output) with a human-readable [blockedReason].
 */
sealed interface FeatureTaskRuntimeRunReport {
  val issueKey: String
  val workflowId: String

  data class Completed(
    override val issueKey: String,
    override val workflowId: String,
    val completedPhaseIds: List<String>,
  ) : FeatureTaskRuntimeRunReport

  data class Blocked(
    override val issueKey: String,
    override val workflowId: String,
    val lastIncompletePhase: String,
    val blockedReason: String,
    val completedPhaseIds: List<String>,
  ) : FeatureTaskRuntimeRunReport {
    init {
      require(lastIncompletePhase.isNotBlank()) {
        "FeatureTaskRuntimeRunReport.Blocked.lastIncompletePhase must be non-blank."
      }
      require(blockedReason.isNotBlank()) {
        "FeatureTaskRuntimeRunReport.Blocked.blockedReason must be non-blank."
      }
    }
  }
}

/**
 * SKILL-65 Subtask 3 (AC4): typed observability events emitted at phase
 * boundaries. A thin sink is carried on the run request instead of introducing
 * a new infra type, mirroring `GoalRunnerEventSink`.
 */
sealed interface FeatureTaskRuntimeRunEvent {
  val workflowId: String
  val phaseId: String

  data class PhaseStarted(
    override val workflowId: String,
    override val phaseId: String,
    val resolvedAgentId: String,
    val attemptCount: Int,
    val resumed: Boolean,
  ) : FeatureTaskRuntimeRunEvent

  data class PhaseFixLoopIteration(
    override val workflowId: String,
    override val phaseId: String,
    val resolvedAgentId: String,
    val attemptCount: Int,
    val fixLoopIteration: Int,
  ) : FeatureTaskRuntimeRunEvent

  data class PhaseCompleted(
    override val workflowId: String,
    override val phaseId: String,
    val resolvedAgentId: String,
    val attemptCount: Int,
  ) : FeatureTaskRuntimeRunEvent

  data class PhaseBlocked(
    override val workflowId: String,
    override val phaseId: String,
    val resolvedAgentId: String,
    val attemptCount: Int,
    val blockedReason: String,
  ) : FeatureTaskRuntimeRunEvent
}

fun interface FeatureTaskRuntimeRunEventSink {
  fun emit(event: FeatureTaskRuntimeRunEvent)

  companion object {
    val NONE: FeatureTaskRuntimeRunEventSink = FeatureTaskRuntimeRunEventSink {}
  }
}

/**
 * SKILL-65 Subtask 3 (AC5): outcome of the bounded fix-loop policy
 * ([skillbill.application.FeatureTaskRuntimeFixLoopPolicy]) for a failed phase
 * attempt.
 */
sealed interface FeatureTaskRuntimeFixLoopDecision {
  /** Re-run the phase at [nextIteration]; ledger records [fixLoopIteration]. */
  data class Retry(val nextIteration: Int, val fixLoopIteration: Int) : FeatureTaskRuntimeFixLoopDecision

  /** Stop and block the run with [blockedReason]. */
  data class Block(val blockedReason: String) : FeatureTaskRuntimeFixLoopDecision
}
