package skillbill.application.install

import me.tatarka.inject.annotations.Inject
import skillbill.ports.install.plan.InstallPlanningFactsPort
import skillbill.ports.install.plan.InstallPlatformSkillMaterializationPort
import skillbill.ports.install.plan.InstallStagingIntentPort

@Inject
class InstallPlanningPorts(
  val planningFactsPort: InstallPlanningFactsPort,
  val platformSkillMaterializationPort: InstallPlatformSkillMaterializationPort,
  val stagingIntentPort: InstallStagingIntentPort,
)
