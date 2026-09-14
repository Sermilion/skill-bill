package skillbill.engine.featuretask

import skillbill.engine.featuretask.model.FeatureTaskRuntimeImplementationContinuation
import skillbill.engine.featuretask.model.FeatureTaskRuntimePhasePromptComposeInputs
import skillbill.engine.featuretask.model.PhasePromptHeaderInputs
import skillbill.workflow.taskruntime.FeatureTaskRuntimePhaseWorkflowDefinition

fun phasePromptLeadingSections(inputs: FeatureTaskRuntimePhasePromptComposeInputs): List<String> = listOf(
  phasePromptHeader(
    PhasePromptHeaderInputs(
      issueKey = inputs.issueKey,
      phaseId = inputs.briefing.phaseId,
      agentRunValidateFallback = inputs.agentRunValidateFallback,
      packCollectAllCommand = inputs.packCollectAllCommand,
      packBuildCommand = inputs.packBuildCommand,
      validationGateRepair = inputs.validationGateRepair,
      validationGateTriage = inputs.validationGateTriage,
      acceptanceCriteria = inputs.briefing.acceptanceCriteria,
    ),
  ),
  installedRuntimeAuthorityDirective(),
  ceremonyDirective(inputs.briefing),
  mutatingPhaseIdempotencyDirective(inputs.briefing.phaseId),
  nonValidatePhaseValidationOwnershipDirective(
    inputs.briefing.phaseId,
  ),
  nonBuildPhaseBuildOwnershipDirective(
    inputs.briefing.phaseId,
  ),
  minimalismDisciplineDirective(inputs.briefing.phaseId),
  testValueDisciplineDirective(inputs.briefing.phaseId),
)

fun phasePromptMiddleSections(inputs: FeatureTaskRuntimePhasePromptComposeInputs): List<String> = listOf(
  goalContinuationDirective(inputs.briefing.phaseId, inputs.suppressDecomposition),
  absentValidationGateDegradationDirective(inputs.briefing.phaseId, inputs.agentRunValidateFallback),
  validationGateFindingsDirective(
    inputs.briefing.phaseId,
    inputs.validationGateFindings,
    inputs.validationGateTriagePlan,
  ),
  reviewExecutionDirective(
    inputs.briefing.phaseId,
    ReviewExecutionDirectiveInputs(
      codeReviewMode = inputs.codeReviewMode,
      goalSubtaskReviewInput = inputs.goalSubtaskReviewInput,
      reviewPassNumber = inputs.reviewPassNumber,
      resolvedReviewTier = inputs.resolvedReviewTier,
      reviewDecidingRule = inputs.reviewDecidingRule,
      baselineUntrackedPaths = inputs.baselineUntrackedPaths,
      repairLedger = inputs.repairLedger,
      priorReviewContext = inputs.priorReviewContext,
    ),
  ),
  commitExclusionDirective(inputs.briefing.phaseId, inputs.issueKey),
  inputs.briefing.briefingText,
)

fun phasePromptTrailingSections(
  inputs: FeatureTaskRuntimePhasePromptComposeInputs,
  effectiveContinuation: FeatureTaskRuntimeImplementationContinuation?,
): List<String> = listOf(
  operatorBlockRetryDirective(inputs.briefing.phaseId, inputs.operatorBlockRetry),
  auditRetryFocusDirective(inputs.auditRetryFocusHint),
  implementationContinuationDirective(inputs.briefing.phaseId, effectiveContinuation),
  retryCorrectionDirective(inputs.briefing, inputs.priorSchemaFailure, inputs.correctiveRepairContext),
  terminalRetryDirective(inputs.priorTerminalFailure),
  findingCoverageDirective(inputs.priorFindingCoverage),
  if (
    inputs.validationGateFindings != null ||
    (
      !inputs.agentRunValidateFallback &&
        inputs.briefing.phaseId == FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_VALIDATE
      )
  ) {
    gateRepairNoOutputSchemaDirective(inputs.briefing.phaseId, inputs.validationGateTriage)
  } else {
    outputContract(inputs.briefing, inputs.agentRunValidateFallback)
  },
)
