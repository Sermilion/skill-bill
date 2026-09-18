package skillbill.infrastructure.skills.install.nativeagent

import skillbill.config.model.RepoLocalConfig
import skillbill.infrastructure.skills.nativeagent.composition.NativeAgentCompositionContext
import skillbill.infrastructure.skills.scaffold.authoring.renderAuthoredContentBody

internal fun installNativeAgentCompositionContext(): NativeAgentCompositionContext = NativeAgentCompositionContext(
  reviewContextBudgetBytes = RepoLocalConfig.defaults().reviewContextBudget.maxLaneLaunchBytes,
  renderGovernedBody = ::renderAuthoredContentBody,
  packLoader = InstallNativeAgentPlatformPackLoader,
)
