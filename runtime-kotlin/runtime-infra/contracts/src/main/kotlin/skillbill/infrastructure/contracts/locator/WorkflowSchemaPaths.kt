package skillbill.infrastructure.contracts.locator

object WorkflowStateSchemaPaths {
  const val REPO_RELATIVE_PATH: String =
    "orchestration/contracts/workflow-state-schema.yaml"

  const val CLASSPATH_RESOURCE: String =
    "skillbill/infrastructure/contracts/workflow-state-schema.yaml"

  const val EXPECTED_SCHEMA_ID: String =
    "https://skill-bill.dev/contracts/workflow-state-schema.yaml"
}

object IdeStatusSchemaPaths {
  const val REPO_RELATIVE_PATH: String =
    "orchestration/contracts/ide-status-schema.yaml"

  const val CLASSPATH_RESOURCE: String =
    "skillbill/infrastructure/contracts/ide-status-schema.yaml"

  const val EXPECTED_SCHEMA_ID: String =
    "https://skill-bill.dev/contracts/ide-status-schema.yaml"
}
