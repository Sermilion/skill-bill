package skillbill.workflow.taskruntime.model.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FeatureTaskRuntimeProjectionCanonicalizationIdempotenceTest {
  @Test
  fun `existing fixture keeps its canonical output shape`() {
    val fixture = FeatureTaskRuntimeProjectionCanonicalizationFixtures.ALL.single()

    assertEquals(
      mapOf(
        "completed_task_ids" to listOf("task-01"),
        "changed_paths" to listOf("src/Foo.kt"),
        "tests_executed" to listOf(mapOf("name" to "FooTest.kt", "outcome" to "passed")),
        "reconciliation_evidence" to mapOf("reconciled" to true, "evidence" to "ok"),
      ),
      FeatureTaskRuntimeProjectionCanonicalizer.canonicalize(fixture).canonical,
    )
  }

  @Test
  fun `canonicalize equals canonicalize applied twice across every canned fixture`() {
    FeatureTaskRuntimeProjectionCanonicalizationFixtures.ALL.forEachIndexed { index, fixture ->
      val first = FeatureTaskRuntimeProjectionCanonicalizer.canonicalize(fixture)
      val second = FeatureTaskRuntimeProjectionCanonicalizer.canonicalize(first.canonical)

      assertEquals(first.canonical, second.canonical, "fixture[$index] must reach a canonical fixed point")
      assertTrue(
        second.diagnostics.isEmpty(),
        "fixture[$index] must report no further canonicalizations on the second pass",
      )
    }
  }

  @Test
  fun `an unknown key on a nested closed object reaches a fixed point in one pass`() {
    val produced =
      mapOf(
        "tests_executed" to listOf(mapOf("name" to "FooTest", "outcome" to "passed", "duration_ms" to 12)),
        "reconciliation_evidence" to mapOf("reconciled" to true, "evidence" to "  tree at target  ", "extra" to 1),
      )

    val first = FeatureTaskRuntimeProjectionCanonicalizer.canonicalize(produced)
    val second = FeatureTaskRuntimeProjectionCanonicalizer.canonicalize(first.canonical)

    assertEquals(first.canonical, second.canonical)
    assertTrue(
      second.diagnostics.isEmpty(),
      "the discard must be complete in one pass, leaving nothing for a second",
    )
  }
}
