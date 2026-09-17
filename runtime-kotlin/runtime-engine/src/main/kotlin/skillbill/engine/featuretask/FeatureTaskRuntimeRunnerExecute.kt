package skillbill.engine.featuretask
import skillbill.contracts.JsonCodec
import skillbill.engine.featuretask.model.FeatureTaskRuntimeCrashReconciliationResult
import skillbill.engine.featuretask.model.FeatureTaskRuntimeFindingVerificationTelemetry
import skillbill.engine.featuretask.model.FeatureTaskRuntimeRegenerationTelemetry
import skillbill.engine.featuretask.model.FeatureTaskRuntimeRunEvent
import skillbill.engine.featuretask.model.FeatureTaskRuntimeRunReport
import skillbill.engine.featuretask.model.FeatureTaskRuntimeRunRequest
import skillbill.ports.workflow.gitops.buildGoalSubtaskReviewInput
import skillbill.ports.workflow.gitops.model.GoalSubtaskReviewBaseline
import skillbill.workflow.goal.model.GoalSubtaskReviewState
import skillbill.workflow.model.WorkflowStepStatus
import skillbill.workflow.model.workflowStepStatus
import skillbill.workflow.taskruntime.FeatureTaskRuntimePhaseWorkflowDefinition
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimePhaseLedgerAction
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeResolvedBranch
import skillbill.workflow.taskruntime.toWorkflowArtifactMap

fun FeatureTaskRuntimeRunner.executePreparedRun(
  runRequest: FeatureTaskRuntimeRunRequest,
  reconciliation: FeatureTaskRuntimeCrashReconciliationResult,
): FeatureTaskRuntimeRunReport {
  val specSource = specSourceResolver.resolve(
    repoRoot = runRequest.repoRoot,
    specReference = runRequest.runInvariants.specReference,
    isGoalContinuation = isGoalContinuationRun(runRequest),
  )
  emitFeatureTaskRuntimeEventSafely(
    diagnostics = diagnostics,
    seam = "RunStarted event-sink emission",
  ) {
    runRequest.eventSink.emit(
      FeatureTaskRuntimeRunEvent.RunStarted(runRequest.workflowId, runRequest.runInvariants.featureSize.name),
    )
  }
  val telemetrySessionId = lifecycleTelemetry.started(runRequest)
  val observability = FeatureTaskRuntimeRunObservability(recorder, runRequest, diagnostics)
  val transitions = transitionsFor(runRequest)
  val state = createExecutePreparedRunState(runRequest, transitions)
  val telemetryContext = buildExecutePreparedRunTelemetryContext(
    runRequest,
    telemetrySessionId,
    reconciliation,
    state,
  )
  val report = runCatching {
    driveExecutePreparedRunLoop(runRequest, specSource, transitions, observability, state)
  }.onFailure { error ->
    lifecycleTelemetry.finishedError(
      telemetryContext,
      error,
    )
  }.getOrThrow()
  val terminalReport = finalizeExecutePreparedRunReport(runRequest, report, specSource)
  lifecycleTelemetry.finished(terminalReport, telemetryContext)
  return terminalReport
}

fun FeatureTaskRuntimeRunner.reopenCappedReviewOnChangedDelta(request: FeatureTaskRuntimeRunRequest) {
  if (!cappedReviewIsStale(request)) return
  checkNotNull(recorder.persistReviewGenerationInvalidation(request.workflowId)) {
    "Could not durably reopen the stale capped review for workflow '${request.workflowId}'."
  }
}

fun FeatureTaskRuntimeRunner.cappedReviewIsStale(request: FeatureTaskRuntimeRunRequest): Boolean {
  val goalBranch = request.goalContinuation?.goalBranch ?: return false
  val state = goalContinuationRecorder.reviewState(request.workflowId)
    ?.takeIf { it.reviewCapReached || it.pausedForOperatorDecision }
    ?: return false
  val judgedDigest = state.reviewedDeltaDigest ?: return true
  val resolved = recorder.loadResolvedBranch(request.workflowId)
  val digests = listOfNotNull(state.remediationBaseSha, state.reviewBaseSha).distinct().mapNotNull { base ->
    phaseGates.gitOperations.buildGoalSubtaskReviewInput(
      request.repoRoot,
      reviewBaseline(request, resolved, state, base),
      goalBranch,
    ).input?.deltaDigest
  }
  return digests.isNotEmpty() && judgedDigest !in digests
}

fun FeatureTaskRuntimeRunner.reviewBaseline(
  request: FeatureTaskRuntimeRunRequest,
  resolved: FeatureTaskRuntimeResolvedBranch?,
  state: GoalSubtaskReviewState,
  reviewBaseSha: String,
): GoalSubtaskReviewBaseline = resolved
  ?.let { FeatureTaskRuntimeScopedReviewBaseline.of(phaseGates.gitOperations, request.repoRoot, it, reviewBaseSha) }
  ?: GoalSubtaskReviewBaseline(reviewBaseSha, state.baselineUntrackedPaths)

internal fun FeatureTaskRuntimeRunner.loadReviewFixIterationCount(request: FeatureTaskRuntimeRunRequest): Int =
  recorder.loadPhaseLedger(request.workflowId)
    .orEmpty()
    .filter {
      it.action == FeatureTaskRuntimePhaseLedgerAction.LOOP_EDGE &&
        it.loopId == FeatureTaskRuntimePhaseWorkflowDefinition.REVIEW_FIX_LOOP_ID
    }
    .mapNotNull { it.edgeIteration }
    .maxOrNull()
    ?: 0

internal fun FeatureTaskRuntimeRunner.loadFindingVerificationTelemetry(
  request: FeatureTaskRuntimeRunRequest,
): FeatureTaskRuntimeFindingVerificationTelemetry {
  val capExhausted = reviewFixCapExhaustion(request.workflowId)
  val verifyRecord = recorder.loadPhaseRecords(request.workflowId)
    ?.get(FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_VERIFY_FINDINGS)
    ?: return FeatureTaskRuntimeFindingVerificationTelemetry(reviewFixCapExhausted = capExhausted)
  val outputMap = verifyRecord.outputArtifact
    ?.let(JsonCodec::parseObjectOrNull)
    ?.let(JsonCodec::jsonElementToValue)
    ?.let(JsonCodec::anyToStringAnyMap)
    ?.toWorkflowArtifactMap()
    ?: return FeatureTaskRuntimeFindingVerificationTelemetry(reviewFixCapExhausted = capExhausted)
  return FeatureTaskRuntimeFindingVerificationTelemetry(
    verifiedCount = FeatureTaskRuntimeOutputVerification.verifiedFindingDispositions(outputMap).size,
    rejectedCount = FeatureTaskRuntimeOutputVerification.rejectedFindingDispositions(outputMap).size,
    reviewFixCapExhausted = capExhausted,
  )
}

internal fun FeatureTaskRuntimeRunner.loadRegenerationTelemetry(
  request: FeatureTaskRuntimeRunRequest,
): FeatureTaskRuntimeRegenerationTelemetry {
  val ledger = recorder.loadPhaseLedger(request.workflowId).orEmpty()
  val regenFires = ledger.filter {
    it.action == FeatureTaskRuntimePhaseLedgerAction.LOOP_EDGE &&
      FeatureTaskRuntimePhaseWorkflowDefinition.isRegenerationLoopId(it.loopId.orEmpty())
  }
  val firedLoops = regenFires.mapNotNull { it.loopId }.toSet()
  val blocked = recorder.loadPhaseRecords(request.workflowId)
    .orEmpty()
    .values
    .filter { it.status.workflowStepStatus() == WorkflowStepStatus.BLOCKED }
  val capExhaustedLoops = blocked
    .mapNotNull { it.loopId }
    .filter(FeatureTaskRuntimePhaseWorkflowDefinition::isRegenerationLoopId)
    .toSet()
  val unattributable = blocked.count {
    (it.blockedReason ?: "").contains("cannot attribute to a producing phase")
  }
  val producerNotInPipeline = blocked.count {
    (it.blockedReason ?: "").contains("absent from this run's resolved pipeline")
  }
  val regenerated = (firedLoops - capExhaustedLoops).size
  val outcomeCounts = buildMap {
    if (regenerated > 0) put("regenerated", regenerated)
    if (capExhaustedLoops.isNotEmpty()) put("cap_exhausted", capExhaustedLoops.size)
    if (unattributable > 0) put("unattributable", unattributable)
    if (producerNotInPipeline > 0) put("producer_not_in_pipeline", producerNotInPipeline)
  }
  return FeatureTaskRuntimeRegenerationTelemetry(
    activationCount = firedLoops.size,
    attemptCount = regenFires.size,
    outcomeCounts = outcomeCounts,
  )
}

internal fun FeatureTaskRuntimeRunner.finalizingAgentId(request: FeatureTaskRuntimeRunRequest): String? =
  agentAttributionFromPhaseState(recorder, request.workflowId).finalizingAgentId

internal val FeatureTaskRuntimeRunner.lifecycleTelemetry get() = phaseGates.lifecycleTelemetry
internal val FeatureTaskRuntimeRunner.specSourceResolver get() = phaseGates.specGate.specSourceResolver
