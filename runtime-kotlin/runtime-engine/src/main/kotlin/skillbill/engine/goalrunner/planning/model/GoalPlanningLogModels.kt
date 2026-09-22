package skillbill.engine.goalrunner.planning.model

import java.nio.file.Path
import java.time.Duration
import java.time.Instant

enum class GoalPlanningAttemptOutcome(val wireValue: String) {
  IN_FLIGHT("in_flight"),
  FAILED("failed"),
  SUCCEEDED("succeeded"),
  ;

  companion object {
    fun fromWire(value: String): GoalPlanningAttemptOutcome? = entries.firstOrNull { it.wireValue == value }
  }
}

data class GoalPlanningLogRequest(
  val issueKey: String,
  val repoRoot: Path? = null,
  val subtaskId: Int? = null,
  val failuresOnly: Boolean = false,
)

data class GoalPlanningLogAttempt(
  val phaseId: String,
  val subtaskId: Int,
  val attempt: Int,
  val startedAt: Instant?,
  val finishedAt: Instant?,
  val outcome: GoalPlanningAttemptOutcome,
  val rule: String? = null,
  val reason: String? = null,
  val agentId: String? = null,
  val rejectedOutputIdentity: String? = null,
  val rejectedOutputBytes: Long? = null,
) {
  val durationMs: Long?
    get() =
      startedAt?.let { start ->
        finishedAt?.let { end ->
          if (end.isBefore(start)) null else Duration.between(start, end).toMillis()
        }
      }

  val timestampsInconsistent: Boolean
    get() = startedAt != null && finishedAt != null && finishedAt.isBefore(startedAt)

  val inFlight: Boolean get() = startedAt != null && finishedAt == null
}

data class GoalPlanningLog(
  val issueKey: String,
  val parentWorkflowId: String?,
  val attempts: List<GoalPlanningLogAttempt> = emptyList(),
) {
  val totalAttempts: Int get() = attempts.size
  val failedAttempts: Int get() = attempts.count { it.outcome == GoalPlanningAttemptOutcome.FAILED }
  val succeededAttempts: Int get() = attempts.count { it.outcome == GoalPlanningAttemptOutcome.SUCCEEDED }

  val totalPlanningMs: Long get() = attempts.mapNotNull(GoalPlanningLogAttempt::durationMs).sum()

  val firstAttemptFailures: Int
    get() = attempts.count { it.attempt == 1 && it.outcome == GoalPlanningAttemptOutcome.FAILED }

  val phasesObserved: Int get() = attempts.map { it.phaseId }.distinct().size
}
