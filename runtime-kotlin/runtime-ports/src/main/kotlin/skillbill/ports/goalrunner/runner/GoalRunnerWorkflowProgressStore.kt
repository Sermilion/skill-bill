package skillbill.ports.goalrunner.runner

import skillbill.goalrunner.model.GoalRunnerObservabilityRecordRequest
import skillbill.ports.goalrunner.runner.model.GoalRunnerProgressEventRecordRequest
import skillbill.ports.goalrunner.runner.model.GoalRunnerWorkflowProgress
import skillbill.workflow.model.goalreview.GoalProgressEvent

interface GoalRunnerWorkflowProgressStore {
  fun progress(workflowId: String): GoalRunnerWorkflowProgress?

  fun recordObservabilityEvent(request: GoalRunnerObservabilityRecordRequest): Boolean

  fun recordProgressEvent(request: GoalRunnerProgressEventRecordRequest): Boolean

  fun progressEvents(workflowId: String): List<GoalProgressEvent>
}
