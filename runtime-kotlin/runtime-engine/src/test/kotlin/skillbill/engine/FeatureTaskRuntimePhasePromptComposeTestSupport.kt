package skillbill.engine

import skillbill.engine.featuretask.model.phase.FeatureTaskRuntimePhaseLaunchBriefing
import skillbill.engine.featuretask.phase.prompt.compose.FeatureTaskRuntimePhasePromptComposeInputs
import skillbill.engine.featuretask.phase.prompt.compose.FeatureTaskRuntimePhasePromptComposer

private val TEST_MUTATING_PHASES = setOf("implement", "simplify", "implement_fix")
private val TEST_SINGLE_SESSION_PHASES = setOf("simplify", "audit")

internal fun composePhasePrompt(inputs: FeatureTaskRuntimePhasePromptComposeInputs): String =
  FeatureTaskRuntimePhasePromptComposer.compose(inputs)

internal fun composePhasePrompt(
  issueKey: String,
  briefing: FeatureTaskRuntimePhaseLaunchBriefing,
  configure: FeatureTaskRuntimePhasePromptComposeInputs.() -> FeatureTaskRuntimePhasePromptComposeInputs = { this },
): String =
  composePhasePrompt(
    FeatureTaskRuntimePhasePromptComposeInputs(
      issueKey = issueKey,
      briefing = briefing,
      mutating = briefing.phaseId in TEST_MUTATING_PHASES,
      singleAgentSession = briefing.phaseId in TEST_SINGLE_SESSION_PHASES,
    ).configure(),
  )
