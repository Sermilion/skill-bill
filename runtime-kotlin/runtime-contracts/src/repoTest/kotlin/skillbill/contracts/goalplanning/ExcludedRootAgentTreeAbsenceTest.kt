package skillbill.contracts.goalplanning

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ExcludedRootAgentTreeAbsenceTest {
  @Test
  fun `no working tree path under an excluded root contains an agent segment`() {
    val repoRoot = assertNotNull(repoRoot(), "this invariant is asserted against the checked-in working tree")

    val offenders = workingTreeDirectories(repoRoot).filter { path ->
      GoalPlanningDiscoveryExclusions.isExcluded(path) && path.split("/").contains("agent")
    }

    assertEquals(emptyList(), offenders, "delete these agent/ trees; excluded roots carry no boundary memory")
  }

  @Test
  fun `boundary writer skills forbid agent trees under excluded roots`() {
    val repoRoot = assertNotNull(repoRoot(), "this invariant is asserted against the checked-in working tree")
    listOf("skills/bill-boundary-history/content.md", "skills/bill-boundary-decisions/content.md").forEach { path ->

      val content = Files.readString(repoRoot.resolve(path))
      assertTrue(
        content.contains("never create `agent/` under `platform-packs/`"),
        "$path must forbid agent/ under excluded roots",
      )
      assertTrue(
        content.contains("goal-planning discovery exclusion contract"),
        "$path must name the exclusion contract as the authority",
      )
    }
  }

  private fun repoRoot(): Path? {
    var candidate: Path? = Path.of("").toAbsolutePath()
    while (candidate != null) {
      val gitEntry = candidate.resolve(".git")
      if (Files.isDirectory(gitEntry) || Files.isRegularFile(gitEntry)) return candidate
      candidate = candidate.parent
    }
    return null
  }

  private fun workingTreeDirectories(repoRoot: Path): List<String> {
    val found = mutableListOf<String>()
    val pending = ArrayDeque(listOf(repoRoot))
    while (pending.isNotEmpty()) {
      val children = runCatching {
        Files.list(pending.removeFirst()).use { entries ->
          entries.filter { path -> Files.isDirectory(path) }.toList()
        }
      }.getOrDefault(emptyList())
      for (child in children) {
        if (child.fileName.toString() in GoalPlanningDiscoveryExclusions.excludedDirectoryNames) continue
        found.add(repoRoot.relativize(child).joinToString("/"))
        pending.add(child)
      }
    }
    return found
  }
}
