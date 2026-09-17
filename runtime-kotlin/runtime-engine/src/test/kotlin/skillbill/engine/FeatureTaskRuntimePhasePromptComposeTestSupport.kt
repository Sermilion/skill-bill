package skillbill.engine

import skillbill.engine.featuretask.FeatureTaskRuntimePhasePromptComposeInputs
import skillbill.engine.featuretask.FeatureTaskRuntimePhasePromptComposer
import skillbill.engine.featuretask.model.FeatureTaskRuntimePhaseLaunchBriefing

internal fun composePhasePrompt(inputs: FeatureTaskRuntimePhasePromptComposeInputs): String =
  FeatureTaskRuntimePhasePromptComposer.compose(inputs)

internal fun composePhasePrompt(
  issueKey: String,
  briefing: FeatureTaskRuntimePhaseLaunchBriefing,
  configure: FeatureTaskRuntimePhasePromptComposeInputs.() -> FeatureTaskRuntimePhasePromptComposeInputs = { this },
): String = composePhasePrompt(
  FeatureTaskRuntimePhasePromptComposeInputs(issueKey = issueKey, briefing = briefing).configure(),
)
