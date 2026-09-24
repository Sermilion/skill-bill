package skillbill.engine.goalrunner.experiment

import skillbill.engine.goalrunner.model.GoalRunnerRunRequest
import skillbill.goalrunner.model.GoalRunnerRunReport

fun interface ExperimentGoalRunnerPort {
  fun run(request: GoalRunnerRunRequest): GoalRunnerRunReport
}
