package skillbill.engine.featuretask

import skillbill.engine.featuretask.model.FeatureTaskRuntimeRunRequest

fun isGoalContinuationRun(request: FeatureTaskRuntimeRunRequest): Boolean = request.goalContinuation != null
