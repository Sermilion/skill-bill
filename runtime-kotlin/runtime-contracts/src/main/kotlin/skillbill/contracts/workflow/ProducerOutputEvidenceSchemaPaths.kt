package skillbill.contracts.workflow

const val PRODUCER_OUTPUT_EVIDENCE_CONTRACT_VERSION: String = "0.1"

object ProducerOutputEvidenceSchemaPaths {
  const val REPOSITORY_PATH: String =
    "orchestration/contracts/producer-output-evidence-schema.yaml"
  const val CLASSPATH_RESOURCE: String =
    "skillbill/infrastructure/contracts/producer-output-evidence-schema.yaml"
  const val EXPECTED_SCHEMA_ID: String =
    "https://skill-bill.dev/contracts/producer-output-evidence-schema.yaml"
}
