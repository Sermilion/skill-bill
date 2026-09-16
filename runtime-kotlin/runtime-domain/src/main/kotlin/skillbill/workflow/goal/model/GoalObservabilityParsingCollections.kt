package skillbill.workflow.goal.model

import skillbill.workflow.goal.invalidGoalObservabilityEvent

internal fun Map<*, *>.requireOnlyKeys(allowedKeys: Set<String>, sourceLabel: String) {
  keys.forEach { key ->
    val stringKey = key as? String
      ?: throw invalidGoalObservabilityEvent(sourceLabel, "", "event keys must be strings.")
    if (stringKey !in allowedKeys) {
      throw invalidGoalObservabilityEvent(sourceLabel, stringKey, "unknown field is not allowed.")
    }
  }
}

internal fun Any?.asRequiredMap(sourceLabel: String): Map<*, *> = this as? Map<*, *>
  ?: throw invalidGoalObservabilityEvent(sourceLabel, "", "field must be an object.")
