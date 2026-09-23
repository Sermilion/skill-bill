package skillbill.application.telemetry.lifecycle

import skillbill.application.telemetry.model.GoalFinishedRequest
import skillbill.application.telemetry.model.GoalIssueFinishedRequest
import skillbill.application.telemetry.model.GoalStartedRequest
import skillbill.application.telemetry.model.GoalSubtaskFinishedRequest

val noopGoalLifecycleTelemetryEmitter: GoalLifecycleTelemetryEmitter =
  object : GoalLifecycleTelemetryEmitter {
    override fun goalStarted(request: GoalStartedRequest) = Unit

    override fun goalSubtaskFinished(request: GoalSubtaskFinishedRequest) = Unit

    override fun goalFinished(request: GoalFinishedRequest) = Unit

    override fun goalIssueFinished(request: GoalIssueFinishedRequest) = Unit
  }
