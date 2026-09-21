package skillbill.ports.experiment.navigation.model

import java.nio.file.Path

data class ExperimentNavigationDecisionContext(
  val pairId: String,
  val armId: String,
  val repoRoot: Path,
  val acceptanceCriteria: List<String>,
)

sealed interface ExperimentNavigationDecision {
  data class Search(val query: String) : ExperimentNavigationDecision

  data class Read(val path: String) : ExperimentNavigationDecision

  data object Complete : ExperimentNavigationDecision
}
