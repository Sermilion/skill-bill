package skillbill.engine.featuretask.phase.prompt.compose

import skillbill.agentaddon.model.HydratedAgentAddonSelection
import skillbill.engine.featuretask.model.phase.FeatureTaskRuntimePhaseLaunchBriefing

object FeatureTaskRuntimePhasePromptComposer {
  fun compose(
    issueKey: String,
    briefing: FeatureTaskRuntimePhaseLaunchBriefing,
    configure: FeatureTaskRuntimePhasePromptComposeInputs.() -> FeatureTaskRuntimePhasePromptComposeInputs = { this },
  ): String =
    compose(
      configure(
        FeatureTaskRuntimePhasePromptComposeInputs(
          issueKey = issueKey,
          briefing = briefing,
        ),
      ),
    )

  fun compose(inputs: FeatureTaskRuntimePhasePromptComposeInputs): String = composePhasePrompt(inputs)

  fun budgetedAddonsFor(selection: HydratedAgentAddonSelection): HydratedAgentAddonSelection = selection
}
