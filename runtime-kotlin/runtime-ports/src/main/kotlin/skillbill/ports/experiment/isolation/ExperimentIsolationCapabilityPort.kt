package skillbill.ports.experiment.isolation

import skillbill.ports.experiment.isolation.model.ExperimentArmIsolationContext
import skillbill.ports.experiment.isolation.model.ExperimentIsolationObservation

interface ExperimentIsolationCapabilityPort {
  fun assertLaunchSupported(context: ExperimentArmIsolationContext)

  fun observe(context: ExperimentArmIsolationContext): ExperimentIsolationObservation = ExperimentIsolationObservation()
}
