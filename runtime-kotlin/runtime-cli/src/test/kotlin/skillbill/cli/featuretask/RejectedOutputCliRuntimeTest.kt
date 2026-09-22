package skillbill.cli.featuretask

import skillbill.application.diagnostics.RejectedOutputDiagnosticService
import skillbill.application.diagnostics.model.RejectedOutputDiagnosticRequest
import skillbill.cli.core.CliRuntime
import skillbill.cli.model.CliRuntimeContext
import skillbill.infrastructure.sqlite.sqliteDatabaseSessionFactory
import java.nio.file.Files
import java.time.Clock
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class RejectedOutputCliRuntimeTest {
  @Test
  fun `rejected-output-cleanup completes through CliRunState without usage help`() {
    val home = Files.createTempDirectory("skillbill-rejected-cleanup")
    val db = home.resolve("metrics.db")
    val result =
      CliRuntime.run(
        listOf(
          "--db",
          db.toString(),
          "feature-task",
          "rejected-output-cleanup",
          "--workflow",
          "wf-missing",
        ),
        CliRuntimeContext(userHome = home, environment = emptyMap()),
      )
    assertEquals(0, result.exitCode)
    assertEquals("deleted=0\n", result.stdout)
    assertFalse(result.stdout.contains("Usage"))
  }

  @Test
  fun `rejected-output raw retrieval keeps stored bytes through the cli result`() {
    val home = Files.createTempDirectory("skillbill-rejected-raw")
    val db = home.resolve("metrics.db")
    val raw = byteArrayOf(0xff.toByte(), 0, 13, 10, 42)
    val database =
      sqliteDatabaseSessionFactory(userHome = home, dbPathOverride = db.toString(), environment = emptyMap())
    database.transaction { unitOfWork ->
      RejectedOutputDiagnosticService(
        repository = requireNotNull(unitOfWork.rejectedOutputDiagnostics),
        permissions = requireNotNull(unitOfWork.rejectedOutputDiagnosticPermissions),
        metadataValidator = { },
        clock = Clock.systemUTC(),
      ).record(
        RejectedOutputDiagnosticRequest(
          workflowId = "wf-raw",
          phaseId = "implement",
          attempt = 1,
          rule = "schema",
          path = "$.status",
          reason = "invalid",
          agentId = "codex",
          model = "gpt",
          rawResponse = raw,
        ),
      )
    }

    val result =
      CliRuntime.run(
        listOf(
          "--db",
          db.toString(),
          "feature-task",
          "rejected-output",
          "--workflow",
          "wf-raw",
          "--phase",
          "implement",
          "--attempt",
          "1",
          "--raw-output",
        ),
        CliRuntimeContext(userHome = home, environment = emptyMap()),
      )

    assertEquals(0, result.exitCode)
    assertContentEquals(raw, requireNotNull(result.rawStdout))
    assertEquals("", result.stdout)
    assertFalse(result.stdout.contains("Usage"))
  }
}
