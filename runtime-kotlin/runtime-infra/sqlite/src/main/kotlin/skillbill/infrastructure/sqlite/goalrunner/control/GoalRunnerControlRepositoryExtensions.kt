package skillbill.infrastructure.sqlite.goalrunner.control
import skillbill.goalrunner.model.GOAL_ACTIVE_HEARTBEAT_GAP_LIMIT_MS
import skillbill.goalrunner.model.GOAL_PAUSE_REASON_RUNNER_INTERRUPTED
import skillbill.goalrunner.model.GoalRunnerControlState
import skillbill.goalrunner.model.GoalRunnerExecutionLease
import skillbill.goalrunner.model.parseExecutionLeaseInstant
import skillbill.ports.goalrunner.GoalRunnerControlRepository
import java.time.Duration

fun GoalRunnerControlRepository.executionLease(parentWorkflowId: String): GoalRunnerExecutionLease? =
  controlState(parentWorkflowId).executionLease

fun GoalRunnerControlRepository.acquireExecutionLease(
  parentWorkflowId: String,
  lease: GoalRunnerExecutionLease,
  expectedOwnerToken: String? = null,
): Boolean {
  val state = controlState(parentWorkflowId)
  if (state.executionLease?.ownerToken != expectedOwnerToken) return false
  persistControlState(
    parentWorkflowId,
    state.copy(
      executionLease = lease,
      activeDurationAsOf = lease.heartbeatAt,
      subtaskActiveDurationAsOf = lease.heartbeatAt.takeIf { state.currentSubtaskId != null },
    ),
  )
  return true
}

fun GoalRunnerControlRepository.heartbeatExecutionLease(
  parentWorkflowId: String,
  lease: GoalRunnerExecutionLease,
): Boolean {
  val state = controlState(parentWorkflowId)
  val current = state.executionLease ?: return false
  if (current.ownerToken != lease.ownerToken || current.generation != lease.generation) return false
  persistControlState(parentWorkflowId, state.advancedBy(lease.heartbeatAt).copy(executionLease = lease))
  return true
}

fun GoalRunnerControlRepository.releaseExecutionLease(
  parentWorkflowId: String,
  ownerToken: String,
  generation: Long,
): Boolean {
  val state = controlState(parentWorkflowId)
  val current = state.executionLease ?: return false
  if (current.ownerToken != ownerToken || current.generation != generation) return false
  persistControlState(
    parentWorkflowId,
    state.copy(executionLease = null, activeDurationAsOf = null, subtaskActiveDurationAsOf = null),
  )
  return true
}

fun GoalRunnerControlRepository.releaseExecutionLeaseIfExpired(
  parentWorkflowId: String,
  ownerToken: String,
  generation: Long,
  nowInstant: String,
): Boolean {
  val state = controlState(parentWorkflowId)
  val current = state.executionLease ?: return false
  if (current.ownerToken != ownerToken || current.generation != generation) return false
  val now = parseExecutionLeaseInstant("now_instant", nowInstant)
  if (current.expiresAtInstant.isAfter(now)) return false
  return releaseExecutionLease(parentWorkflowId, ownerToken, generation)
}

private fun GoalRunnerControlState.advancedBy(heartbeatAt: String): GoalRunnerControlState {
  val goal = advanceAccumulator(activeDurationMs, activeDurationAsOf, heartbeatAt)
  val subtask = if (currentSubtaskId != null) {
    advanceAccumulator(subtaskActiveDurationMs, subtaskActiveDurationAsOf, heartbeatAt)
  } else {
    subtaskActiveDurationMs to subtaskActiveDurationAsOf
  }
  return copy(
    activeDurationMs = goal.first,
    activeDurationAsOf = goal.second,
    subtaskActiveDurationMs = subtask.first,
    subtaskActiveDurationAsOf = subtask.second,
  )
}

private fun advanceAccumulator(accumulatedMs: Long, asOf: String?, heartbeatAt: String): Pair<Long, String?> {
  val previous = asOf ?: return accumulatedMs to heartbeatAt
  val elapsedMs = Duration.between(
    parseExecutionLeaseInstant("active_duration_as_of", previous),
    parseExecutionLeaseInstant("heartbeat_at", heartbeatAt),
  ).toMillis()
  val counted = elapsedMs.coerceIn(0, GOAL_ACTIVE_HEARTBEAT_GAP_LIMIT_MS)
  return accumulatedMs + counted to heartbeatAt
}

fun GoalRunnerControlRepository.clearRunnerInterruptedPauseState(parentWorkflowId: String): GoalRunnerControlState {
  val state = controlState(parentWorkflowId)
  if (state.pauseReason != GOAL_PAUSE_REASON_RUNNER_INTERRUPTED) return state
  return persistControlState(
    parentWorkflowId,
    state.copy(
      paused = false,
      pauseRequested = false,
      pauseConsumed = false,
      pauseReason = null,
      pausedAt = null,
    ),
  )
}
