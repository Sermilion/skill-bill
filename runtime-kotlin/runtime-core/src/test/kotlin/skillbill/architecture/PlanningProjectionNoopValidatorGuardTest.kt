package skillbill.architecture

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.name
import kotlin.test.Test
import kotlin.test.assertEquals

class PlanningProjectionNoopValidatorGuardTest {
  private val runtimeRoot: Path =
    Path.of("").toAbsolutePath().normalize().let { workingDir ->
      if (workingDir.fileName.toString().startsWith("runtime-")) workingDir.parent else workingDir
    }

  private val noopSymbol = "NoopFeatureTaskRuntimePlanningProjectionValidator"

  private val permittedConsumers: Map<String, String> = mapOf(
    "FeatureTaskRuntimeRunnerTestSupport.kt" to
      "Shared run-loop harness default; runner-behavior tests do not assert schema-projection " +
      "enforcement (covered by the RealValidator* integration suites).",
    "GoalPlanningSweepTest.kt" to
      "Goal-planning sweep behavior; planning-projection enforcement is incidental to the sweep.",
    "VerdictAwareRegisterAndConsumersTest.kt" to
      "Typed Kotlin projection rules for the review-repair request; SKILL-233 made the previously " +
      "implicit constructor default explicit.",
    "FeatureTaskRuntimeHandoffProjectionValidatorTestSupport.kt" to
      "runtime-domain test fixture; the domain test source set cannot reach the infra-fs validator.",
    "FeatureTaskRuntimeSharedReviewEvidenceProjectionTest.kt" to
      "runtime-domain projection shape assertions; the domain test source set cannot reach the " +
      "infra-fs validator.",
  )

  @Test
  fun `only the enumerated tests may use the Noop planning-projection validator`() {
    val testRoots = listOf(
      runtimeRoot.resolve("runtime-application/src/test"),
      runtimeRoot.resolve("runtime-domain/src/test"),
      runtimeRoot.resolve("runtime-cli/src/test"),
      runtimeRoot.resolve("runtime-engine/src/test"),
    )

    val actualConsumers = testRoots
      .filter { Files.isDirectory(it) }
      .flatMap { root ->
        Files.walk(root).use { paths ->
          paths.filter { Files.isRegularFile(it) && it.extension == "kt" }
            .filter { Files.readString(it).contains(noopSymbol) }
            .map { it.name }
            .toList()
        }
      }
      .toSet()

    assertEquals(
      permittedConsumers.keys,
      actualConsumers,
      "The Noop planning-projection validator consumer set drifted from the AC-004 allow-list. A new " +
        "consumer must switch to the real validator (see RealValidator* integration suites) or be added " +
        "to permittedConsumers with an explicit rationale.",
    )
  }
}
