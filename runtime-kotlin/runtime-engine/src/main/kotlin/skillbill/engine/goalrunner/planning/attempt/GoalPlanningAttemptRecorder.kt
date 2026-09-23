package skillbill.engine.goalrunner.planning.attempt

import me.tatarka.inject.annotations.Inject
import skillbill.application.runtime.RuntimeSingleton
import skillbill.engine.goalrunner.planning.model.GoalPlanningAttemptRecord
import skillbill.ports.goalrunner.runner.GoalRunnerWorkflowOutcomeStore
import skillbill.ports.goalrunner.runner.model.GoalRunnerProgressEventRecordRequest
import skillbill.workflow.model.goalreview.GoalProgressEvent
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
  private val nextSequenceByWorkflow = mutableMapOf<String, Int>()

  @Synchronized
  override fun record(attempt: GoalPlanningAttemptRecord) {
    outcomeStore.recordProgressEvent(
      GoalRunnerProgressEventRecordRequest(
        workflowId = attempt.parentWorkflowId,
        event =
          GoalProgressEvent(
            eventKind = attempt.eventKind,
            workflowId = attempt.parentWorkflowId,
            workflowPhase = "goal_planning",
            processAlive = true,
            sequenceNumber =
              nextSequenceByWorkflow.getOrPut(attempt.parentWorkflowId) {
                outcomeStore.ledgerSequenceWatermarks(attempt.issueKey)
                  .maxProgressSequence
                  ?.plus(1)
                  ?: 0
              },
            timestamp = clock.instant(),
            stepId = attempt.phaseId,
            operationName = "${attempt.phaseId}:${attempt.subtaskId}:attempt:${attempt.attempt}",
            operationKind = "planning_projection_attempt",
            expectedLong = true,
            outcome = attempt.outcome,
          ),
      ),
    )
    nextSequenceByWorkflow[attempt.parentWorkflowId] = nextSequenceByWorkflow.getValue(attempt.parentWorkflowId) + 1
  }
}
