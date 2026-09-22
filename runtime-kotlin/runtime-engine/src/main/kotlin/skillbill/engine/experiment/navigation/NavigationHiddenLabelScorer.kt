package skillbill.engine.experiment.navigation

import skillbill.ports.experiment.navigation.ExperimentNavigationLabelCoverage
import skillbill.ports.experiment.navigation.ExperimentNavigationReadReceipt

data class NavigationHiddenLabelScore(
  val coverage: ExperimentNavigationLabelCoverage,
  val deliveredCriteria: Int,
  val relevantReads: Int,
  val precision: Double?,
)

object NavigationHiddenLabelScorer {
  fun score(
    acceptanceCriteria: List<String>,
    hiddenLabels: Map<String, Set<String>>,
    reads: List<ExperimentNavigationReadReceipt>,
    deliveredPaths: List<String>,
    annotationsExhaustive: Boolean,
  ): NavigationHiddenLabelScore {
    val labelled = acceptanceCriteria.count { criterion -> hiddenLabels.containsKey(criterion) }
    val relevant =
      reads.count { receipt ->
        hiddenLabels.values.any { paths -> receipt.path in paths }
      }
    val delivered =
      acceptanceCriteria.count { criterion ->
        hiddenLabels[criterion].orEmpty().any { path -> path in deliveredPaths }
      }
    val precision =
      if (annotationsExhaustive && labelled == acceptanceCriteria.size && reads.isNotEmpty()) {
        relevant.toDouble() / reads.size
      } else {
        null
      }
    return NavigationHiddenLabelScore(
      coverage =
        ExperimentNavigationLabelCoverage(
          labelledCriteria = labelled,
          totalCriteria = acceptanceCriteria.size,
          precisionAvailable = precision != null,
        ),
      deliveredCriteria = delivered,
      relevantReads = relevant,
      precision = precision,
    )
  }
}
