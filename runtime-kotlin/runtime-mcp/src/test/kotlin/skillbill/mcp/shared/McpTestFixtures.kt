package skillbill.mcp.shared

import skillbill.contracts.JsonCodec
import skillbill.infrastructure.sqlite.withLifecycleTelemetryStore
import skillbill.telemetry.CONFIG_ENVIRONMENT_KEY
import skillbill.telemetry.TELEMETRY_PROXY_URL_ENVIRONMENT_KEY
import skillbill.telemetry.model.GoalFinishedRecord
import skillbill.telemetry.model.GoalStartedRecord
import skillbill.telemetry.model.GoalSubtaskFinishedRecord
import java.nio.file.Files
import java.nio.file.Path
import java.sql.Connection
import kotlin.test.assertEquals
import kotlin.test.assertTrue

internal const val SAMPLE_REVIEW: String =
  """
  Routed to: bill-kotlin-code-review
  Review session ID: rvs-20260402-001
  Review run ID: rvw-20260402-001
  Detected review scope: unstaged changes
  Detected stack: kotlin
  Execution mode: inline
  Specialist reviews: architecture, testing, architecture

  ### 2. Risk Register
  - [F-001] Major | High | README.md:12 | README wording is stale after the routing change.
  - [F-002] Minor | Medium | install.sh:88 | Installer prompt wording is inconsistent with the new flow.
  """

internal const val ZERO_FINDING_REVIEW: String =
  """
  Routed to: bill-kmp-code-review
  Review session ID: rvs-20260427-empty
  Review run ID: rvw-20260427-empty
  Detected review scope: unstaged changes
  Detected stack: kmp
  Execution mode: inline

  ### 2. Risk Register
  No findings.
  """

internal const val TEST_TELEMETRY_PROXY_URL = "http://127.0.0.1:9/skill-bill-test-telemetry"

internal fun Map<String, String>.withTestTelemetryProxy(): Map<String, String> =
  this + (TELEMETRY_PROXY_URL_ENVIRONMENT_KEY to TEST_TELEMETRY_PROXY_URL)

internal fun enabledTelemetryEnvironment(tempDir: Path): Map<String, String> =
  telemetryEnvironment(tempDir, level = "anonymous").withTestTelemetryProxy()

internal fun disabledTelemetryEnvironment(tempDir: Path): Map<String, String> =
  telemetryEnvironment(tempDir, level = "off")

private fun telemetryEnvironment(
  tempDir: Path,
  level: String,
): Map<String, String> {
  val configPath = tempDir.resolve("config.json")
  Files.writeString(
    configPath,
    """
    {
      "install_id": "test-install-id",
      "telemetry": {
        "level": "$level",
        "proxy_url": "",
        "batch_size": 50
      }
    }
    """.trimIndent() + "\n",
  )
  return mapOf(
    "SKILL_BILL_REVIEW_DB" to tempDir.resolve("metrics.db").toString(),
    CONFIG_ENVIRONMENT_KEY to configPath.toString(),
  )
}

internal fun decodeJsonObject(rawJson: String): Map<String, Any?> {
  val parsed = JsonCodec.parseObjectOrNull(rawJson)
  require(parsed != null) { "Expected JSON object but got: $rawJson" }
  val decoded = JsonCodec.anyToStringAnyMap(JsonCodec.jsonElementToValue(parsed))
  require(decoded != null) { "Expected decoded JSON object but got: $rawJson" }
  return decoded
}

internal fun goldenJson(
  fileName: String,
  vararg replacements: Pair<String, String>,
): String {
  var expected =
    Files.readString(Path.of("src/test/resources/golden").resolve(fileName))
      .replace("\r\n", "\n")
      .trim()
  replacements.forEach { (placeholder, value) ->
    expected = expected.replace(placeholder, value)
  }
  return expected
}

internal fun assertGoldenPayload(
  fileName: String,
  payload: Map<String, *>,
  vararg replacements: Pair<String, String>,
) {
  val normalizedPayload = decodeJsonObject(JsonCodec.mapToJsonString(payload.mapValues { (_, value) -> value }))
  assertEquals(decodeJsonObject(goldenJson(fileName, *replacements)), normalizedPayload)
}

internal fun scalarInt(
  connection: Connection,
  sql: String,
): Int =
  connection.createStatement().use { statement ->
    statement.executeQuery(sql).use { resultSet ->
      resultSet.next()
      resultSet.getInt(1)
    }
  }

internal fun scalarString(
  connection: Connection,
  sql: String,
): String =
  connection.createStatement().use { statement ->
    statement.executeQuery(sql).use { resultSet ->
      resultSet.next()
      resultSet.getString(1)
    }
  }

internal fun assertMatchesPattern(
  pattern: Regex,
  value: String,
  label: String,
) {
  assertTrue(pattern.matches(value), "Expected $label to match ${pattern.pattern} but got $value")
}

internal fun assertWorkflowIdShape(
  workflowId: String,
  prefix: String,
) {
  assertMatchesPattern(Regex("""^$prefix-\d{8}-\d{6}-[a-z0-9]{4}$"""), workflowId, "workflow_id")
}

internal fun assertSqliteTimestampShape(
  timestamp: String,
  label: String,
) {
  assertMatchesPattern(Regex("""^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}Z$"""), timestamp, label)
}

internal fun pathIsUnderRoot(
  path: Path,
  root: Path,
): Boolean {
  val normalizedPath = path.toAbsolutePath().normalize()
  var existingAncestor = normalizedPath
  while (!Files.exists(existingAncestor)) {
    existingAncestor = existingAncestor.parent ?: return false
  }
  val canonicalAncestor = existingAncestor.toRealPath()
  val canonicalPath =
    canonicalAncestor.resolve(existingAncestor.relativize(normalizedPath)).normalize()
  return canonicalPath.startsWith(root.toRealPath())
}

internal fun seedGoalBlockedRun(
  dbPath: Path,
  workflowId: String,
) {
  withLifecycleTelemetryStore(dbPath.parent, dbPath) { store ->
    store.goalStarted(
      GoalStartedRecord(
        issueKey = "SKILL-66",
        featureName = "goal telemetry",
        workflowId = workflowId,
        subtaskTotal = 1,
        resumed = false,
        startedAt = "2026-06-05T10:00:00Z",
        mode = "runtime",
      ),
      level = "full",
    )
    store.goalSubtaskFinished(
      GoalSubtaskFinishedRecord(
        issueKey = "SKILL-66",
        workflowId = workflowId,
        subtaskId = 1,
        subtaskName = "implement",
        status = "blocked",
        startedAt = "2026-06-05T10:00:00Z",
        finishedAt = "2026-06-05T10:05:00Z",
        durationMs = 300_000,
        attemptCount = 1,
        blockedReason = "test failure",
      ),
      "full",
    )
    store.goalFinished(
      GoalFinishedRecord(
        issueKey = "SKILL-66",
        workflowId = workflowId,
        status = "blocked",
        startedAt = "2026-06-05T10:00:00Z",
        finishedAt = "2026-06-05T10:10:00Z",
        durationMs = 600_000,
        subtasksComplete = 0,
        subtasksBlocked = 1,
        subtasksSkipped = 0,
        mode = "runtime",
      ),
      level = "full",
    )
  }
}
