package skillbill.mcp.telemetry

const val TELEMETRY_EVENT_CONTRACT_VERSION: String = "1.11.0"

object TelemetryEventSchemaPaths {

  const val REPO_RELATIVE_PATH: String =
    "orchestration/contracts/telemetry-event-schema.yaml"

  const val CLASSPATH_RESOURCE: String =
    "skillbill/infrastructure/fs/contracts/telemetry-event-schema.yaml"

  const val EXPECTED_SCHEMA_ID: String =
    "https://skill-bill.dev/contracts/telemetry-event-schema.yaml"
}
