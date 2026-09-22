package skillbill.infrastructure.skills.nativeagent.composition

const val NATIVE_AGENT_COMPOSITION_CONTRACT_VERSION: String = "0.1"

object NativeAgentCompositionSchemaPaths {
  const val REPO_RELATIVE_PATH: String =
    "orchestration/contracts/native-agent-composition-schema.yaml"

  const val CLASSPATH_RESOURCE: String =
    "skillbill/infrastructure/contracts/native-agent-composition-schema.yaml"

  const val EXPECTED_SCHEMA_ID: String =
    "https://skill-bill.dev/contracts/native-agent-composition-schema.yaml"
}
