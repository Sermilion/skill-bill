package skillbill.engine.goalrunner.planning.attempt

import me.tatarka.inject.annotations.Inject
import skillbill.application.runtime.RuntimeSingleton
import skillbill.engine.goalrunner.planning.model.GoalPlanningAttemptRecord
import skillbill.ports.goalrunner.runner.GoalRunnerWorkflowOutcomeStore
import skillbill.ports.goalrunner.runner.model.GoalProgressEventDraft
import skillbill.ports.goalrunner.runner.model.GoalRunnerProgressEventRecordRequest
import java.time.Clock

fun interface GoalPlanningAttemptRecorder {
  fun record(attempt: GoalPlanningAttemptRecord)
}

@RuntimeSingleton
@Inject
class DurableGoalPlanningAttemptRecorder(
  private val outcomeStore: GoalRunnerWorkflowOutcomeStore,
  private val clock: Clock,
) : GoalPlanningAttemptRecorder {
  override fun record(attempt: GoalPlanningAttemptRecord) {
    outcomeStore.recordProgressEvent(
      GoalRunnerProgressEventRecordRequest(
        workflowId = attempt.parentWorkflowId,
        issueKey = attempt.issueKey,
        draft =
          GoalProgressEventDraft(
            eventKind = attempt.eventKind,
            workflowId = attempt.parentWorkflowId,
            workflowPhase = "goal_planning",
            processAlive = true,
            timestamp = clock.instant(),
            stepId = attempt.phaseId,
            operationName = "${attempt.phaseId}:${attempt.subtaskId}:attempt:${attempt.attempt}",
            operationKind = "planning_projection_attempt",
            expectedLong = true,
            outcome = attempt.outcome,
          ),
      ),
    )
  }
}
