package skillbill.cli.experiment

import skillbill.cli.RecordingRuntimeDiagnostics
import skillbill.cli.core.CliRuntime
import skillbill.cli.model.CliRuntimeContext
import skillbill.ports.telemetry.transport.RemoteTransportPort
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ExperimentsCliRuntimeTest {
  @Test
  fun `experiments stats completes through CliRunState without usage help`() {
    val home = Files.createTempDirectory("skillbill-experiments-stats")
    val db = home.resolve("metrics.db")
    val result =
      CliRuntime.run(
        listOf("--db", db.toString(), "experiments", "stats"),
        CliRuntimeContext(userHome = home, environment = emptyMap()),
      )
    assertEquals(0, result.exitCode, result.stdout)
    assertTrue(result.stdout.contains("goal:"))
    assertTrue(result.stdout.contains("navigation:"))
    assertFalse(result.stdout.contains("Usage"))
  }

  @Test
  fun `experiments report for unknown pair returns empty stdout`() {
    val home = Files.createTempDirectory("skillbill-experiments-report")
    val db = home.resolve("metrics.db")
    val result =
      CliRuntime.run(
        listOf("--db", db.toString(), "experiments", "report", "nope"),
        CliRuntimeContext(userHome = home, environment = emptyMap()),
      )
    assertEquals(1, result.exitCode)
    assertEquals("", result.stdout)
    assertFalse(result.stderr.contains("at "))
    assertEquals(
      "Experiment navigation pair 'nope' is not available.",
      result.stderr.trim(),
    )
  }

  @Test
  fun `experiments report rejects yaml format at parse time with empty stdout`() {
    val home = Files.createTempDirectory("skillbill-experiments-format")
    val db = home.resolve("metrics.db")
    val result =
      CliRuntime.run(
        listOf(
          "--db",
          db.toString(),
          "experiments",
          "report",
          "nope",
          "--format",
          "yaml",
        ),
        CliRuntimeContext(userHome = home, environment = emptyMap()),
      )
    assertEquals(1, result.exitCode)
    assertTrue(result.stdout.isEmpty())
    assertTrue(result.stderr.contains("yaml"), result.stderr)
  }

  @Test
  fun `runtime failures use one line stderr diagnostics for validation probes`() {
    val home = Files.createTempDirectory("skillbill-cli-failure-probes")
    val db = home.resolve("metrics.db")
    val probes =
      listOf(
        listOf("--db", db.toString(), "import-review", "/path/that/does/not/exist", "--format", "json"),
        listOf("--db", db.toString(), "feature-task", "rejected-output", "--workflow", "wf-missing"),
        listOf("--db", db.toString(), "goal", "status", "NOPE-1", "--format", "json"),
        listOf(
          "--db",
          db.toString(),
          "feature-task",
          "repair-identity",
          "wfl-x",
          "SKILL-1",
          "/etc/passwd",
          "--reason",
          "r",
          "--format",
          "json",
        ),
      )

    probes.forEach { arguments ->
      val result = CliRuntime.run(arguments, CliRuntimeContext(userHome = home, environment = emptyMap()))
      assertEquals(1, result.exitCode, arguments.joinToString(" "))
      assertEquals("", result.stdout, arguments.joinToString(" "))
      assertTrue(result.stderr.isNotBlank(), arguments.joinToString(" "))
      if (arguments.contains("goal") || arguments.contains("/etc/passwd")) {
        assertTrue(result.stderr.startsWith("Usage:"), result.stderr)
      } else {
        assertFalse(result.stderr.contains("\n"), result.stderr)
        assertFalse(result.stderr.contains("at "), result.stderr)
      }
      if (arguments.contains("import-review")) {
        assertTrue(result.stderr.contains("/path/that/does/not/exist"), result.stderr)
      }
    }
  }

  @Test
  fun `unexpected command failure is diagnosed through the injected runtime diagnostics`() {
    val diagnostics = RecordingRuntimeDiagnostics()
    val result =
      CliRuntime.run(
        listOf("update-check", "--format", "json"),
        CliRuntimeContext(
          environment = emptyMap(),
          runtimeDiagnostics = diagnostics,
          requester = RemoteTransportPort { _, _, _, _ -> throw TransportFailure("transport exploded") },
        ),
      )

    assertEquals(1, result.exitCode)
    assertEquals("", result.stdout)
    assertEquals("TransportFailure: transport exploded", result.stderr)
    assertEquals(listOf("TransportFailure: transport exploded"), diagnostics.errors)
  }
}

private class TransportFailure(message: String) : Exception(message)
