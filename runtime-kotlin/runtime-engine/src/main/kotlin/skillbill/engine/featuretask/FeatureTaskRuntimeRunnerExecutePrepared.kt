package skillbill.engine.featuretask

import skillbill.engine.featuretask.model.FeatureTaskRuntimeCrashReconciliationResult
import skillbill.engine.featuretask.model.FeatureTaskRuntimeFinishedTelemetryContext
import skillbill.engine.featuretask.model.FeatureTaskRuntimeRunReport
import skillbill.engine.featuretask.model.FeatureTaskRuntimeRunRequest
import skillbill.engine.featuretask.model.RemediationBaseBlocked
import skillbill.engine.featuretask.model.RemediationBaseCoherent
import skillbill.engine.featuretask.validation.durableValidationChangedPaths
import skillbill.engine.featuretask.validation.resolveRequiredValidationCommand
import skillbill.error.FeatureTaskRuntimeOperatorDecisionRejectedError
import skillbill.workflow.decomposition.model.SpecSource
import skillbill.workflow.model.WorkflowStepStatus
import skillbill.workflow.model.workflowStepStatus
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeTransitionDeclaration

fun FeatureTaskRuntimeRunner.buildExecutePreparedRunTelemetryContext(
  runRequest: FeatureTaskRuntimeRunRequest,
  telemetrySessionId: String,
  reconciliation: FeatureTaskRuntimeCrashReconciliationResult,
  state: FeatureTaskRuntimeRunState,
) = FeatureTaskRuntimeFinishedTelemetryContext(
  telemetrySessionId = telemetrySessionId,
  phaseOutcomes = {
    recorder.loadPhaseRecords(runRequest.workflowId)
      .orEmpty()
      .mapValues { (_, record) -> record.status.wireValue }
  },
  reviewFixIterationCount = { loadReviewFixIterationCount(runRequest) },
  auditGapIterationCount = { auditGapIterationCount(runRequest.workflowId) },
  agentContext = { featureTaskRuntimeAgentContext(runRequest.workflowId) },
  regenerationTelemetry = { loadRegenerationTelemetry(runRequest) },
  findingVerificationTelemetry = { loadFindingVerificationTelemetry(runRequest) },
  phaseTokenData = { serializeTokenData(state.phaseTokenView) },
  crashReconciliation = { reconciliation },
)

fun FeatureTaskRuntimeRunner.driveExecutePreparedRunLoop(
  runRequest: FeatureTaskRuntimeRunRequest,
  specSource: SpecSource,
  transitions: FeatureTaskRuntimeTransitionDeclaration,
  observability: FeatureTaskRuntimeRunObservability,
  state: FeatureTaskRuntimeRunState,
): FeatureTaskRuntimeRunReport {
  reopenCappedReviewOnChangedDelta(runRequest)
  if (isGoalContinuationRun(runRequest)) {
    when (
      val remediation = goalContinuationRecorder.reconcileRemediationBaseCoherence(
        workflowId = runRequest.workflowId,
        gitOperations = phaseGates.gitOperations,
        repoRoot = runRequest.repoRoot,
      )
    ) {
      is RemediationBaseBlocked ->
        return remediationBaseCoherenceBlockedReport(runRequest, remediation.operatorGuidance)
      is RemediationBaseCoherent -> Unit
    }
  }
  val loop = FeatureTaskRuntimeRunLoop(
    context = FeatureTaskRuntimeRunLoopContext(
      runRequest,
      state,
      observability,
      specSource,
      transitions,
      recorder,
      goalContinuationRecorder,
      outputValidator,
      phaseGates,
      subtaskLauncher,
      phaseSettlementService,
      activityStampWriter,
      clock,
      diagnostics,
      FeatureTaskRuntimeRunLoopSession(
        operatorBlockRetry = recorder
          .loadOperatorBlockRetry(runRequest.workflowId)
          ?.takeIf { retry ->
            state.recordFor(retry.phaseId)?.status.let { status ->
              status == null || status.workflowStepStatus() == WorkflowStepStatus.PENDING
            }
          },
        initialPendingReentry = null,
      ),
    ),
  )
  runRequest.operatorDecision?.let { decision ->
    loop.applyOperatorDecision()?.let { rejection ->
      throw FeatureTaskRuntimeOperatorDecisionRejectedError(runRequest.workflowId, decision.wireValue, rejection)
    }
  }
  loop.drive()
  return loop.report()
}

private fun FeatureTaskRuntimeRunner.createExecutePreparedRunState(
  runRequest: FeatureTaskRuntimeRunRequest,
  transitions: FeatureTaskRuntimeTransitionDeclaration,
): FeatureTaskRuntimeRunState = FeatureTaskRuntimeRunState(
  initialRecords = recorder.loadPhaseRecords(runRequest.workflowId).orEmpty(),
  transitions = transitions,
  durableInitialLedger = recorder.loadPhaseLedger(runRequest.workflowId).orEmpty(),
  outputValidator = outputValidator,
  initialReviewGeneration = recorder.reconcileReviewGeneration(runRequest.workflowId),
  validationEvidenceCommandResolver = { validationEvidence ->
    resolveRequiredValidationCommand(
      resolver = phaseGates.validationGateResolver,
      requiredCommandForDeclaration = { declaration ->
        phaseGates.validationGateCoordinator.requiredValidationCommand(
          runRequest.repoRoot,
          runRequest.workflowId,
          declaration,
        )
      },
      changedPaths = durableValidationChangedPaths(recorder, runRequest.workflowId),
      evidence = validationEvidence,
      sourceLabel = "validate",
    )
  },
)

fun FeatureTaskRuntimeRunner.finalizeExecutePreparedRunReport(
  runRequest: FeatureTaskRuntimeRunRequest,
  report: FeatureTaskRuntimeRunReport,
  specSource: SpecSource,
): FeatureTaskRuntimeRunReport {
  val terminalReport =
    persistGoalContinuationOutcome(goalContinuationRecorder, recorder, phaseGates.gitOperations, runRequest, report)
  phaseGates.specGate.finalizeSingleSpecOnTerminal(
    runRequest,
    terminalReport,
    specSource,
    { finalizingAgentId(runRequest) },
  )
  return terminalReport
}
