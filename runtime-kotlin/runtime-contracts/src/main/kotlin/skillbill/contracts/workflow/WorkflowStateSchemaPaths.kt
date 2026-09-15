package skillbill.contracts.workflow

const val WORKFLOW_STATE_CONTRACT_VERSION: String = "0.3"

object WorkflowStateSchemaPaths {

  const val REPO_RELATIVE_PATH: String =
    "orchestration/contracts/workflow-state-schema.yaml"

  const val CLASSPATH_RESOURCE: String =
    "skillbill/infrastructure/fs/contracts/workflow-state-schema.yaml"

  const val EXPECTED_SCHEMA_ID: String =
    "https://skill-bill.dev/contracts/workflow-state-schema.yaml"
}
