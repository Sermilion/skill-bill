package skillbill.engine.experiment.navigation

import skillbill.ports.experiment.navigation.ExperimentNavigationReadReceipt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class NavigationHiddenLabelScorerTest {
  @Test
  fun `hidden labels score settled reads and keep precision unavailable without exhaustive annotations`() {
    val score = NavigationHiddenLabelScorer.score(
      acceptanceCriteria = listOf("one", "two"),
      hiddenLabels = mapOf("one" to setOf("src/One.kt")),
      reads = listOf(
        ExperimentNavigationReadReceipt("src/One.kt", "discovery"),
        ExperimentNavigationReadReceipt("src/Other.kt", "discovery"),
      ),
      deliveredPaths = listOf("src/One.kt"),
      annotationsExhaustive = false,
    )

    assertEquals(1, score.coverage.labelledCriteria)
    assertEquals(2, score.coverage.totalCriteria)
    assertEquals(1, score.deliveredCriteria)
    assertEquals(1, score.relevantReads)
    assertNull(score.precision)
  }

  @Test
  fun `partial annotations cannot claim exhaustive precision`() {
    val score = NavigationHiddenLabelScorer.score(
      acceptanceCriteria = listOf("one", "two"),
      hiddenLabels = mapOf("one" to setOf("src/One.kt")),
      reads = listOf(ExperimentNavigationReadReceipt("src/One.kt", "discovery")),
      deliveredPaths = listOf("src/One.kt"),
      annotationsExhaustive = true,
    )

    assertFalse(score.coverage.precisionAvailable)
    assertNull(score.precision)
  }
}
