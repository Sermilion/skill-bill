package skillbill.engine.goalrunner.experiment

import skillbill.engine.goalrunner.model.GoalRunnerRunRequest
import skillbill.goalrunner.model.GoalRunnerRunReport
import skillbill.model.RuntimeContext

fun interface ExperimentGoalRunnerPort {
  fun run(request: GoalRunnerRunRequest): GoalRunnerRunReport
}

fun interface ExperimentGoalRunnerFactory {
  fun create(context: RuntimeContext): ExperimentGoalRunnerPort
}
