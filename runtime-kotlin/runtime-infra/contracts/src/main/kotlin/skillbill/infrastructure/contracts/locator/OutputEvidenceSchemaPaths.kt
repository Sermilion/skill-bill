package skillbill.infrastructure.contracts.locator

object ProducerOutputEvidenceSchemaPaths {
  const val REPOSITORY_PATH: String =
    "orchestration/contracts/producer-output-evidence-schema.yaml"
  const val CLASSPATH_RESOURCE: String =
    "skillbill/infrastructure/contracts/producer-output-evidence-schema.yaml"
  const val EXPECTED_SCHEMA_ID: String =
    "https://skill-bill.dev/contracts/producer-output-evidence-schema.yaml"
}

object RejectedOutputDiagnosticSchemaPaths {
  const val REPOSITORY_PATH: String =
    "orchestration/contracts/rejected-output-diagnostic-schema.yaml"
  const val CLASSPATH_RESOURCE: String =
    "skillbill/infrastructure/contracts/rejected-output-diagnostic-schema.yaml"
  const val EXPECTED_SCHEMA_ID: String =
    "https://skill-bill.dev/contracts/rejected-output-diagnostic-schema.yaml"
}
