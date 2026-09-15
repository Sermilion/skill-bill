package skillbill.contracts.workflow

const val IDE_STATUS_CONTRACT_VERSION: String = "0.2"

const val GOAL_PLANNING_WAVE_CAP: Int = 5

object IdeStatusSchemaPaths {
  const val REPO_RELATIVE_PATH: String =
    "orchestration/contracts/ide-status-schema.yaml"

  const val CLASSPATH_RESOURCE: String =
    "skillbill/infrastructure/fs/contracts/ide-status-schema.yaml"

  const val EXPECTED_SCHEMA_ID: String =
    "https://skill-bill.dev/contracts/ide-status-schema.yaml"
}
