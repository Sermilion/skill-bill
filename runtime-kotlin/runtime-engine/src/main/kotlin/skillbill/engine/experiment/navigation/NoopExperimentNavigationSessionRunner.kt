package skillbill.engine.experiment.navigation

import me.tatarka.inject.annotations.Inject
import skillbill.error.shellcontent.ExperimentIsolationCapabilityRefusalError
import skillbill.ports.experiment.navigation.ExperimentNavigationSessionRequest
import skillbill.ports.experiment.navigation.ExperimentNavigationSessionResult
import skillbill.ports.experiment.navigation.ExperimentNavigationSessionRunnerPort

@Inject
class NoopExperimentNavigationSessionRunner : ExperimentNavigationSessionRunnerPort {
  override fun runSession(request: ExperimentNavigationSessionRequest): ExperimentNavigationSessionResult {
    throw ExperimentIsolationCapabilityRefusalError(
      "Navigation experiment sessions are unavailable until a read-only session runner is configured.",
    )
  }
}
