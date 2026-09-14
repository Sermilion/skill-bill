package skillbill.goalrunner.subtaskreview

import skillbill.contracts.JsonCodec

internal fun Any.asGoalSubtaskReviewPhaseOutputMap(): Map<String, Any?> = JsonCodec.anyToStringAnyMap(this)
  ?: throw IllegalArgumentException("Goal subtask review phase output must decode to an object.")
