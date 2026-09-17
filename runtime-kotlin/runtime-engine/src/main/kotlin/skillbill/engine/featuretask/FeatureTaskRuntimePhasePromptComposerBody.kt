package skillbill.engine.featuretask

import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeCorrectiveRepairContext
import skillbill.workflow.taskruntime.FeatureTaskRuntimePhaseWorkflowDefinition

fun composePhasePrompt(inputs: FeatureTaskRuntimePhasePromptComposeInputs): String =
  phasePromptSections(inputs).filter(String::isNotBlank).joinToString(separator = "\n\n")

fun phasePromptSections(inputs: FeatureTaskRuntimePhasePromptComposeInputs): List<String> {
  val effectiveInputs = inputs.auditRetryScoped()
  requireComposableInputs(
    issueKey = effectiveInputs.issueKey,
    priorSchemaFailure = effectiveInputs.priorSchemaFailure,
    priorTerminalFailure = effectiveInputs.priorTerminalFailure,
    priorFindingCoverage = effectiveInputs.priorFindingCoverage,
    correctiveRepairContext = effectiveInputs.correctiveRepairContext,
  )
  val effectiveContinuation =
    effectiveInputs.implementationContinuation.takeUnless { effectiveInputs.correctiveRepairContext != null }
  return phasePromptLeadingSections(effectiveInputs) +
    phasePromptMiddleSections(effectiveInputs) +
    phasePromptTrailingSections(effectiveInputs, effectiveContinuation)
}

private fun FeatureTaskRuntimePhasePromptComposeInputs.auditRetryScoped():
  FeatureTaskRuntimePhasePromptComposeInputs {
  val focusHint = auditRetryFocusHint?.takeIf(String::isNotBlank)
  if (briefing.phaseId != FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_AUDIT || focusHint == null) return this
  return copy(briefing = briefing.forAuditRetry(focusHint))
}

private fun requireComposableInputs(
  issueKey: String,
  priorSchemaFailure: String?,
  priorTerminalFailure: String?,
  priorFindingCoverage: String?,
  correctiveRepairContext: FeatureTaskRuntimeCorrectiveRepairContext?,
) {
  require(issueKey.isNotBlank()) { "issueKey is required to compose a phase prompt." }
  require(correctiveRepairContext == null || !priorSchemaFailure.isNullOrBlank()) {
    "correctiveRepairContext requires a non-blank priorSchemaFailure; raw repair context belongs " +
      "only to schema-gate retries."
  }
  require(correctiveRepairContext == null || priorTerminalFailure.isNullOrBlank()) {
    "correctiveRepairContext cannot accompany a retryable-terminal failure; the correction kinds " +
      "must stay separate."
  }
  require(priorFindingCoverage.isNullOrBlank() || priorSchemaFailure.isNullOrBlank()) {
    "priorFindingCoverage cannot accompany a schema-gate failure; a receipt is either short of its " +
      "carried findings or rejected, never both in one correction."
  }
}
