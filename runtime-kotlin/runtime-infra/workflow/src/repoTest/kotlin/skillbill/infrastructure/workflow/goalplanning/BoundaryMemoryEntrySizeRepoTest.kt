package skillbill.infrastructure.workflow.goalplanning

import skillbill.ports.goalrunner.planning.model.GoalPlanningBoundaryBodyResolutionCaps
import skillbill.testing.repoRootFromTest
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BoundaryMemoryEntrySizeRepoTest {
  private val maxEntryBodyBytes = GoalPlanningBoundaryBodyResolutionCaps.VERIFICATION.maxBodyBytes

  @Test
  fun `every boundary memory entry body fits the verification max_body_bytes cap`() {
    val repoRoot = GoalPlanningRepositoryScope.canonicalRoot(repoRootFromTest())
    val walk = GoalPlanningRepositoryScope.agentDirectories(repoRoot)
    assertFalse(walk.incomplete, "boundary memory directory walk hit its visit cap")

    val boundaryFiles =
      walk.directories
        .flatMap { directory -> GoalPlanningRepositoryScope.BOUNDARY_MEMORY_FILES.map(directory::resolve) }
        .filter(Files::isRegularFile)
    assertTrue(boundaryFiles.isNotEmpty(), "no boundary memory files found under $repoRoot")

    val oversized =
      boundaryFiles.flatMap { file ->
        val sourcePath = repoRoot.relativize(file).toString().replace('\\', '/')
        BoundaryMemoryHeadingParser.parse(sourcePath, Files.readString(file))
          .map { entry -> entry to entry.body.toByteArray(Charsets.UTF_8).size }
          .filter { (_, bytes) -> bytes > maxEntryBodyBytes }
          .map { (entry, bytes) -> "$sourcePath: ${entry.heading} ($bytes bytes)" }
      }

    assertEquals(
      emptyList(),
      oversized,
      "Boundary memory entry bodies must be at most $maxEntryBodyBytes UTF-8 bytes, measured from one " +
        "`## [<date>] <title>` heading to the next. Condense or split these entries:",
    )
  }
}
