package skillbill.workflow.goal.model

import skillbill.workflow.taskruntime.model.DurableArtifactMapReader
import skillbill.workflow.taskruntime.model.toStringKeyedArtifactMap

internal fun reviewStateReader(map: Map<String, Any?>, sourceLabel: String): DurableArtifactMapReader =
  DurableArtifactMapReader(map) { detail ->
    reviewStateError(sourceLabel, detail)
  }

internal fun Any?.toReviewStateMap(sourceLabel: String): Map<String, Any?> =
  toStringKeyedArtifactMap { detail -> reviewStateError(sourceLabel, detail) }

internal fun Map<String, Any?>.requireOnlyReviewStateKeys(allowed: Set<String>, sourceLabel: String) {
  keys.forEach { key ->
    if (key !in allowed) reviewStateError("$sourceLabel.$key", "unknown field is not allowed.")
  }
}
