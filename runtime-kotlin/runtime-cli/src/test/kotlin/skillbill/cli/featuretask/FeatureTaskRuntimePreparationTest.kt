package skillbill.cli.featuretask

import skillbill.cli.core.CliRuntime
import skillbill.cli.model.CliRuntimeContext
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

class FeatureTaskRuntimePreparationTest {
  @Test
  fun `invalid operator and conflicting review options fail before creating a workflow`() {
    val home = Files.createTempDirectory("skillbill-feature-task-preparation")
    val db = home.resolve("metrics.db")
    val context = CliRuntimeContext(userHome = home, environment = emptyMap())
    val invocations =
      listOf(
        listOf(
          "--db",
          db.toString(),
          "feature-task",
          "SKILL-348",
          "--operator-decision",
          "unknown",
        ),
        listOf(
          "--db",
          db.toString(),
          "feature-task",
          "SKILL-348",
          "--code-review-mode",
          "inline",
          "--code-review-mode",
          "auto",
        ),
      )

    invocations.forEach { arguments ->
      val result = CliRuntime.run(arguments, context)

      assertEquals(1, result.exitCode, result.stderr)
      assertContains(result.stderr, "Error:")
    }

    val database =
      sqliteDatabaseSessionFactory(userHome = home, dbPathOverride = db.toString(), environment = emptyMap())
    database.transaction { unitOfWork ->
      assertEquals(emptyList(), unitOfWork.workflowStates.listFeatureTaskRuntimeWorkflows())
    }
  }
}
