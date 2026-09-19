package skillbill.engine.featuretask.phase.prompt.compose




import skillbill.engine.featuretask.phase.prompt.directives.ReviewExecutionDirectiveInputs
import skillbill.engine.featuretask.phase.prompt.directives.auditRetryFocusDirective
import skillbill.engine.featuretask.phase.prompt.directives.ceremonyDirective
import skillbill.engine.featuretask.phase.prompt.directives.commitExclusionDirective
import skillbill.engine.featuretask.phase.prompt.directives.findingCoverageDirective
import skillbill.engine.featuretask.phase.prompt.directives.gateRepairNoOutputSchemaDirective
import skillbill.engine.featuretask.phase.prompt.directives.goalContinuationDirective
import skillbill.engine.featuretask.phase.prompt.directives.implementationContinuationDirective
import skillbill.engine.featuretask.phase.prompt.directives.installedRuntimeAuthorityDirective
import skillbill.engine.featuretask.phase.prompt.directives.minimalismDisciplineDirective
import skillbill.engine.featuretask.phase.prompt.directives.mutatingPhaseIdempotencyDirective
import skillbill.engine.featuretask.phase.prompt.directives.nonBuildPhaseBuildOwnershipDirective
import skillbill.engine.featuretask.phase.prompt.directives.nonValidatePhaseValidationOwnershipDirective
import skillbill.engine.featuretask.phase.prompt.directives.operatorBlockRetryDirective
import skillbill.engine.featuretask.phase.prompt.directives.outputContract
import skillbill.engine.featuretask.phase.prompt.directives.phasePromptHeader
import skillbill.engine.featuretask.phase.prompt.directives.retryCorrectionDirective
import skillbill.engine.featuretask.review.core.reviewExecutionDirective
import skillbill.engine.featuretask.phase.prompt.directives.runtimeOwnedValidateFinishedDirective
import skillbill.engine.featuretask.phase.prompt.directives.terminalRetryDirective
import skillbill.engine.featuretask.phase.prompt.directives.testValueDisciplineDirective
import skillbill.engine.featuretask.phase.prompt.directives.validationGateFindingsDirective
import skillbill.engine.featuretask.model.core.FeatureTaskRuntimeImplementationContinuation
import skillbill.engine.featuretask.model.phase.PhasePromptHeaderInputs
import skillbill.workflow.taskruntime.FeatureTaskRuntimePhaseWorkflowDefinition

fun phasePromptLeadingSections(inputs: FeatureTaskRuntimePhasePromptComposeInputs): List<String> = listOf(
  phasePromptHeader(
    PhasePromptHeaderInputs(
      issueKey = inputs.issueKey,
      phaseId = inputs.briefing.phaseId,
      agentRunValidateFallback = inputs.agentRunValidateFallback,
      packCollectAllCommand = inputs.packCollectAllCommand,
      packConfirmationGateCommand = inputs.packConfirmationGateCommand,
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
  commitExclusionDirective(inputs.briefing.phaseId),
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
    inputs.briefing.phaseId == FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_VALIDATE &&
    !inputs.validationGateTriage
  ) {
    runtimeOwnedValidateFinishedDirective(inputs.briefing.phaseId)
  } else if (inputs.validationGateFindings != null) {
    gateRepairNoOutputSchemaDirective(inputs.briefing.phaseId, inputs.validationGateTriage)
  } else {
    outputContract(inputs.briefing, inputs.agentRunValidateFallback)
  },
)
