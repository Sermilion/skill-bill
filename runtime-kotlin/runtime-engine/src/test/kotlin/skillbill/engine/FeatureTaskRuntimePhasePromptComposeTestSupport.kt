package skillbill.engine

import skillbill.engine.featuretask.model.phase.FeatureTaskRuntimePhaseLaunchBriefing
import skillbill.engine.featuretask.phase.prompt.compose.FeatureTaskRuntimePhasePromptComposeInputs
import skillbill.engine.featuretask.phase.prompt.compose.FeatureTaskRuntimePhasePromptComposer

internal fun composePhasePrompt(inputs: FeatureTaskRuntimePhasePromptComposeInputs): String =
  FeatureTaskRuntimePhasePromptComposer.compose(inputs)

internal fun composePhasePrompt(
  issueKey: String,
  briefing: FeatureTaskRuntimePhaseLaunchBriefing,
  configure: FeatureTaskRuntimePhasePromptComposeInputs.() -> FeatureTaskRuntimePhasePromptComposeInputs = { this },
): String = composePhasePrompt(
  FeatureTaskRuntimePhasePromptComposeInputs(issueKey = issueKey, briefing = briefing).configure(),
)
