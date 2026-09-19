package skillbill.contracts.workflow.identity.task

const val FEATURE_TASK_EXECUTION_IDENTITY_CONTRACT_VERSION: String = "0.1"

object FeatureTaskExecutionIdentitySchemaPaths {
  const val REPO_RELATIVE_PATH: String =
    "orchestration/contracts/feature-task-execution-identity-schema.yaml"
  const val CLASSPATH_RESOURCE: String =
    "skillbill/infrastructure/contracts/feature-task-execution-identity-schema.yaml"
  const val EXPECTED_SCHEMA_ID: String =
    "https://skill-bill.dev/contracts/feature-task-execution-identity-schema.yaml"
}
