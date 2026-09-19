package skillbill.engine.featuretask.runloop.phase




import skillbill.engine.featuretask.runloop.core.AttemptResult
import skillbill.engine.featuretask.runloop.core.BlockAndPersistArgs
import skillbill.engine.featuretask.runloop.core.BlockAndPersistInPhaseArgs
import skillbill.engine.featuretask.runloop.core.BlockAndPersistPayload
import skillbill.engine.featuretask.runloop.core.CapturedPhaseOutput
import skillbill.engine.featuretask.runloop.state.FeatureTaskRuntimeAttemptBudgets
import skillbill.engine.featuretask.runloop.observability.FeatureTaskRuntimeContinuationKind
import skillbill.engine.featuretask.lifecycle.continuation.FeatureTaskRuntimeGoalContinuationRecorder
import skillbill.engine.featuretask.runloop.state.FeatureTaskRuntimeNonOutputAttempt
import skillbill.engine.featuretask.phase.record.FeatureTaskRuntimePhaseRecorder
import skillbill.engine.featuretask.runloop.settlement.FeatureTaskRuntimeRunLoopAttemptSettlement
import skillbill.engine.featuretask.runloop.core.FeatureTaskRuntimeRunLoopContext
import skillbill.engine.featuretask.runloop.output.FeatureTaskRuntimeRunLoopOutputPersistence
import skillbill.engine.featuretask.runloop.core.FeatureTaskRuntimeRunLoopPlanningBranch
import skillbill.engine.featuretask.runloop.output.FeatureTaskRuntimeRunLoopRecordRejection
import skillbill.engine.featuretask.runloop.core.FeatureTaskRuntimeRunLoopSession
import skillbill.engine.featuretask.runloop.observability.FeatureTaskRuntimeRunObservability
import skillbill.engine.featuretask.runloop.observability.blocked
import skillbill.engine.featuretask.runloop.observability.continuation
import skillbill.engine.featuretask.runloop.state.FeatureTaskRuntimeRunState
import skillbill.engine.featuretask.runloop.core.FindingsOwedKind
import skillbill.engine.featuretask.runloop.core.FixLoopBranchContext
import skillbill.engine.featuretask.lifecycle.continuation.GoalContinuationStateRecordRequest
import skillbill.engine.featuretask.runloop.core.MissingProducerAgentBlockArgs
import skillbill.engine.featuretask.runloop.core.MissingProducerAgentResolutionArgs
import skillbill.engine.featuretask.runloop.core.PauseAndPersistInPhaseArgs
import skillbill.engine.featuretask.runloop.core.PauseAtArgs
import skillbill.engine.featuretask.runloop.core.PhaseAttemptContext
import skillbill.engine.featuretask.runloop.core.PhaseBlockRequest
import skillbill.engine.featuretask.runloop.core.PhaseOutcome
import skillbill.engine.featuretask.runloop.core.PhaseRun
import skillbill.engine.featuretask.phase.prompt.directives.PriorAttemptCorrection
import skillbill.engine.featuretask.runloop.core.ProducerEvidenceRecordRejectionArgs
import skillbill.engine.featuretask.runloop.core.QuarantineRecordRejectionArgs
import skillbill.engine.featuretask.runloop.core.RecordRejectedOutputArgs
import skillbill.engine.featuretask.runloop.core.RecordRejection
import skillbill.engine.featuretask.runloop.core.RejectedOutputTargetingOverrides
import skillbill.engine.featuretask.runner.STATUS_BLOCKED
import skillbill.engine.featuretask.runner.STATUS_PAUSED
import skillbill.engine.featuretask.runner.STATUS_RUNNING
import skillbill.engine.featuretask.runloop.core.SettleRecordRejectionArgs
import skillbill.engine.featuretask.runloop.core.UnattributableRecordRejectionArgs
import skillbill.engine.featuretask.runloop.core.WriteQuarantineRejectedOutputArgs
import skillbill.engine.featuretask.runloop.core.defaultRejectedOutputTargetingArgs
import skillbill.engine.featuretask.runloop.observability.fixLoopIteration
import skillbill.engine.featuretask.lifecycle.continuation.isGoalContinuationRun
import skillbill.engine.featuretask.runner.isProcessFailureBlockReason
import skillbill.engine.featuretask.runner.nonRetryingPhaseSchemaBlockReason
import skillbill.engine.featuretask.runloop.observability.paused
import skillbill.engine.featuretask.runloop.core.phaseBlockArgs
import skillbill.engine.featuretask.runloop.core.withDisposition
import skillbill.engine.featuretask.runner.withSchemaGateDetail
import skillbill.engine.featuretask.model.phase.FeatureTaskRuntimePhaseStateRequest
import skillbill.engine.featuretask.model.phase.FeatureTaskRuntimeProducerOutputRead
import skillbill.engine.featuretask.model.review.FeatureTaskRuntimeRejectedOutputWrite
import skillbill.engine.featuretask.model.core.FeatureTaskRuntimeRunRequest
import skillbill.engine.featuretask.model.phase.ProducerOutputQueryArgs
import skillbill.ports.diagnostics.model.ProducerOutputEvidence
import skillbill.workflow.taskruntime.FeatureTaskRuntimePhaseWorkflowDefinition
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeBackwardEdge
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeFailureDisposition
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeImplementationAttemptStatus
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeQuarantineEntry
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeTransitionDeclaration
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeVerdict

object FeatureTaskRuntimeRunLoopPhaseAttempts {
  internal fun settleIncompleteWork(
    request: FeatureTaskRuntimeRunRequest,
    state: FeatureTaskRuntimeRunState,
    recorder: FeatureTaskRuntimePhaseRecorder,
    observability: FeatureTaskRuntimeRunObservability,
    context: FixLoopBranchContext,
  ): PhaseOutcome? {
    val run = context.run
    val attempt = context.attempt
    val loop = context.loop
    val observability = context.observability
    val agentId = context.agentId
    loop.continuationSegmentCount += 1
    if (!FeatureTaskRuntimeRunLoopPhaseAttempts.recordIncompleteAttempt(recorder, run, loop.iteration, attempt)) {
      return blockInPhase(
        request,
        state,
        recorder,
        observability,
        PhaseBlockRequest(
          run = run,
          attemptCount = loop.iteration,
          reason = "Feature-task-runtime phase '${run.phaseId}' could not durably append its incomplete " +
            "implementation attempt (segment ${loop.continuationSegmentCount}). Continuing would lose the " +
            "continuation projection, so the run stops here rather than retrying against persistence.state that was " +
            "never persisted.",
          observability = observability,
          payload = BlockAndPersistPayload(fileManifest = attempt.fileManifest),
          failureDisposition = FeatureTaskRuntimeFailureDisposition.PROCESS_FAILURE,
        ),
      )
    }
    loop.iteration += 1
    loop.priorCorrection = null
    observability.continuation(
      run.phaseId,
      agentId,
      loop.iteration,
      loop.continuationSegmentCount,
      FeatureTaskRuntimeContinuationKind.IMPLEMENTATION_CONTINUATION,
    )
    return null
  }

  internal fun settleBoundaryBodyDelivery(
    observability: FeatureTaskRuntimeRunObservability,
    context: FixLoopBranchContext,
  ): PhaseOutcome? {
    val run = context.run
    val loop = context.loop
    val observability = context.observability
    val agentId = context.agentId
    loop.continuationSegmentCount += 1
    loop.iteration += 1
    loop.priorCorrection = null
    observability.continuation(
      run.phaseId,
      agentId,
      loop.iteration,
      loop.continuationSegmentCount,
      FeatureTaskRuntimeContinuationKind.VERIFICATION_BODY_DELIVERY,
    )
    return null
  }

  internal fun settleAuditRetry(
    observability: FeatureTaskRuntimeRunObservability,
    session: FeatureTaskRuntimeRunLoopSession,
    context: FixLoopBranchContext,
  ): PhaseOutcome? {
    val run = context.run
    val attempt = context.attempt
    val loop = context.loop
    val observability = context.observability
    val agentId = context.agentId
    val focusHint = requireNotNull(attempt.auditRetryFocusHint)
    session.transitionAuditRetryFocusHint(focusHint)
    loop.continuationSegmentCount += 1
    loop.iteration += 1
    loop.priorCorrection = null
    observability.continuation(
      run.phaseId,
      agentId,
      loop.iteration,
      loop.continuationSegmentCount,
      FeatureTaskRuntimeContinuationKind.AUDIT_AC_RETRY,
    )
    return null
  }

  internal fun settleFindingsOwed(
    request: FeatureTaskRuntimeRunRequest,
    state: FeatureTaskRuntimeRunState,
    recorder: FeatureTaskRuntimePhaseRecorder,
    observability: FeatureTaskRuntimeRunObservability,
    context: FixLoopBranchContext,
  ): PhaseOutcome? {
    val run = context.run
    val attempt = context.attempt
    val loop = context.loop
    val observability = context.observability
    val agentId = context.agentId
    val refs = requireNotNull(attempt.findingsOwedRefs)
    val blockReason = when (requireNotNull(attempt.findingsOwedKind)) {
      FindingsOwedKind.OMITTED -> FeatureTaskRuntimeAttemptBudgets.findingCoverageBlockReason(
        run.phaseId,
        refs,
        loop.priorUnaccountedFindings,
      )
      FindingsOwedKind.UNRESOLVED -> FeatureTaskRuntimeAttemptBudgets.unresolvedFindingBlockReason(
        run.phaseId,
        refs,
        loop.priorUnresolvedFindings,
        requireNotNull(attempt.findingsOwedDetail),
      )
    }
    blockReason?.let { reason ->
      return blockInPhase(
        request,
        state,
        recorder,
        observability,
        PhaseBlockRequest(
          run = run,
          attemptCount = loop.iteration,
          reason = reason,
          observability = observability,
          payload = BlockAndPersistPayload(fileManifest = attempt.fileManifest),
          failureDisposition = FeatureTaskRuntimeFailureDisposition.NEEDS_USER_ACTION,
        ),
      )
    }
    when (attempt.findingsOwedKind) {
      FindingsOwedKind.OMITTED -> loop.priorUnaccountedFindings = refs
      FindingsOwedKind.UNRESOLVED -> loop.priorUnresolvedFindings = loop.priorUnresolvedFindings + refs
      null -> Unit
    }
    loop.itemCoverageSegmentCount += 1
    loop.iteration += 1
    loop.priorCorrection = PriorAttemptCorrection.unaccountedFindings(
      requireNotNull(attempt.findingsOwedRetryReason),
    )
    observability.continuation(
      run.phaseId,
      agentId,
      loop.iteration,
      loop.itemCoverageSegmentCount,
      FeatureTaskRuntimeContinuationKind.ITEM_COVERAGE,
    )
    return null
  }

  internal fun settleMalformedOutput(
    request: FeatureTaskRuntimeRunRequest,
    state: FeatureTaskRuntimeRunState,
    recorder: FeatureTaskRuntimePhaseRecorder,
    observability: FeatureTaskRuntimeRunObservability,
    context: FixLoopBranchContext,
  ): PhaseOutcome? {
    val run = context.run
    val attempt = context.attempt
    val loop = context.loop
    val observability = context.observability
    val agentId = context.agentId
    if (FeatureTaskRuntimePhaseWorkflowDefinition.singleAgentSessionOnly(run.phaseId)) {
      return blockSingleAgentMalformedOutput(request, state, recorder, context)
    }
    loop.outputGateFailures += 1
    loop.malformedAttemptCount += 1
    val formatBlock = FeatureTaskRuntimeAttemptBudgets.outputGateBlockReason(
      run.phaseId,
      loop.outputGateFailures,
    )
    if (formatBlock == null) {
      loop.iteration += 1
      loop.priorCorrection = PriorAttemptCorrection.schemaGate(
        requireNotNull(attempt.schemaInvalidRetryReason),
        correctiveRepairContext = attempt.correctiveRepairContext,
      )
      observability.fixLoopIteration(run.phaseId, agentId, loop.iteration, loop.malformedAttemptCount)
      return null
    }
    return blockInPhase(
      request,
      state,
      recorder,
      observability,
      PhaseBlockRequest(
        run = run,
        attemptCount = loop.iteration,
        reason = withSchemaGateDetail(formatBlock, requireNotNull(attempt.schemaInvalidOperatorReason)),
        observability = observability,
        payload = BlockAndPersistPayload(
          fileManifest = attempt.fileManifest,
          rejectedOutput = attempt.rejectedOutput,
        ),
        failureDisposition = FeatureTaskRuntimeFailureDisposition.INVALID_OUTPUT,
      ),
    )
  }

  private fun blockSingleAgentMalformedOutput(
    request: FeatureTaskRuntimeRunRequest,
    state: FeatureTaskRuntimeRunState,
    recorder: FeatureTaskRuntimePhaseRecorder,
    context: FixLoopBranchContext,
  ): PhaseOutcome {
    val attempt = context.attempt
    return blockInPhase(
      request,
      state,
      recorder,
      context.observability,
      PhaseBlockRequest(
        run = context.run,
        attemptCount = context.loop.iteration,
        reason = withSchemaGateDetail(
          nonRetryingPhaseSchemaBlockReason(context.run.phaseId),
          requireNotNull(attempt.schemaInvalidOperatorReason),
        ),
        observability = context.observability,
        payload = BlockAndPersistPayload(
          fileManifest = attempt.fileManifest,
          rejectedOutput = attempt.rejectedOutput,
        ),
        failureDisposition = FeatureTaskRuntimeFailureDisposition.INVALID_OUTPUT,
      ),
    )
  }

  internal fun settleRetryableTerminal(
    request: FeatureTaskRuntimeRunRequest,
    state: FeatureTaskRuntimeRunState,
    recorder: FeatureTaskRuntimePhaseRecorder,
    observability: FeatureTaskRuntimeRunObservability,
    context: FixLoopBranchContext,
  ): PhaseOutcome? {
    val run = context.run
    val attempt = context.attempt
    val loop = context.loop
    val observability = context.observability
    val agentId = context.agentId
    if (!FeatureTaskRuntimePhaseWorkflowDefinition.retriesOnInvalidOutput(run.phaseId)) {
      return blockInPhase(
        request,
        state,
        recorder,
        observability,
        PhaseBlockRequest(
          run = run,
          attemptCount = loop.iteration,
          reason = "${nonRetryingPhaseSchemaBlockReason(run.phaseId)} " +
            requireNotNull(attempt.retryableOperatorReason),
          observability = observability,
          payload = BlockAndPersistPayload(fileManifest = attempt.fileManifest),
          failureDisposition = requireNotNull(attempt.retryableTerminalDisposition),
        ),
      )
    }
    val failedIteration = loop.semanticIteration
    loop.iteration += 1
    loop.semanticIteration += 1
    loop.priorCorrection =
      PriorAttemptCorrection.retryableTerminal(requireNotNull(attempt.retryableTerminalRetryReason))
    observability.continuation(
      run.phaseId,
      agentId,
      loop.iteration,
      failedIteration,
      FeatureTaskRuntimeContinuationKind.PROCESS_RETRY,
    )
    return null
  }
  internal fun blockInPhase(
    request: FeatureTaskRuntimeRunRequest,
    state: FeatureTaskRuntimeRunState,
    recorder: FeatureTaskRuntimePhaseRecorder,
    observability: FeatureTaskRuntimeRunObservability,
    block: PhaseBlockRequest,
  ): PhaseOutcome = FeatureTaskRuntimeRunLoopPhaseAttempts.blockAndPersistInPhase(
    request,
    state,
    recorder,
    null,
    phaseBlockArgs(
      block.run,
      block.attemptCount,
      block.reason,
      block.observability,
      block.payload,
    ).withDisposition(block.failureDisposition),
  )

  internal fun settleSemanticFailure(
    request: FeatureTaskRuntimeRunRequest,
    state: FeatureTaskRuntimeRunState,
    recorder: FeatureTaskRuntimePhaseRecorder,
    observability: FeatureTaskRuntimeRunObservability,
    context: FixLoopBranchContext,
  ): PhaseOutcome? {
    val run = context.run
    val attempt = context.attempt
    val loop = context.loop
    val observability = context.observability
    val agentId = context.agentId
    if (!FeatureTaskRuntimePhaseWorkflowDefinition.retriesOnInvalidOutput(run.phaseId)) {
      return blockNonRetryableSemanticFailure(request, state, recorder, context)
    }
    loop.outputGateFailures += 1
    FeatureTaskRuntimeAttemptBudgets.outputGateBlockReason(run.phaseId, loop.outputGateFailures)?.let { capReason ->
      return FeatureTaskRuntimeRunLoopPhaseAttempts.blockInPhase(
        request,
        state,
        recorder,
        observability,
        PhaseBlockRequest(
          run = run,
          attemptCount = loop.iteration,
          reason = withSchemaGateDetail(capReason, requireNotNull(attempt.retryableOperatorReason)),
          observability = observability,
          payload = BlockAndPersistPayload(
            fileManifest = attempt.fileManifest,
            rejectedOutput = attempt.rejectedOutput,
          ),
          failureDisposition = FeatureTaskRuntimeFailureDisposition.INVALID_OUTPUT,
        ),
      )
    }
    val failedIteration = loop.semanticIteration
    loop.iteration += 1
    loop.semanticIteration += 1
    loop.priorCorrection = attempt.semanticRetryReason?.let { retryReason ->
      PriorAttemptCorrection.schemaGate(
        retryReason,
        correctiveRepairContext = attempt.correctiveRepairContext,
      )
    }
    observability.fixLoopIteration(run.phaseId, agentId, loop.iteration, failedIteration)
    return null
  }

  private fun blockNonRetryableSemanticFailure(
    request: FeatureTaskRuntimeRunRequest,
    state: FeatureTaskRuntimeRunState,
    recorder: FeatureTaskRuntimePhaseRecorder,
    context: FixLoopBranchContext,
  ): PhaseOutcome {
    val run = context.run
    val attempt = context.attempt
    return blockInPhase(
      request,
      state,
      recorder,
      context.observability,
      PhaseBlockRequest(
        run = run,
        attemptCount = context.loop.iteration,
        reason = withSchemaGateDetail(
          nonRetryingPhaseSchemaBlockReason(run.phaseId),
          requireNotNull(attempt.retryableOperatorReason),
        ),
        observability = context.observability,
        payload = BlockAndPersistPayload(
          fileManifest = attempt.fileManifest,
          rejectedOutput = attempt.rejectedOutput,
        ),
        failureDisposition = FeatureTaskRuntimeFailureDisposition.INVALID_OUTPUT,
      ),
    )
  }

  internal fun durableNonOutputAttempts(
    state: FeatureTaskRuntimeRunState,
    run: PhaseRun,
  ): List<FeatureTaskRuntimeNonOutputAttempt> =
    state.trailingNonOutputAttempts(run.phaseId) { reason -> isProcessFailureBlockReason(run.phaseId, reason) }

  internal fun operatorReopenedPhase(session: FeatureTaskRuntimeRunLoopSession, phaseId: String): Boolean =
    session.operatorBlockRetry?.phaseId == phaseId && !session.operatorBlockRetryCompleted

  internal fun durableContinuationSegmentCount(recorder: FeatureTaskRuntimePhaseRecorder, run: PhaseRun): Int {
    if (!FeatureTaskRuntimePhaseWorkflowDefinition.isMutatingPhase(run.phaseId)) return 0
    val attempts = recorder.loadImplementationAttempts(run.request.workflowId)
      ?: return 0
    return attempts.count {
      it.phaseId == run.phaseId &&
        it.loopId == run.reentry?.loopId &&
        it.edgeIteration == run.reentry?.edgeIteration &&
        it.status == FeatureTaskRuntimeImplementationAttemptStatus.INCOMPLETE
    }
  }

  internal fun recordIncompleteAttempt(
    recorder: FeatureTaskRuntimePhaseRecorder,
    run: PhaseRun,
    iteration: Int,
    attempt: AttemptResult,
  ): Boolean {
    val normalized = attempt.incompleteWorkOutput ?: return false
    return recorder.recordIncompleteImplementationAttempt(
      FeatureTaskRuntimePhaseStateRequest(
        workflowId = run.request.workflowId,
        phaseId = run.phaseId,
        status = STATUS_RUNNING,
        attemptCount = iteration.coerceAtLeast(1),
        resolvedAgentId = run.resolvedAgent.resolvedAgentId,
        finished = false,
        normalizedOutput = normalized,
        loopId = run.reentry?.loopId,
        edgeIteration = run.reentry?.edgeIteration,
      ),
    )
  }

  internal fun FeatureTaskRuntimeRunLoopContext.blockAndPersist(args: BlockAndPersistArgs): PhaseOutcome =
    blockAndPersistCore(request, state, recorder, goalContinuationRecorder, args)

  internal fun blockAndPersist(
    request: FeatureTaskRuntimeRunRequest,
    state: FeatureTaskRuntimeRunState,
    recorder: FeatureTaskRuntimePhaseRecorder,
    goalContinuationRecorder: FeatureTaskRuntimeGoalContinuationRecorder?,
    args: BlockAndPersistArgs,
  ): PhaseOutcome = blockAndPersistCore(request, state, recorder, goalContinuationRecorder, args)

  internal fun blockAndPersistInPhase(
    request: FeatureTaskRuntimeRunRequest,
    state: FeatureTaskRuntimeRunState,
    recorder: FeatureTaskRuntimePhaseRecorder,
    goalContinuationRecorder: FeatureTaskRuntimeGoalContinuationRecorder?,
    args: BlockAndPersistInPhaseArgs,
  ): PhaseOutcome = blockAndPersistCore(
    request,
    state,
    recorder,
    goalContinuationRecorder,
    BlockAndPersistArgs(
      run = args.run,
      attemptCount = args.attemptCount,
      reason = args.reason,
      observability = args.observability,
      loopId = args.run.reentry?.loopId,
      edgeIteration = args.run.reentry?.edgeIteration,
      failureDisposition = args.failureDisposition,
      payload = args.payload,
    ),
  )

  private fun blockAndPersistCore(
    request: FeatureTaskRuntimeRunRequest,
    state: FeatureTaskRuntimeRunState,
    recorder: FeatureTaskRuntimePhaseRecorder,
    goalContinuationRecorder: FeatureTaskRuntimeGoalContinuationRecorder?,
    args: BlockAndPersistArgs,
  ): PhaseOutcome {
    val run = args.run
    val attemptCount = args.attemptCount
    val reason = args.reason
    val observability = args.observability
    val loopId = args.loopId
    val edgeIteration = args.edgeIteration
    val failureDisposition = args.failureDisposition
    val fileManifest = args.payload.fileManifest
    val outputArtifact = args.payload.outputArtifact
    val normalizedOutput = args.payload.normalizedOutput
    val repairEvidence = args.payload.repairEvidence
    val rejectedOutput = args.payload.rejectedOutput
    val childNeverLaunched = args.payload.childNeverLaunched
    val phaseState = FeatureTaskRuntimePhaseStateRequest(
      workflowId = run.request.workflowId,
      phaseId = run.phaseId,
      status = STATUS_BLOCKED,
      attemptCount = attemptCount.coerceAtLeast(1),
      resolvedAgentId = run.resolvedAgent.resolvedAgentId,
      finished = false,
      outputArtifact = normalizedOutput?.canonicalJson
        ?: outputArtifact
        ?: state.outputFor(run.phaseId)?.payload,
      rejectedOutput = rejectedOutput,
      normalizedOutput = normalizedOutput,
      repairEvidence = repairEvidence,
      blockedReason = reason,
      failureDisposition = failureDisposition,
      fileManifestBefore = fileManifest?.before.orEmpty(),
      fileManifestAfter = fileManifest?.after.orEmpty(),
      fileManifestIntroduced = fileManifest?.introduced.orEmpty(),
      loopId = loopId,
      edgeIteration = edgeIteration,
      reviewPassNumber = goalContinuationRecorder?.let { recorder ->
        FeatureTaskRuntimeRunLoopOutputPersistence.reviewPassNumber(
          request,
          recorder,
          run,
          state,
        )
      },
      launchOutcomeKnown = childNeverLaunched,
    )
    state.reserveReviewPass(phaseState.reviewPassNumber)
    recorder.recordPhaseState(
      phaseState,
    )
    observability.blocked(run.phaseId, run.resolvedAgent.resolvedAgentId, attemptCount.coerceAtLeast(1), reason)
    return PhaseOutcome.blocked(reason)
  }

  internal fun FeatureTaskRuntimeRunLoopContext.pauseAndPersistInPhase(
    args: PauseAndPersistInPhaseArgs,
  ): PhaseOutcome {
    val run = args.run
    val attemptCount = args.attemptCount
    val reason = args.reason
    val observability = args.observability
    val fileManifest = args.fileManifest
    val attempt = attemptCount.coerceAtLeast(1)
    if (isGoalContinuationRun(request)) {
      goalContinuationRecorder.recordGoalContinuationState(
        GoalContinuationStateRecordRequest(
          workflowId = request.workflowId,
          workflowStatus = STATUS_PAUSED,
        ),
      )
    }
    recorder.recordPhaseState(
      FeatureTaskRuntimePhaseStateRequest(
        workflowId = request.workflowId,
        phaseId = run.phaseId,
        status = STATUS_PAUSED,
        attemptCount = attempt,
        resolvedAgentId = run.resolvedAgent.resolvedAgentId,
        finished = false,
        outputArtifact = state.outputFor(run.phaseId)?.payload,
        blockedReason = reason,
        failureDisposition = FeatureTaskRuntimeFailureDisposition.RETRYABLE,
        fileManifestBefore = fileManifest?.before.orEmpty(),
        fileManifestAfter = fileManifest?.after.orEmpty(),
        fileManifestIntroduced = fileManifest?.introduced.orEmpty(),
        loopId = run.reentry?.loopId,
        edgeIteration = run.reentry?.edgeIteration,
        launchOutcomeKnown = false,
      ),
    )
    observability.paused(run.phaseId, run.resolvedAgent.resolvedAgentId, attempt, reason)
    FeatureTaskRuntimeRunLoopPlanningBranch.pauseAt(
      PauseAtArgs(
        request = request,
        state = state,
        session = session,
        phaseId = run.phaseId,
        reason = reason,
        resumableStep = run.phaseId,
      ),
    )
    return PhaseOutcome.paused(reason)
  }

  internal fun FeatureTaskRuntimeRunLoopContext.blockAndPersistInPhase(
    args: BlockAndPersistInPhaseArgs,
  ): PhaseOutcome = blockAndPersist(
    BlockAndPersistArgs(
      run = args.run,
      attemptCount = args.attemptCount,
      reason = args.reason,
      observability = args.observability,
      loopId = args.run.reentry?.loopId,
      edgeIteration = args.run.reentry?.edgeIteration,
      failureDisposition = args.failureDisposition,
      payload = args.payload,
    ),
  )

  internal fun FeatureTaskRuntimeRunLoopContext.settleRecordRejection(args: SettleRecordRejectionArgs): PhaseOutcome {
    val run = args.run
    val state = args.state
    val iteration = args.iteration
    val observability = args.observability
    val rejection = args.rejection
    val regeneration = FeatureTaskRuntimeRunLoopPhaseAttempts.recordRejectionRegenerationEdge(transitions, run.phaseId)
    if (regeneration == null) {
      return FeatureTaskRuntimeRunLoopRecordRejection.blockUnattributableRecordRejection(
        request,
        state,
        recorder,
        observability,

        UnattributableRecordRejectionArgs(
          context = PhaseAttemptContext(run, state, iteration, observability),
          rejection = rejection,
          producer = FeatureTaskRuntimePhaseWorkflowDefinition.REGENERATION_PRODUCER_BY_CONSUMER[run.phaseId],
        ),
      )
    }
    val attemptContext = PhaseAttemptContext(run, state, iteration, observability)
    val evidenceResolution = FeatureTaskRuntimeRunLoopPhaseAttempts
      .readProducerEvidenceForRecordRejection(
        request,
        state,
        recorder,
        observability,
        ProducerEvidenceRecordRejectionArgs(
          context = attemptContext,
          producer = regeneration.producer,
          consumer = run.phaseId,
        ),
      )
    return when (evidenceResolution) {
      is FeatureTaskRuntimeRunLoopPhaseAttempts
        .RecordRejectionEvidenceResolution.Settled,
      -> evidenceResolution.outcome
      is FeatureTaskRuntimeRunLoopPhaseAttempts.RecordRejectionEvidenceResolution.Ready ->
        FeatureTaskRuntimeRunLoopPhaseAttempts.quarantineRecordRejection(
          request,
          state,
          recorder,
          QuarantineRecordRejectionArgs(
            context = attemptContext,
            rejection = rejection,
            regeneration = regeneration,
            producerEvidence = evidenceResolution.evidence,
          ),
        )
    }
  }

  internal data class RecordRejectionRegenerationEdge(
    val producer: String,
    val edge: FeatureTaskRuntimeBackwardEdge,
  )

  internal fun recordRejectionRegenerationEdge(
    transitions: FeatureTaskRuntimeTransitionDeclaration,
    consumer: String,
  ): FeatureTaskRuntimeRunLoopPhaseAttempts.RecordRejectionRegenerationEdge? {
    val producer = FeatureTaskRuntimePhaseWorkflowDefinition.REGENERATION_PRODUCER_BY_CONSUMER[consumer] ?: return null
    val edge = transitions.backwardEdges.firstOrNull {
      it.fromPhaseId == consumer && it.destinationPhaseId == producer &&
        it.triggeringVerdict == FeatureTaskRuntimeVerdict.RECORD_REJECTED
    } ?: return null
    if (producer !in transitions.forwardPhaseIds) return null
    return FeatureTaskRuntimeRunLoopPhaseAttempts.RecordRejectionRegenerationEdge(producer, edge)
  }

  internal sealed interface RecordRejectionEvidenceResolution {
    data class Ready(val evidence: ProducerOutputEvidence) : RecordRejectionEvidenceResolution
    data class Settled(val outcome: PhaseOutcome) : RecordRejectionEvidenceResolution
  }

  internal fun readProducerEvidenceForRecordRejection(
    request: FeatureTaskRuntimeRunRequest,
    state: FeatureTaskRuntimeRunState,
    recorder: FeatureTaskRuntimePhaseRecorder,
    observability: FeatureTaskRuntimeRunObservability,
    args: ProducerEvidenceRecordRejectionArgs,
  ): RecordRejectionEvidenceResolution {
    val run = args.context.run
    val state = args.context.state
    val iteration = args.context.iteration
    val observability = args.context.observability
    val producer = args.producer
    val consumer = args.consumer
    val producingIteration =
      (state.outputFor(producer)?.iteration ?: state.recordFor(producer)?.attemptCount ?: 1)
        .coerceAtLeast(1)
    val producerAgentId = state.recordFor(producer)?.resolvedAgentId
      ?: return missingProducerAgentResolution(
        MissingProducerAgentResolutionArgs(
          request,
          state,
          recorder,
          run,
          iteration,
          consumer,
          producer,
          observability,
        ),
      )
    return when (
      val producerRead = recorder.producerOutput(
        ProducerOutputQueryArgs(
          workflowId = request.workflowId,
          phaseId = producer,
          attempt = producingIteration,
          agentId = producerAgentId,
          generation = state.evidenceGeneration(producer),
        ),
      )
    ) {
      is FeatureTaskRuntimeProducerOutputRead.Found ->
        RecordRejectionEvidenceResolution.Ready(producerRead.evidence)
      is FeatureTaskRuntimeProducerOutputRead.Absent,
      is FeatureTaskRuntimeProducerOutputRead.Unreadable,
      -> RecordRejectionEvidenceResolution.Settled(
        missingProducerEvidenceBlock(
          request,
          state,
          recorder,
          observability,
          MissingProducerEvidenceBlockArgs(
            run = run,
            iteration = iteration,
            consumer = consumer,
            producer = producer,
            producingIteration = producingIteration,
            producerRead = producerRead,
            observability = observability,
          ),
        ),
      )
    }
  }

  private fun missingProducerAgentResolution(
    args: MissingProducerAgentResolutionArgs,
  ): RecordRejectionEvidenceResolution = RecordRejectionEvidenceResolution.Settled(
    missingProducerAgentBlock(
      args.request,
      args.state,
      args.recorder,
      args.observability,
      MissingProducerAgentBlockArgs(
        args.run,
        args.iteration,
        args.consumer,
        args.producer,
        args.observability,
      ),
    ),
  )

  private fun missingProducerAgentBlock(
    request: FeatureTaskRuntimeRunRequest,
    state: FeatureTaskRuntimeRunState,
    recorder: FeatureTaskRuntimePhaseRecorder,
    observability: FeatureTaskRuntimeRunObservability,
    args: MissingProducerAgentBlockArgs,
  ): PhaseOutcome = FeatureTaskRuntimeRunLoopPhaseAttempts.blockInPhase(
    request,
    state,
    recorder,
    observability,

    PhaseBlockRequest(
      run = args.run,
      attemptCount = args.iteration,
      reason = "Feature-task-runtime phase '${args.consumer}' rejected the durable record produced by " +
        "'${args.producer}', but the producing phase's resolved agent is unavailable, so exact raw " +
        "evidence cannot be scoped to a producer. The run blocks instead of fabricating a " +
        "rejected-output diagnostic.",
      observability = args.observability,
      failureDisposition = FeatureTaskRuntimeFailureDisposition.NEEDS_USER_ACTION,
    ),
  )

  private data class MissingProducerEvidenceBlockArgs(
    val run: PhaseRun,
    val iteration: Int,
    val consumer: String,
    val producer: String,
    val producingIteration: Int,
    val producerRead: FeatureTaskRuntimeProducerOutputRead,
    val observability: FeatureTaskRuntimeRunObservability,
  )

  private fun missingProducerEvidenceBlock(
    request: FeatureTaskRuntimeRunRequest,
    state: FeatureTaskRuntimeRunState,
    recorder: FeatureTaskRuntimePhaseRecorder,
    observability: FeatureTaskRuntimeRunObservability,
    args: MissingProducerEvidenceBlockArgs,
  ): PhaseOutcome {
    val evidenceClause = if (args.producerRead is FeatureTaskRuntimeProducerOutputRead.Unreadable) {
      "retained evidence for attempt ${args.producingIteration} exists and the diagnostic store " +
        "refused it (${args.producerRead.failureClass.wireValue}). The run blocks instead of " +
        "fabricating a rejected-output diagnostic from normalized workflow persistence.state."
    } else {
      "no retained evidence exists for attempt ${args.producingIteration}. The run blocks instead " +
        "of fabricating a rejected-output diagnostic from normalized workflow persistence.state."
    }
    return FeatureTaskRuntimeRunLoopPhaseAttempts.blockInPhase(
      request,
      state,
      recorder,
      observability,
      PhaseBlockRequest(
        run = args.run,
        attemptCount = args.iteration,
        reason = "Feature-task-runtime phase '${args.consumer}' rejected the durable record " +
          "produced by '${args.producer}', but $evidenceClause",
        observability = args.observability,
        failureDisposition = FeatureTaskRuntimeFailureDisposition.NEEDS_USER_ACTION,
      ),
    )
  }

  internal fun quarantineRecordRejection(
    request: FeatureTaskRuntimeRunRequest,
    state: FeatureTaskRuntimeRunState,
    recorder: FeatureTaskRuntimePhaseRecorder,
    args: QuarantineRecordRejectionArgs,
  ): PhaseOutcome = quarantineRecordRejectionBody(
    request,
    state,
    recorder,

    args,
  )

  internal fun quarantineRecordRejectionBody(
    request: FeatureTaskRuntimeRunRequest,
    state: FeatureTaskRuntimeRunState,
    recorder: FeatureTaskRuntimePhaseRecorder,
    args: QuarantineRecordRejectionArgs,
  ): PhaseOutcome {
    val run = args.context.run
    val state = args.context.state
    val iteration = args.context.iteration
    val rejection = args.rejection
    val regeneration = args.regeneration
    val producerEvidence = args.producerEvidence
    val consumer = run.phaseId
    val producer = regeneration.producer
    val producingIteration =
      (state.outputFor(producer)?.iteration ?: state.recordFor(producer)?.attemptCount ?: 1)
        .coerceAtLeast(1)
    val diagnosticWrite = writeQuarantineRejectedOutput(
      state,
      recorder,
      WriteQuarantineRejectedOutputArgs(
        run,
        producingIteration,
        rejection,
        producer,

        producerEvidence,
      ),
    )
    appendQuarantineEntryForRejection(
      request,
      recorder,
      QuarantineEntryWriteArgs(
        consumer = consumer,
        producer = producer,
        producingIteration = producingIteration,
        rejection = rejection,
        regenerationAttempt = (state.edgeIterationCount(regeneration.edge.loopId) + 1).coerceAtLeast(1),
        iteration = iteration,
        diagnosticWrite = diagnosticWrite,
        producerEvidence = producerEvidence,
      ),
    )
    return PhaseOutcome.regenerateProducer(producer)
  }

  private fun writeQuarantineRejectedOutput(
    state: FeatureTaskRuntimeRunState,
    recorder: FeatureTaskRuntimePhaseRecorder,
    args: WriteQuarantineRejectedOutputArgs,
  ): FeatureTaskRuntimeRejectedOutputWrite {
    val run = args.run
    val producingIteration = args.producingIteration
    val rejection = args.rejection
    val producer = args.producer
    val producerEvidence = args.producerEvidence
    val rejectedPayload = producerEvidence.payload ?: byteArrayOf()
    return FeatureTaskRuntimeRunLoopAttemptSettlement.recordRejectedOutput(
      state,
      recorder,
      RecordRejectedOutputArgs(
        run = run,
        iteration = producingIteration,
        rule = "reconciliation-${rejection.rejectionClass}",
        reason = FeatureTaskRuntimeRunLoopRecordRejection.retryRejectionReason(
          FeatureTaskRuntimeRunLoopRecordRejection.payloadFreeRejectionReason(
            "reconciliation-${rejection.rejectionClass}",
            FeatureTaskRuntimeRunLoopRecordRejection.rejectionPath(rejection.rejectionDetail),
          ),
          rejection.rejectionDetail,
        ),
        captured = CapturedPhaseOutput(
          text = rejectedPayload.decodeToString(),
          bytes = rejectedPayload,
          truncated = producerEvidence.payload == null,
          byteSize = producerEvidence.byteSize,
          sha256 = producerEvidence.sha256,
        ),
        targeting = FeatureTaskRuntimeRunLoopAttemptSettlement.rejectedOutputTargeting(
          defaultRejectedOutputTargetingArgs(
            run,
            RejectedOutputTargetingOverrides(
              phaseId = producer,
              agentId = producerEvidence.agentId,
              model = producerEvidence.model,
              path = FeatureTaskRuntimeRunLoopRecordRejection.rejectionPath(rejection.rejectionDetail),
              repairTurn = producerEvidence.repairTurn,
            ),
          ),
        ),
      ),
    )
  }

  private data class QuarantineEntryWriteArgs(
    val consumer: String,
    val producer: String,
    val producingIteration: Int,
    val rejection: RecordRejection,
    val regenerationAttempt: Int,
    val iteration: Int,
    val diagnosticWrite: FeatureTaskRuntimeRejectedOutputWrite,
    val producerEvidence: ProducerOutputEvidence,
  )

  private fun appendQuarantineEntryForRejection(
    request: FeatureTaskRuntimeRunRequest,
    recorder: FeatureTaskRuntimePhaseRecorder,
    args: QuarantineEntryWriteArgs,
  ) {
    recorder.appendQuarantineEntry(
      request.workflowId,
      FeatureTaskRuntimeQuarantineEntry(
        producingPhaseId = args.producer,
        consumingPhaseId = args.consumer,
        producingIteration = args.producingIteration,
        rejectionClass = args.rejection.rejectionClass,
        rejectionDetail = FeatureTaskRuntimeRunLoopRecordRejection.payloadFreeRejectionReason(
          "reconciliation-${args.rejection.rejectionClass}",
          FeatureTaskRuntimeRunLoopRecordRejection.rejectionPath(args.rejection.rejectionDetail),
        ),
        regenerationAttempt = args.regenerationAttempt,
        quarantinedAtIteration = args.iteration.coerceAtLeast(1),
        diagnosticIdentity =
        (args.diagnosticWrite as? FeatureTaskRuntimeRejectedOutputWrite.Written)?.identity,
        rejectedRecordByteSize = args.producerEvidence.byteSize,
        rejectedRecordSha256 = args.producerEvidence.sha256,
        diagnosticDegraded = args.diagnosticWrite is FeatureTaskRuntimeRejectedOutputWrite.Degraded,
      ),
    )
  }
}
