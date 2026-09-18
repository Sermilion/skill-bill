package skillbill.contracts.workflow

const val GOAL_PROGRESS_EVENT_CONTRACT_VERSION: String = "0.1"

object GoalProgressEventSchemaPaths {
  const val REPO_RELATIVE_PATH: String =
    "orchestration/contracts/goal-progress-event-schema.yaml"

  const val CLASSPATH_RESOURCE: String =
    "skillbill/infrastructure/contracts/goal-progress-event-schema.yaml"

  const val EXPECTED_SCHEMA_ID: String =
    "https://skill-bill.dev/contracts/goal-progress-event-schema.yaml"
}
