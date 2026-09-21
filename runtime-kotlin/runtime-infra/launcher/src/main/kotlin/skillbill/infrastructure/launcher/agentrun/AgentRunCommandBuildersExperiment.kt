package skillbill.infrastructure.launcher.agentrun

import skillbill.infrastructure.launcher.experiment.ExperimentLaunchIsolationEnvironment
import skillbill.infrastructure.launcher.experiment.ExperimentLaunchIsolationRequest
import skillbill.ports.agentrun.model.SkillRunRequest

internal fun experimentLaunchIsolation(request: SkillRunRequest, inheritedPath: String? = null) =
  ExperimentLaunchIsolationEnvironment.apply(
    ExperimentLaunchIsolationRequest(
      treatmentEnabled = request.treatmentCapabilitiesEnabled.any {
        it !in request.treatmentCapabilitiesDenied
      },
      treatmentCapabilities = request.treatmentCapabilitiesEnabled,
      treatmentCapabilitiesDenied = request.treatmentCapabilitiesDenied,
      requiredLauncherCapabilities = request.experimentRequiredLauncherCapabilities,
      repoRoot = request.repoRoot,
      managedToolsBin = request.experimentManagedToolsBin,
      graphIndexDirectory = request.experimentGraphIndexDirectory,
      inheritedPath = inheritedPath,
    ),
  )
