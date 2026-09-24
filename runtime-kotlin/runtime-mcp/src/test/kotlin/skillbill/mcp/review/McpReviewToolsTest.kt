package skillbill.mcp.review

import skillbill.cli.core.CliRuntime
import skillbill.cli.model.CliExecutionResult
import skillbill.cli.model.CliRuntimeContext
import skillbill.contracts.JsonCodec
import skillbill.infrastructure.sqlite.ensureTestDatabase
import skillbill.mcp.lifecycle.recordFeatureVerifyLifecycle
import skillbill.mcp.shared.McpRuntimeContext
import skillbill.mcp.shared.SAMPLE_REVIEW
import skillbill.mcp.shared.ZERO_FINDING_REVIEW
import skillbill.mcp.shared.assertGoldenPayload
import skillbill.mcp.shared.callToolError
import skillbill.mcp.shared.callToolPayload
import skillbill.mcp.shared.decodeJsonObject
import skillbill.mcp.shared.disabledTelemetryEnvironment
import skillbill.mcp.shared.enabledTelemetryEnvironment
import skillbill.mcp.shared.goldenJson
import skillbill.mcp.shared.scalarInt
import skillbill.mcp.shared.scalarString
import skillbill.mcp.shared.withTestTelemetryProxy
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

class McpReviewToolsTest {
  @Test
  fun `import review and feature stats preserve stable payloads`() {
    val tempDir = Files.createTempDirectory("skillbill-mcp-import")
    val context = McpRuntimeContext(environment = enabledTelemetryEnvironment(tempDir), userHome = tempDir)

    val importResult =
      context.callToolPayload("import_review", mapOf("review_text" to SAMPLE_REVIEW.trimIndent()))
    recordFeatureVerifyLifecycle(context)
    val verifyStats = context.callToolPayload("feature_verify_stats")
    val dbPath = tempDir.resolve("metrics.db").toAbsolutePath().normalize().toString()

    assertGoldenPayload("mcp-import-review.json", importResult, "<DB_PATH>" to dbPath)
    assertEquals("rvw-20260402-001", importResult["review_run_id"])
    assertEquals("rvs-20260402-001", importResult["review_session_id"])
    assertEquals(2, importResult["finding_count"])
    assertEquals("bill-kotlin-code-review", importResult["routed_skill"])
    assertEquals(featureVerifyStatsKeys(), verifyStats.keys)
    assertEquals(dbPath, verifyStats["db_path"])
    assertEquals("bill-feature-verify", verifyStats["workflow"])
    assertEquals(1, verifyStats["total_runs"])
    assertEquals(1, verifyStats["finished_runs"])
    assertEquals(1, verifyStats["feature_flag_audit_performed_runs"])
    assertEquals(1, verifyStats["runs_with_gaps_found"])
    assertEquals(1.0, (verifyStats["history_helpful_rate"] as Number).toDouble())
    val verifyHistoryRelevanceCounts = verifyStats["history_relevance_counts"] as Map<*, *>
    assertEquals(0, verifyHistoryRelevanceCounts["none"])
    assertEquals(1, verifyHistoryRelevanceCounts["low"])
  }

  @Test
  fun `standalone zero finding import emits review finished telemetry`() {
    val tempDir = Files.createTempDirectory("skillbill-mcp-zero-finding-import")
    val dbPath = tempDir.resolve("metrics.db")
    val context = McpRuntimeContext(environment = enabledTelemetryEnvironment(tempDir), userHome = tempDir)

    val importResult =
      context.callToolPayload("import_review", mapOf("review_text" to ZERO_FINDING_REVIEW.trimIndent()))

    assertEquals("rvw-20260427-empty", importResult["review_run_id"])
    assertEquals(0, importResult["finding_count"])
    ensureTestDatabase(dbPath).use { connection ->
      assertEquals(
        1,
        scalarInt(
          connection,
          "SELECT COUNT(*) FROM telemetry_outbox WHERE event_name = 'skillbill_review_finished'",
        ),
      )
      assertEquals(
        1,
        scalarInt(
          connection,
          """
          SELECT COUNT(*)
          FROM review_runs
          WHERE review_run_id = 'rvw-20260427-empty'
            AND review_finished_at IS NOT NULL
            AND review_finished_event_emitted_at IS NOT NULL
          """.trimIndent(),
        ),
      )
      val telemetryPayload =
        decodeJsonObject(
          scalarString(
            connection,
            "SELECT payload_json FROM telemetry_outbox WHERE event_name = 'skillbill_review_finished'",
          ),
        )
      assertEquals("rvw-20260427-empty", telemetryPayload["review_run_id"])
      assertEquals("rvs-20260427-empty", telemetryPayload["review_session_id"])
      assertEquals("bill-kmp-code-review", telemetryPayload["routed_skill"])
      assertEquals("kmp", telemetryPayload["review_platform"])
      assertEquals("kmp", telemetryPayload["platform_slug"])
      assertEquals("kmp", telemetryPayload["detected_stack"])
      assertEquals(false, telemetryPayload["fallback"])
      assertEquals("unstaged changes", telemetryPayload["review_scope"])
      assertEquals("inline", telemetryPayload["execution_mode"])
      assertEquals(0, telemetryPayload["total_findings"])
      assertEquals(0, telemetryPayload["accepted_findings"])
      assertEquals(0, telemetryPayload["unresolved_findings"])
      assertEquals(0.0, (telemetryPayload["accepted_rate"] as Number).toDouble())
      assertEquals(emptyList<Map<String, Any?>>(), telemetryPayload["accepted_finding_details"])
      assertEquals(emptyList<Map<String, Any?>>(), telemetryPayload["rejected_finding_details"])
      val learnings = telemetryPayload["learnings"] as Map<*, *>
      assertEquals(0, learnings["applied_count"])
      assertEquals("none", learnings["applied_summary"])
    }
  }

  @Test
  fun `triage orchestrated returns telemetry payload and suppresses outbox emission`() {
    val tempDir = Files.createTempDirectory("skillbill-mcp-orchestrated")
    val dbPath = tempDir.resolve("metrics.db")
    val context = McpRuntimeContext(environment = enabledTelemetryEnvironment(tempDir), userHome = tempDir)

    val importResult =
      context.callToolPayload(
        "import_review",
        mapOf("review_text" to SAMPLE_REVIEW.trimIndent(), "orchestrated" to true),
      )
    val triageResult =
      context.callToolPayload(
        "triage_findings",
        mapOf(
          "review_run_id" to "rvw-20260402-001",
          "decisions" to listOf("fix=[1,2]"),
          "orchestrated" to true,
        ),
      )

    val telemetryPayload = triageResult["telemetry_payload"] as Map<*, *>
    assertGoldenPayload(
      "mcp-import-review-orchestrated.json",
      importResult,
      "<DB_PATH>" to dbPath.toAbsolutePath().normalize().toString(),
    )
    assertGoldenPayload(
      "mcp-triage-findings-orchestrated.json",
      triageResult,
      "<DB_PATH>" to dbPath.toAbsolutePath().normalize().toString(),
      "<REVIEW_FINISHED_AT>" to telemetryPayload["review_finished_at"].toString(),
    )
    assertEquals("orchestrated", importResult["mode"])
    assertEquals("orchestrated", triageResult["mode"])
    assertEquals("bill-code-review", telemetryPayload["skill"])
    assertEquals(2, telemetryPayload["total_findings"])
    assertEquals(0, telemetryPayload["rejected_findings"])
    assertEquals(0.0, (telemetryPayload["rejected_rate"] as Number).toDouble())
    assertEquals("kotlin", telemetryPayload["platform_slug"])
    assertEquals("unstaged_changes", telemetryPayload["scope_type"])

    ensureTestDatabase(dbPath).use { connection ->
      assertEquals(
        0,
        scalarInt(
          connection,
          "SELECT COUNT(*) FROM telemetry_outbox WHERE event_name = 'skillbill_review_finished'",
        ),
      )
      assertEquals(
        1,
        scalarInt(
          connection,
          "SELECT orchestrated_run FROM review_runs WHERE review_run_id = 'rvw-20260402-001'",
        ),
      )
    }
  }

  @Test
  fun `resolve learnings skips when telemetry is disabled`() {
    val tempDir = Files.createTempDirectory("skillbill-mcp-disabled")
    val context = McpRuntimeContext(environment = disabledTelemetryEnvironment(tempDir), userHome = tempDir)

    val result = context.callToolPayload("resolve_learnings")

    assertEquals("skipped", result["status"])
    assertEquals("telemetry is disabled", result["reason"])
    assertEquals("none", result["applied_learnings"])
    assertEquals(emptyList<Map<String, Any?>>(), result["learnings"])
  }

  @Test
  fun `resolve learnings returns stable payload when telemetry is enabled`() {
    val tempDir = Files.createTempDirectory("skillbill-mcp-learnings")
    val env = enabledTelemetryEnvironment(tempDir)
    seedLearningScenario(tempDir, env)

    val result =
      McpRuntimeContext(environment = env, userHome = tempDir).callToolPayload(
        "resolve_learnings",
        mapOf("skill" to "bill-kotlin-code-review", "review_session_id" to "rvs-20260402-001"),
      )

    assertEquals("bill-kotlin-code-review", result["skill_name"])
    assertEquals(
      decodeJsonObject(
        goldenJson(
          "mcp-resolve-learnings.json",
          "<DB_PATH>" to tempDir.resolve("metrics.db").toAbsolutePath().normalize().toString(),
        ),
      ),
      result,
    )
    assertEquals(listOf("skill", "repo", "global"), result["scope_precedence"])
    assertEquals("L-001", result["applied_learnings"])
    assertEquals("rvs-20260402-001", result["review_session_id"])
    val entries = result["learnings"] as List<*>
    val firstEntry = entries.first() as Map<*, *>
    assertEquals("L-001", firstEntry["reference"])
    assertEquals("skill", firstEntry["scope"])
    assertEquals("Keep wording aligned", firstEntry["title"])
  }

  @Test
  fun `add learning promotes a rejected finding and resolve learnings returns it`() {
    val tempDir = Files.createTempDirectory("skillbill-mcp-add-learning")
    val context = McpRuntimeContext(environment = enabledTelemetryEnvironment(tempDir), userHome = tempDir)
    context.callToolPayload("import_review", mapOf("review_text" to SAMPLE_REVIEW.trimIndent()))
    context.callToolPayload(
      "triage_findings",
      mapOf(
        "review_run_id" to "rvw-20260402-001",
        "decisions" to listOf("1 fix", "2 false-positive - Prompt wording is intentional."),
      ),
    )

    val added = context.callToolPayload("add_learning", addLearningArguments(findingId = "F-002"))
    val resolved =
      context.callToolPayload(
        "resolve_learnings",
        mapOf("repo" to "acme/repo", "skill" to "bill-kotlin-code-review"),
      )

    assertEquals("repo", added["scope"])
    assertEquals("active", added["status"])
    val resolvedEntries = resolved["learnings"] as List<*>
    assertEquals(
      listOf(added["reference"]),
      resolvedEntries.map { entry -> requireNotNull(JsonCodec.anyToStringAnyMap(entry))["reference"] },
    )

    val notRejected = context.callToolError("add_learning", addLearningArguments(findingId = "F-001"))
    assertContains(notRejected["error"].toString(), "has no rejected outcome")

    val schemaViolation =
      context.callToolError(
        "add_learning",
        addLearningArguments(findingId = "F-002") + ("unexpected" to true),
      )
    assertContains(schemaViolation["error"].toString(), "unexpected")
  }

  @Test
  fun `mcp import review honors overridden user home when resolving telemetry config`() {
    val tempDir = Files.createTempDirectory("skillbill-mcp-user-home")
    Files.createDirectories(tempDir.resolve(".skill-bill"))
    Files.writeString(
      tempDir.resolve(".skill-bill").resolve("config.json"),
      """
      {
        "install_id": "test-install-id",
        "telemetry": {
          "level": "anonymous",
          "proxy_url": "",
          "batch_size": 50
        }
      }
      """.trimIndent() + "\n",
    )
    val env =
      mapOf("SKILL_BILL_REVIEW_DB" to tempDir.resolve("metrics.db").toString())
        .withTestTelemetryProxy()

    val result =
      McpRuntimeContext(environment = env, userHome = tempDir)
        .callToolPayload("import_review", mapOf("review_text" to SAMPLE_REVIEW.trimIndent()))

    assertEquals("rvw-20260402-001", result["review_run_id"])
    assertEquals(2, result["finding_count"])
    assertEquals("bill-kotlin-code-review", result["routed_skill"])
  }
}

private fun addLearningArguments(findingId: String): Map<String, Any?> =
  mapOf(
    "scope" to "repo",
    "scope_key" to "acme/repo",
    "title" to "Keep the installer prompt wording",
    "rule_text" to "Do not flag the installer prompt wording as inconsistent.",
    "source_review_run_id" to "rvw-20260402-001",
    "source_finding_id" to findingId,
  )

private fun featureVerifyStatsKeys(): Set<String> =
  setOf(
    "workflow",
    "total_runs",
    "finished_runs",
    "in_progress_runs",
    "completion_status_counts",
    "audit_result_counts",
    "rollout_relevant_runs",
    "rollout_relevant_rate",
    "feature_flag_audit_performed_runs",
    "feature_flag_audit_performed_rate",
    "history_read_runs",
    "history_read_rate",
    "history_relevant_runs",
    "history_relevant_rate",
    "history_helpful_runs",
    "history_helpful_rate",
    "history_relevance_counts",
    "history_helpfulness_counts",
    "runs_with_gaps_found",
    "average_acceptance_criteria_count",
    "average_review_iterations",
    "average_duration_seconds",
    "db_path",
  )

private fun seedLearningScenario(
  tempDir: Path,
  env: Map<String, String>,
) {
  val dbPath = tempDir.resolve("metrics.db")
  assertMcpCliSuccess(
    CliRuntime.run(
      listOf("--db", dbPath.toString(), "import-review", "-", "--format", "json"),
      CliRuntimeContext(environment = env, userHome = tempDir, stdinText = SAMPLE_REVIEW.trimIndent()),
    ),
  )
  assertMcpCliSuccess(
    CliRuntime.run(
      listOf(
        "--db",
        dbPath.toString(),
        "triage",
        "--run-id",
        "rvw-20260402-001",
        "--decision",
        "2 reject - intentional",
        "--format",
        "json",
      ),
      CliRuntimeContext(environment = env, userHome = tempDir),
    ),
  )
  assertMcpCliSuccess(
    CliRuntime.run(
      listOf(
        "--db",
        dbPath.toString(),
        "learnings",
        "add",
        "--scope",
        "skill",
        "--scope-key",
        "bill-kotlin-code-review",
        "--title",
        "Keep wording aligned",
        "--rule",
        "Update the installer prompt when routing text changes.",
        "--from-run",
        "rvw-20260402-001",
        "--from-finding",
        "F-002",
        "--format",
        "json",
      ),
      CliRuntimeContext(environment = env, userHome = tempDir),
    ),
  )
}

private fun assertMcpCliSuccess(result: CliExecutionResult) {
  assertEquals(0, result.exitCode, result.stdout)
}
