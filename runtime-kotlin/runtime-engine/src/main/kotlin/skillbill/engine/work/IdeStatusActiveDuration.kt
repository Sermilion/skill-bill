package skillbill.engine.work

import skillbill.goalrunner.model.ExecutionLiveness
import skillbill.goalrunner.model.GoalRunnerStatusProjection
import java.time.Instant

internal fun GoalRunnerStatusProjection.recordedActiveDurationMs(): Long? =
  activeDurationMs.takeIf { it > 0 || liveActiveDurationAnchor() != null }

internal fun GoalRunnerStatusProjection.liveActiveDurationAnchor(): Instant? =
  parseInstantOrNull(activeDurationAsOf).takeIf { executionLiveness == ExecutionLiveness.LIVE }

internal fun GoalRunnerStatusProjection.recordedSubtaskActiveDurationMs(): Long? =
  subtaskActiveDurationMs.takeIf { it > 0 || liveSubtaskActiveDurationAnchor() != null }

internal fun GoalRunnerStatusProjection.liveSubtaskActiveDurationAnchor(): Instant? =
  parseInstantOrNull(subtaskActiveDurationAsOf).takeIf { executionLiveness == ExecutionLiveness.LIVE }
