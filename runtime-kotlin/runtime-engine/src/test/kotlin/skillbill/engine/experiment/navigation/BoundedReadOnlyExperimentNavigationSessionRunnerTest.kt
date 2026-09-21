package skillbill.engine.experiment.navigation

import skillbill.error.shellcontent.ExperimentIsolationCapabilityRefusalError
import skillbill.ports.experiment.navigation.ExperimentNavigationDecisionAdapter
import skillbill.ports.experiment.navigation.ExperimentNavigationSessionRequest
import skillbill.ports.experiment.navigation.ExperimentNavigationTerminalOutcome
import skillbill.ports.experiment.navigation.model.ExperimentNavigationDecision
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BoundedReadOnlyExperimentNavigationSessionRunnerTest {
  @Test
  fun `read only runner records direct evidence and never exposes labels`() {
    val root = Files.createTempDirectory("navigation-snapshot")
    Files.createDirectories(root.resolve("src"))
    Files.writeString(root.resolve("src/Feature.kt"), "fun feature() = true")

    val result = BoundedReadOnlyExperimentNavigationSessionRunner(
      ExperimentNavigationDecisionAdapter { listOf(ExperimentNavigationDecision.Search("feature")) },
    ).runSession(
      ExperimentNavigationSessionRequest(
        pairId = "pair",
        armId = "control",
        repoRoot = root,
        frozenSpecBytes = "spec".toByteArray(),
        acceptanceCriteria = listOf("feature"),
        treatmentEnabled = false,
      ),
    )

    assertEquals(ExperimentNavigationTerminalOutcome.SEARCH_COMPLETED, result.outcome)
    assertEquals(listOf("src/Feature.kt"), result.deliveredPaths)
    assertEquals(listOf("src/Feature.kt"), result.shortlistedPaths)
    assertEquals(listOf("src/Feature.kt"), result.readReceipts.map { it.path })
    assertEquals(listOf("search"), result.readReceipts.map { it.purpose })
    assertEquals(1, result.attemptCount)
    assertFalse(result.labelCoverage.precisionAvailable)
    assertTrue(result.labelCoverage.totalCriteria == 1)
    assertTrue(result.restrictedBaseline)
  }

  @Test
  fun `model decision adapter can jump directly to evidence without parent traversal`() {
    val root = Files.createTempDirectory("navigation-direct-read")
    Files.createDirectories(root.resolve("src"))
    Files.writeString(root.resolve("src/Feature.kt"), "fun feature() = true")
    val runner = BoundedReadOnlyExperimentNavigationSessionRunner(
      ExperimentNavigationDecisionAdapter { context ->
        assertEquals(root.toAbsolutePath().normalize(), context.repoRoot)
        listOf(ExperimentNavigationDecision.Read("src/Feature.kt"), ExperimentNavigationDecision.Complete)
      },
    )

    val result = runner.runSession(
      ExperimentNavigationSessionRequest(
        pairId = "pair",
        armId = "control",
        repoRoot = root,
        frozenSpecBytes = "spec".toByteArray(),
        acceptanceCriteria = listOf("feature"),
        treatmentEnabled = false,
      ),
    )

    assertEquals(listOf("src/Feature.kt"), result.readReceipts.map { it.path })
    assertEquals(listOf("direct_read"), result.readReceipts.map { it.purpose })
  }

  @Test
  fun `model decision adapter cannot escape the snapshot`() {
    val root = Files.createTempDirectory("navigation-escape")
    val runner = BoundedReadOnlyExperimentNavigationSessionRunner(
      ExperimentNavigationDecisionAdapter {
        listOf(ExperimentNavigationDecision.Read("../outside.txt"))
      },
    )

    assertFailsWith<ExperimentIsolationCapabilityRefusalError> {
      runner.runSession(
        ExperimentNavigationSessionRequest(
          pairId = "pair",
          armId = "control",
          repoRoot = root,
          frozenSpecBytes = "spec".toByteArray(),
          acceptanceCriteria = listOf("feature"),
          treatmentEnabled = false,
        ),
      )
    }
  }
}
