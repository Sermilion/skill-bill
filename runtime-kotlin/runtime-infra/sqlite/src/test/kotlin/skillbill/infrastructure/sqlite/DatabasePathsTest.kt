package skillbill.infrastructure.sqlite

import skillbill.infrastructure.sqlite.core.DatabaseRuntime
import skillbill.infrastructure.sqlite.core.DatabasePaths
import java.nio.file.Files
import java.nio.file.Path
import java.sql.Connection
import java.sql.SQLException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DatabasePathsTest {
  @Test
  fun `cli path overrides environment and default path`() {
    val resolved =
      DatabaseRuntime.resolveDbPath(
        cliValue = "./custom/metrics.db",
        environment = mapOf(DatabasePaths.DB_ENVIRONMENT_KEY to "/tmp/env-metrics.db"),
        userHome = Path.of("/tmp/home"),
      )

    assertEquals(Path.of("./custom/metrics.db").toAbsolutePath().normalize(), resolved)
  }

  @Test
  fun `environment path overrides default path`() {
    val resolved =
      DatabaseRuntime.resolveDbPath(
        cliValue = null,
        environment = mapOf(DatabasePaths.DB_ENVIRONMENT_KEY to "~/metrics.db"),
        userHome = Path.of("/tmp/home"),
      )

    assertEquals(Path.of("/tmp/home/metrics.db"), resolved)
  }

  @Test
  fun `default path resolves under skill bill home`() {
    val resolved =
      DatabaseRuntime.resolveDbPath(
        cliValue = null,
        environment = emptyMap(),
        userHome = Path.of("/tmp/home"),
      )

    assertEquals(Path.of("/tmp/home/.skill-bill/review-metrics.db"), resolved)
  }

  @Test
  fun `openReadDb migrates a zero-byte working-directory database instead of reporting it empty`() {
    val workingDir = Files.createTempDirectory("runtime-kotlin-zero-byte-store")
    val dbPath = workingDir.resolve(".skill-bill").resolve("review-metrics.db")
    Files.createDirectories(dbPath.parent)
    Files.createFile(dbPath)
    assertEquals(0, Files.size(dbPath), "The regression starts from a genuinely zero-byte file.")

    listOf(
      DatabaseRuntime.resolveDbPath(cliValue = dbPath.toString(), environment = emptyMap(), userHome = workingDir),
      DatabaseRuntime.resolveDbPath(
        cliValue = null,
        environment = mapOf(DatabasePaths.DB_ENVIRONMENT_KEY to dbPath.toString()),
        userHome = workingDir,
      ),
    ).forEach { resolved ->
      assertEquals(dbPath.toAbsolutePath().normalize(), resolved)
      DatabaseRuntime.openReadDb(cliValue = resolved.toString(), environment = emptyMap(), userHome = workingDir).use { open ->
        assertTrue(
          tableNames(open.connection).containsAll(setOf("review_runs", "findings", "telemetry_outbox")),
          "A schema-less file must be migrated to schema-complete, not reported as an empty store.",
        )
      }
    }
  }

  @Test
  fun `openReadDb on an absent path still bootstraps a schema-complete database`() {
    val workingDir = Files.createTempDirectory("runtime-kotlin-absent-store")
    val dbPath = workingDir.resolve("review-metrics.db")

    DatabaseRuntime.openReadDb(cliValue = dbPath.toString(), environment = emptyMap(), userHome = workingDir).use { open ->
      assertTrue(tableNames(open.connection).containsAll(setOf("review_runs", "findings")))
    }
  }

  @Test
  fun `openReadDb keeps a schema-complete database read-only`() {
    val workingDir = Files.createTempDirectory("runtime-kotlin-readonly-store")
    val dbPath = workingDir.resolve("review-metrics.db")
    DatabaseRuntime.ensureDatabase(dbPath).close()

    DatabaseRuntime.openReadDb(cliValue = dbPath.toString(), environment = emptyMap(), userHome = workingDir).use { open ->
      assertFailsWith<SQLException>("An already-complete store must not gain write capability.") {
        open.connection.createStatement().use { statement ->
          statement.executeUpdate("INSERT INTO review_runs (review_run_id, routed_skill) VALUES ('rvw-x', 's')")
        }
      }
    }
  }

  private fun tableNames(connection: Connection): Set<String> = connection.createStatement().use { statement ->
    statement.executeQuery("SELECT name FROM sqlite_master WHERE type = 'table'").use { rows ->
      buildSet { while (rows.next()) add(rows.getString("name")) }
    }
  }
}
