package skillbill.contracts.workflow

const val GOAL_OBSERVABILITY_EVENT_CONTRACT_VERSION: String = "0.2"

object GoalObservabilityEventSchemaPaths {
  const val REPO_RELATIVE_PATH: String =
    "orchestration/contracts/goal-observability-event-schema.yaml"

  const val CLASSPATH_RESOURCE: String =
    "skillbill/infrastructure/contracts/goal-observability-event-schema.yaml"

  const val EXPECTED_SCHEMA_ID: String =
    "https://skill-bill.dev/contracts/goal-observability-event-schema.yaml"
}
