package skillbill.ports.experiment.navigation

import skillbill.ports.experiment.navigation.model.ExperimentNavigationRunRequest

interface ExperimentNavigationRunPort {
  fun acceptanceCriteria(specText: String): List<String>

  fun run(request: ExperimentNavigationRunRequest): String
}
