package skillbill.engine.featuretask.validation

import skillbill.infrastructure.workflow.github.GitHubPullRequestCheckDiscovery
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ReadinessCheckSelectionTest {
  @Test
  fun `PR 389 plugin hole selects plugin check not pack collect-all`() {
    val selection =
      ReadinessCheckSelection(
        GitHubPullRequestCheckDiscovery(),
      ).select(
        repositoryRoot,
        changedPaths = listOf("intellij-plugin/src/main/kotlin/Foo.kt"),
      )
    val checks = assertIs<ReadinessCheckSelectionResult.Selected>(selection).checks
    assertTrue(checks.any { it.checkId.startsWith("plugin-ci:") })
    assertTrue(
      checks.any { it.command == "(cd intellij-plugin && ./gradlew clean check --no-build-cache)" },
    )
    assertTrue(checks.none { it.checkId == READINESS_PACK_COLLECT_ALL_CHECK_ID })
  }

  @Test
  fun `kotlin-only runtime-kotlin dirt does not select pack collect-all`() {
    val selection =
      ReadinessCheckSelection(
        GitHubPullRequestCheckDiscovery(),
      ).select(
        validationGateTestRepoRoot,
        changedPaths = listOf("runtime-kotlin/runtime-engine/src/Foo.kt"),
      )
    val checks = assertIs<ReadinessCheckSelectionResult.Selected>(selection).checks
    assertTrue(checks.none { it.checkId == READINESS_PACK_COLLECT_ALL_CHECK_ID })
  }

  @Test
  fun `history and run evidence do not invalidate selected project checks`() {
    val selection =
      ReadinessCheckSelection(
        GitHubPullRequestCheckDiscovery(),
      )
    val selected =
      assertIs<ReadinessCheckSelectionResult.Selected>(
        selection.select(
          validationGateTestRepoRoot,
          listOf("runtime-kotlin/runtime-engine/src/Foo.kt", "runtime-kotlin/agent/history.md"),
        ),
      ).checks
    assertEquals(
      emptySet(),
      selection.invalidatedCheckIds(
        selected,
        listOf("runtime-kotlin/agent/history.md", ".skill-bill/run-evidence/wf/receipt.json"),
      ),
    )
  }

  private val repositoryRoot: Path
    get() {
      var current = validationGateTestRepoRoot
      while (current.parent != null) {
        if (Files.isDirectory(current.resolve(".github/workflows"))) return current
        current = current.parent
      }
      error("Could not locate repository root from $validationGateTestRepoRoot")
    }
}
