package skillbill.application.telemetry.lifecycle

import skillbill.application.telemetry.model.GoalFinishedRequest
import skillbill.application.telemetry.model.GoalIssueFinishedRequest
import skillbill.application.telemetry.model.GoalStartedRequest
import skillbill.application.telemetry.model.GoalSubtaskFinishedRequest

interface GoalLifecycleTelemetryEmitter {
  fun goalStarted(request: GoalStartedRequest)

  fun goalSubtaskFinished(request: GoalSubtaskFinishedRequest)

  fun goalFinished(request: GoalFinishedRequest)

  fun goalIssueFinished(request: GoalIssueFinishedRequest)
}
