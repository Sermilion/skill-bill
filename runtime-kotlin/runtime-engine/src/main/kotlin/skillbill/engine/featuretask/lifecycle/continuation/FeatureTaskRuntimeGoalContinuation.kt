package skillbill.engine.featuretask.lifecycle.continuation

import skillbill.engine.featuretask.model.core.FeatureTaskRuntimeRunRequest

fun isGoalContinuationRun(request: FeatureTaskRuntimeRunRequest): Boolean = request.goalContinuation != null
