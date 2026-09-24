package skillbill.telemetry

const val STATE_DIR_ENVIRONMENT_KEY: String = "SKILL_BILL_STATE_DIR"
const val CONFIG_ENVIRONMENT_KEY: String = "SKILL_BILL_CONFIG_PATH"
const val TELEMETRY_ENABLED_ENVIRONMENT_KEY: String = "SKILL_BILL_TELEMETRY_ENABLED"
const val TELEMETRY_LEVEL_ENVIRONMENT_KEY: String = "SKILL_BILL_TELEMETRY_LEVEL"
const val TELEMETRY_PROXY_URL_ENVIRONMENT_KEY: String = "SKILL_BILL_TELEMETRY_PROXY_URL"
const val TELEMETRY_PROXY_STATS_TOKEN_ENVIRONMENT_KEY: String = "SKILL_BILL_TELEMETRY_PROXY_STATS_TOKEN"
const val INSTALL_ID_ENVIRONMENT_KEY: String = "SKILL_BILL_INSTALL_ID"
const val TELEMETRY_BATCH_SIZE_ENVIRONMENT_KEY: String = "SKILL_BILL_TELEMETRY_BATCH_SIZE"
internal const val DEFAULT_TELEMETRY_PROXY_URL: String = "https://skill-bill-telemetry-proxy.skillbill.workers.dev"
const val DEFAULT_TELEMETRY_BATCH_SIZE: Int = 50
const val RESERVED_TEST_INSTALL_ID: String = "test-install-id"
const val TELEMETRY_PROXY_CONTRACT_VERSION: String = "2"

val telemetryLevels: List<String> = listOf("off", "anonymous", "full")
internal val remoteStatsWorkflows: List<String> =
  listOf("feature-task-prose", "bill-feature-verify", "feature-task-runtime")
