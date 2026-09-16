package skillbill.workflow.goal.model

import skillbill.contracts.workflow.GOAL_OBSERVABILITY_EVENT_CONTRACT_VERSION
import skillbill.workflow.goal.invalidGoalObservabilityEvent
import skillbill.workflow.taskruntime.model.DurableArtifactMapReader
import skillbill.workflow.taskruntime.model.toStringKeyedArtifactMap

internal fun goalObservabilityReader(
  map: Map<*, *>,
  sourceLabel: String,
): DurableArtifactMapReader {
  val converted = map.toStringKeyedArtifactMap { detail ->
    throw invalidGoalObservabilityEvent(sourceLabel, "", detail)
  }
  return DurableArtifactMapReader(converted) { detail ->
    throw invalidGoalObservabilityEvent(sourceLabel, detail, "malformed durable field.")
  }
}

internal fun requireGoalObservabilityContractVersion(
  reader: DurableArtifactMapReader,
  sourceLabel: String,
): String = reader.requiredString("contract_version").also { value ->
  if (value != GOAL_OBSERVABILITY_EVENT_CONTRACT_VERSION) {
    throw invalidGoalObservabilityEvent(
      sourceLabel,
      "contract_version",
      "field must equal $GOAL_OBSERVABILITY_EVENT_CONTRACT_VERSION.",
    )
  }
}
