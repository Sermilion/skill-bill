package skillbill.ports.experiment.navigation

import skillbill.ports.experiment.navigation.model.ExperimentNavigationRunRequest

class UnavailableExperimentNavigationRunPort : ExperimentNavigationRunPort {
  override fun acceptanceCriteria(specText: String): List<String> =
    error("ExperimentNavigationRunPort is unavailable in this test harness.")

  override fun run(request: ExperimentNavigationRunRequest): String =
    error("ExperimentNavigationRunPort is unavailable in this test harness.")
}
