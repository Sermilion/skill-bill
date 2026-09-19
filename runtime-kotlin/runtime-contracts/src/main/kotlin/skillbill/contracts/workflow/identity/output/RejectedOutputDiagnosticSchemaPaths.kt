package skillbill.contracts.workflow.identity.output

const val REJECTED_OUTPUT_DIAGNOSTIC_CONTRACT_VERSION: String = "0.1"

object RejectedOutputDiagnosticSchemaPaths {
  const val REPOSITORY_PATH: String =
    "orchestration/contracts/rejected-output-diagnostic-schema.yaml"
  const val CLASSPATH_RESOURCE: String =
    "skillbill/infrastructure/contracts/rejected-output-diagnostic-schema.yaml"
  const val EXPECTED_SCHEMA_ID: String =
    "https://skill-bill.dev/contracts/rejected-output-diagnostic-schema.yaml"
}
