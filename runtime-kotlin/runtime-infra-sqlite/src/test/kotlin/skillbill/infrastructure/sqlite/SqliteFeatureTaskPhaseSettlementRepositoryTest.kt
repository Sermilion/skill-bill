package skillbill.infrastructure.sqlite

import skillbill.contracts.JsonCodec
import skillbill.contracts.workflow.ValidationEvidencePayloadKeys
import skillbill.infrastructure.sqlite.core.DatabaseRuntime
import skillbill.model.EnvironmentContext
import skillbill.ports.featuretask.model.FeatureTaskPhaseSettlement
import skillbill.ports.featuretask.model.FeatureTaskPhaseSettlementKind
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeValidationCommandResult
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeValidationEvidence
import java.nio.file.Files
import java.sql.Connection
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SqliteFeatureTaskPhaseSettlementRepositoryTest {
  @Test
  fun `migration v35 creates settlement table and supports upsert find delete`() {
    val dbPath = Files.createTempDirectory("phase-settlement").resolve("metrics.db")
    DatabaseRuntime.ensureDatabase(dbPath).use { connection ->
      assertTrue(tableExists(connection, "feature_task_phase_settlements"))
      assertNotNull(
        migrationRows(connection).singleOrNull { row ->
          row.version == 35 && row.name == "add-feature-task-phase-settlements"
        },
      )
    }
    val repo = SqliteFeatureTaskPhaseSettlementRepository(
      SQLiteDatabaseSessionFactory(EnvironmentContext(dbPathOverride = dbPath.toString(), environment = emptyMap())),
    )
    val settlement = FeatureTaskPhaseSettlement(
      workflowId = "wftr-1",
      phaseId = "implement",
      attempt = 1,
      kind = FeatureTaskPhaseSettlementKind.Complete,
      envelopeJson = """{"status":"completed","produced_outputs":{"value":"x"}}""",
      recordedAt = Instant.now().toString(),
    )
    repo.upsert(settlement)
    assertEquals(FeatureTaskPhaseSettlementKind.Complete, repo.find("wftr-1", "implement", 1)?.kind)
    assertTrue(repo.delete("wftr-1", "implement", 1))
    assertNull(repo.find("wftr-1", "implement", 1))
    assertFalse(repo.delete("wftr-1", "implement", 1))
  }

  @Test
  fun `validation evidence command and exit code survive sqlite round trip`() {
    val dbPath = Files.createTempDirectory("phase-settlement-evidence").resolve("metrics.db")
    DatabaseRuntime.ensureDatabase(dbPath).close()
    val repo = SqliteFeatureTaskPhaseSettlementRepository(
      SQLiteDatabaseSessionFactory(EnvironmentContext(dbPathOverride = dbPath.toString(), environment = emptyMap())),
    )
    val evidence = FeatureTaskRuntimeValidationEvidence(
      listOf(FeatureTaskRuntimeValidationCommandResult("./gradlew check", 0)),
    )
    val envelopeJson = JsonCodec.mapToJsonString(
      mapOf(
        "status" to "completed",
        "produced_outputs" to mapOf(
          "value" to JsonCodec.mapToJsonString(
            mapOf(ValidationEvidencePayloadKeys.VALIDATION_EVIDENCE to evidence.toArtifactMap()),
          ),
        ),
      ),
    )
    repo.upsert(
      FeatureTaskPhaseSettlement(
        workflowId = "wftr-evidence",
        phaseId = "implement",
        attempt = 1,
        kind = FeatureTaskPhaseSettlementKind.Complete,
        envelopeJson = envelopeJson,
        recordedAt = Instant.now().toString(),
      ),
    )
    val stored = requireNotNull(repo.find("wftr-evidence", "implement", 1))
    val produced = JsonCodec.anyToStringAnyMap(
      JsonCodec.parseObjectOrNull(stored.envelopeJson)
        ?.let(JsonCodec::jsonElementToValue)
        ?.let(JsonCodec::anyToStringAnyMap)
        ?.get("produced_outputs"),
    )
    val value = (produced?.get("value") as? String)
      ?.let(JsonCodec::parseObjectOrNull)
      ?.let(JsonCodec::jsonElementToValue)
      ?.let(JsonCodec::anyToStringAnyMap)
    val results = JsonCodec.anyToStringAnyMap(value?.get(ValidationEvidencePayloadKeys.VALIDATION_EVIDENCE))
      ?.get(ValidationEvidencePayloadKeys.RESULTS) as? List<*>
    val first = results?.first() as? Map<*, *>
    assertEquals("./gradlew check", first?.get(ValidationEvidencePayloadKeys.COMMAND))
    assertEquals(0, first?.get(ValidationEvidencePayloadKeys.EXIT_CODE))
  }

  @Test
  fun `unknown settlement kind survives durable round trip`() {
    val dbPath = Files.createTempDirectory("phase-settlement-unknown").resolve("metrics.db")
    DatabaseRuntime.ensureDatabase(dbPath).close()
    val repo = SqliteFeatureTaskPhaseSettlementRepository(
      SQLiteDatabaseSessionFactory(EnvironmentContext(dbPathOverride = dbPath.toString(), environment = emptyMap())),
    )
    val unknown = FeatureTaskPhaseSettlementKind.Unknown("future_kind")
    repo.upsert(
      FeatureTaskPhaseSettlement(
        workflowId = "wftr-unknown",
        phaseId = "implement",
        attempt = 1,
        kind = unknown,
        envelopeJson = "{\"status\":\"completed\"}",
        recordedAt = Instant.now().toString(),
      ),
    )

    assertEquals(unknown, repo.find("wftr-unknown", "implement", 1)?.kind)
  }

  private fun tableExists(connection: Connection, name: String): Boolean =
    connection.createStatement().use { statement ->
      statement.executeQuery(
        "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = '$name'",
      ).use { it.next() }
    }

  private data class MigrationRow(val version: Int, val name: String)

  private fun migrationRows(connection: Connection): List<MigrationRow> =
    connection.createStatement().use { statement ->
      statement.executeQuery("SELECT version, name FROM schema_migrations ORDER BY version").use { rows ->
        buildList {
          while (rows.next()) {
            add(MigrationRow(rows.getInt("version"), rows.getString("name")))
          }
        }
      }
    }
}
