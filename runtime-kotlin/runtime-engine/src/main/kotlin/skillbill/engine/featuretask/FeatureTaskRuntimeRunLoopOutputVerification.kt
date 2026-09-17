package skillbill.engine.featuretask

import skillbill.application.reviewevidence.FeatureTaskRuntimeSharedReviewEvidenceResolver
import skillbill.application.reviewevidence.model.FeatureTaskRuntimeSharedReviewEvidenceResolved
import skillbill.contracts.JsonCodec
import skillbill.contracts.SharedPayloadKeys
import skillbill.contracts.workflow.ValidationEvidencePayloadKeys
import skillbill.engine.featuretask.model.FeatureTaskRuntimeFindingBoundaryMemoryRequest
import skillbill.engine.featuretask.model.FeatureTaskRuntimeFindingBoundaryMemorySection
import skillbill.engine.featuretask.model.FeatureTaskRuntimeImplementationContinuation
import skillbill.engine.featuretask.model.FeatureTaskRuntimeRunRequest
import skillbill.error.InvalidFeatureTaskRuntimeHandoffProjectionError
import skillbill.error.InvalidFeatureTaskRuntimePhaseBriefingFramingError
import skillbill.error.InvalidFeatureTaskRuntimePhaseOutputSchemaError
import skillbill.goalrunner.subtaskreview.GoalSubtaskReviewStructuredFindingsParse
import skillbill.ports.workflow.gitops.model.WorkflowGitOperationResult
import skillbill.ports.workflow.gitops.repositoryCheckpointFingerprint
import skillbill.ports.workflow.gitops.repositoryFingerprint
import skillbill.ports.workflow.gitops.repositoryOwnedPaths
import skillbill.ports.workflow.gitops.runtimePhaseChangedPathsBetweenCommits
import skillbill.review.model.ReviewFindingVerdict
import skillbill.workflow.model.WorkflowStepStatus
import skillbill.workflow.model.workflowStepStatus
import skillbill.workflow.taskruntime.FeatureTaskRuntimeHandoffContract
import skillbill.workflow.taskruntime.FeatureTaskRuntimePhaseOutputValidator
import skillbill.workflow.taskruntime.FeatureTaskRuntimePhaseWorkflowDefinition
import skillbill.workflow.taskruntime.FeatureTaskRuntimeWorkflowArtifactMap
import skillbill.workflow.taskruntime.envelopeWireMap
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeFailureDisposition
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeFindingVerificationDisposition
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeHandoffAssemblyRequest
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeHandoffSourceRef
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimePhaseOutput
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimePhaseOutputFormat
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimePhaseOutputRepairEvidence
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimePhaseOutputRepairOperation
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimePhaseOutputSourceLocation
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeRepositoryCheckpoint
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeRepositoryCheckpointPolicy
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeVerdict
import skillbill.workflow.taskruntime.model.NormalizedFeatureTaskRuntimePhaseOutput
import skillbill.workflow.taskruntime.model.requireAcceptedOutput
import skillbill.workflow.taskruntime.model.validateDispositionCoverage
import skillbill.workflow.taskruntime.toWorkflowArtifactMap

object FeatureTaskRuntimeRunLoopOutputVerification {
  internal fun attestAbsentGateValidationReceipt(
    outputValidator: FeatureTaskRuntimePhaseOutputValidator,
    run: PhaseRun,
    normalizedOutput: NormalizedFeatureTaskRuntimePhaseOutput,
  ): NormalizedFeatureTaskRuntimePhaseOutput {
    val eligible = run.agentRunValidateFallback &&
      run.phaseId == FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_VALIDATE &&
      (normalizedOutput.envelopeWireMap()[SharedPayloadKeys.STATUS] as? String)
        .workflowStepStatus() == WorkflowStepStatus.COMPLETED
    if (!eligible) return normalizedOutput
    val produced = JsonCodec.anyToStringAnyMap(normalizedOutput.envelopeWireMap()[SharedPayloadKeys.PRODUCED_OUTPUTS])
      ?.toMutableMap()
      ?: return normalizedOutput
    val validationResult = JsonCodec.anyToStringAnyMap(
      produced[ValidationEvidencePayloadKeys.VALIDATION_RESULT],
    )
      ?.toMutableMap()
      ?: return normalizedOutput
    validationResult["gate_run_count"] = 0
    validationResult["gate_runs"] = emptyList<Any?>()
    validationResult.remove("suppression_justifications")
    produced[ValidationEvidencePayloadKeys.VALIDATION_RESULT] = validationResult
    val envelope = normalizedOutput.envelopeWireMap().toMutableMap()
    envelope[SharedPayloadKeys.PRODUCED_OUTPUTS] = produced
    return outputValidator.validatePhaseOutput(
      JsonCodec.mapToJsonString(envelope),
      sourceLabel = run.phaseId,
    ).requireAcceptedOutput(run.phaseId).normalizedOutput
  }

  internal fun implementationObligations(run: PhaseRun): FeatureTaskRuntimeImplementationObligations =
    FeatureTaskRuntimeImplementationObligations(
      plannedTaskIds = emptyList(),
      carriedRepairItemIds = emptyList(),
      loopId = run.reentry?.loopId,
      edgeIteration = run.reentry?.edgeIteration,
    )

  internal fun implementationContinuationFor(
    recorder: FeatureTaskRuntimePhaseRecorder,
    run: PhaseRun,
  ): FeatureTaskRuntimeImplementationContinuation? {
    if (!FeatureTaskRuntimePhaseWorkflowDefinition.isMutatingPhase(run.phaseId)) return null
    val attempts = recorder.loadImplementationAttempts(run.request.workflowId)
      ?: return null
    return featureTaskRuntimeImplementationContinuationFrom(run.phaseId, attempts, implementationObligations(run))
      ?.takeIf { it.priorValueSegments.isNotEmpty() }
  }

  internal fun completionProjectionRejection(context: FeatureTaskRuntimeRunLoopContext,
    args: CompletionProjectionRejectionArgs,
  ): Pair<String, String>? = with(context) {
    producerProjectionGateReason(
      args.run.phaseId,
      args.normalizedOutput.envelopeWireMap(),
      phaseGates.planningProjectionValidator,
    )?.let { "producer-projection" to it }
      ?: FeatureTaskRuntimeRunLoopOutputVerification.immediateConsumerProjectionGateReason(
        context = context,
        run = args.run,
        iteration = args.iteration,
        normalizedOutput = args.normalizedOutput,
        repairEvidence = args.repairEvidence,
        repositoryFingerprint = args.repositoryFingerprint,
      )?.let { "consumer-projection" to it }
      ?: FeatureTaskRuntimeRunLoopOutputVerification.outputVerificationGateReason(
        state,
        recorder,
        phaseGates,
        args.run,
        args.normalizedOutput.envelopeWireMap(),
      )?.let { "output-verification" to it }
  }

  internal fun firstValidatedOutputRejection(
    phaseId: String,
    outputMap: FeatureTaskRuntimeWorkflowArtifactMap,
  ): Pair<String, String>? = mutatingReconciliationGateReason(
    phaseId,
    outputMap,
  )?.let { "mutating-reconciliation" to it }

  internal fun immediateConsumerProjectionGateReason(
    context: FeatureTaskRuntimeRunLoopContext,
    run: PhaseRun,
    iteration: Int,
    normalizedOutput: NormalizedFeatureTaskRuntimePhaseOutput,
    repairEvidence: FeatureTaskRuntimePhaseOutputRepairEvidence?,
    repositoryFingerprint: String?,
  ): String? {
    with(context) {
    if (run.phaseId != FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_VALIDATE) return null
    if (run.validationGateFindings != null) return null
    val producerIndex = transitions.forwardPhaseIds.indexOf(run.phaseId)
    if (producerIndex < 0 || producerIndex == transitions.forwardPhaseIds.lastIndex) return null
    val consumerPhaseId = transitions.forwardPhaseIds[producerIndex + 1]
    val declaration = phaseDeclaration(
      consumerPhaseId,
      run.request.runInvariants.featureSize,
      FeatureTaskRuntimeRunLoopTransitions.qualityGateSelection(request),
    )
    val currentOutput = FeatureTaskRuntimePhaseOutput(
      phaseId = run.phaseId,
      iteration = iteration,
      payload = normalizedOutput.canonicalJson,
      normalizedOutput = normalizedOutput,
      repairEvidence = repairEvidence,
    )
    val outputs = state.outputs().filterNot { it.phaseId == run.phaseId } + currentOutput
    val resolvedFingerprint = repositoryFingerprint?.takeIf(String::isNotBlank)
      ?: phaseGates.gitOperations.repositoryFingerprint(run.request.repoRoot).value.takeIf(String::isNotBlank)
    val checkpoint = resolvedFingerprint
      ?.let(::FeatureTaskRuntimeRepositoryCheckpoint)
    val handoff = FeatureTaskRuntimeHandoffContract.assembleHandoff(
      FeatureTaskRuntimeHandoffAssemblyRequest(
        declaration = declaration,
        runInvariants = run.request.runInvariants,
        recordedOutputs = outputs,
        repositoryCheckpoint = checkpoint,
        expectedRepositoryCheckpoint = checkpoint,
        branchIdentity = session.resolvedBranch,
        baseBranch = recorder.loadResolvedBranch(run.request.workflowId)
          ?.baseBranch
          ?: "main",
      ),
    )
    return try {
      FeatureTaskRuntimePhaseBriefingAssembler.assemble(
        handoff,
        run.request.workflowId,
        phaseGates.planningProjectionValidator,
        run.request.agentAddonSelection,
      )
      null
    } catch (error: InvalidFeatureTaskRuntimeHandoffProjectionError) {
      "Phase '${run.phaseId}' reported 'completed' but its output cannot satisfy immediate consumer " +
        "'$consumerPhaseId': ${boundedSchemaGateDetail(error.message.orEmpty())}"
    } catch (error: InvalidFeatureTaskRuntimePhaseBriefingFramingError) {
      "Phase '${run.phaseId}' reported 'completed' but its output cannot frame immediate consumer " +
        "'$consumerPhaseId': ${boundedSchemaGateDetail(error.message.orEmpty())}"
    }

    }}

  internal fun recordedFindingVerdictsForFixHandoff(
    recorder: FeatureTaskRuntimePhaseRecorder,
    run: PhaseRun,
    state: FeatureTaskRuntimeRunState,
  ): List<ReviewFindingVerdict> {
    if (run.phaseId != FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_IMPLEMENT_FIX) {
      return emptyList()
    }
    val review = state.outputFor(FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_REVIEW) ?: return emptyList()
    val envelope = review.normalizedOutput?.envelopeWireMap()
      ?: JsonCodec.parseObjectOrNull(review.payload)
        ?.let { JsonCodec.jsonElementToValue(it) }
        ?.let(JsonCodec::anyToStringAnyMap)
      ?: return emptyList()
    return recorder.recordedFindingVerdicts(envelope)
  }

  internal fun resolveSharedReviewEvidence(
    phaseGates: FeatureTaskRuntimePhaseGates,
    run: PhaseRun,
    checkpoint: FeatureTaskRuntimeRepositoryCheckpoint?,
  ): FeatureTaskRuntimeSharedReviewEvidenceResolved? {
    val declared = run.declaration.projectionDeclarations.any {
      it.sourceRef == FeatureTaskRuntimeHandoffSourceRef.SharedReviewEvidence
    }
    if (!declared) return null
    return FeatureTaskRuntimeSharedReviewEvidenceResolver(
      phaseGates.sharedEvidenceResolver,
      phaseGates.diffResolver,
    ).resolve(run.request.repoRoot, run.request.workflowId, checkpoint, run.phaseId)
  }

  internal fun resolveRepositoryCheckpoint(
    args: RepositoryCheckpointResolutionArgs,
  ): FeatureTaskRuntimeRepositoryCheckpoint? = if (args.run.declaration.projectionDeclarations.none { projection ->
      projection.checkpointPolicy != FeatureTaskRuntimeRepositoryCheckpointPolicy.NOT_REQUIRED
    }
  ) {
    null
  } else {
    buildRepositoryCheckpoint(args)
  }

  internal fun completedPhaseRepositoryFingerprint(phaseGates: FeatureTaskRuntimePhaseGates, run: PhaseRun) = if (
    run.phaseId == FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_IMPLEMENT_FIX
  ) {
    phaseGates.gitOperations.repositoryFingerprint(run.request.repoRoot)
  } else {
    null
  }

  internal fun terminalOutputAttempt(
    request: FeatureTaskRuntimeRunRequest,
    state: FeatureTaskRuntimeRunState,
    recorder: FeatureTaskRuntimePhaseRecorder,
    observability: FeatureTaskRuntimeRunObservability,
    args: TerminalOutputAttemptArgs,
  ): AttemptResult {
    val run = args.run
    val iteration = args.iteration
    val reason = args.reason
    val outputMap = args.normalizedOutput.envelopeWireMap()
    val normalizedOutput = args.normalizedOutput
    val repairEvidence = args.repairEvidence
    val observability = args.observability
    val fileManifest = args.fileManifest
    val disposition = FeatureTaskRuntimePhaseSafetyPolicy.dispositionForTerminalOutput(run.phaseId, outputMap)
    val operatorTerminalQualityGate =
      !disposition.retryOnResume &&
        run.phaseId == FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_BUILD
    if (operatorTerminalQualityGate) {
      return AttemptResult.settled(
        FeatureTaskRuntimeRunLoopPhaseAttempts.blockInPhase(
          request,
          state,
          recorder,
          observability,
          PhaseBlockRequest(
            run = run,
            attemptCount = iteration,
            reason = reason,
            observability = observability,
            payload = BlockAndPersistPayload(fileManifest = fileManifest),
            failureDisposition = disposition,
          ),
        ),
      )
    }
    return if (
      disposition.retryOnResume &&
      FeatureTaskRuntimePhaseWorkflowDefinition.retriesOnInvalidOutput(run.phaseId)
    ) {
      AttemptResult.retryableTerminal(reason, fileManifest, disposition)
    } else {
      AttemptResult.settled(
        FeatureTaskRuntimeRunLoopPhaseAttempts.blockInPhase(
          request,
          state,
          recorder,
          observability,
          PhaseBlockRequest(
            run = run,
            attemptCount = iteration,
            reason = reason,
            observability = observability,
            payload = BlockAndPersistPayload(fileManifest = fileManifest),
            failureDisposition = disposition,
          ),
        ),
      )
    }
  }

  internal fun outputVerificationGateReason(
    state: FeatureTaskRuntimeRunState,
    recorder: FeatureTaskRuntimePhaseRecorder,
    phaseGates: FeatureTaskRuntimePhaseGates,
    run: PhaseRun,
    outputMap: FeatureTaskRuntimeWorkflowArtifactMap,
  ): String? = findingVerificationBoundaryDispositionGate(
    state,
    recorder,
    phaseGates,
    run,

    outputMap,
  )
    ?: auditRemovedVerdictGate(run.phaseId, outputMap)
    ?: FeatureTaskRuntimeVerificationGateReasons.reviewVerificationSignal(run.phaseId, outputMap)
    ?: FeatureTaskRuntimeVerificationGateReasons.findingVerificationDisposition(
      run.phaseId,
      outputMap,
      FeatureTaskRuntimeRunLoopOutputVerification.reviewFindingIdsForVerification(state, recorder),
    )

  private fun auditRemovedVerdictGate(phaseId: String, outputMap: FeatureTaskRuntimeWorkflowArtifactMap): String? {
    if (phaseId != FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_AUDIT) return null
    val wire = (outputMap[SharedPayloadKeys.VERDICT] as? String)?.trim()
    if (wire == FeatureTaskRuntimeVerdict.GAPS_FOUND.wireValue) {
      return "Feature-task-runtime verdict '${FeatureTaskRuntimeVerdict.GAPS_FOUND.wireValue}' is removed " +
        "(audit phase output); repair gaps in this session and emit satisfied."
    }
    return null
  }

  internal fun findingVerificationBoundarySections(
    state: FeatureTaskRuntimeRunState,
    recorder: FeatureTaskRuntimePhaseRecorder,
    phaseGates: FeatureTaskRuntimePhaseGates,
    run: PhaseRun,
  ): List<FeatureTaskRuntimeFindingBoundaryMemorySection> {
    val reviewOutput = state.outputFor(FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_REVIEW)
      ?.normalizedOutput?.envelopeWireMap()
    val recordedVerdicts = reviewOutput?.let {
      recorder.recordedFindingVerdicts(
        it,
      )
    }.orEmpty()
    val findings = reviewOutput?.let {
      GoalSubtaskReviewStructuredFindingsParse.structuredFindings(it, recordedVerdicts)
    }.orEmpty()
    return phaseGates.findingVerificationBoundaryMemory.sectionsForFindings(
      run.request.repoRoot,
      findings.mapNotNull { finding ->
        val findingId = finding.findingId?.takeIf(String::isNotBlank) ?: return@mapNotNull null
        FeatureTaskRuntimeFindingBoundaryMemoryRequest(
          findingId = findingId,
          findingPaths = FeatureTaskRuntimeRunLoopLaunch.findingPathsForBoundaryMemory(finding),
        )
      },
    )
  }

  internal fun findingVerificationBoundaryBodyDeliveryDecision(
    state: FeatureTaskRuntimeRunState,
    recorder: FeatureTaskRuntimePhaseRecorder,
    phaseGates: FeatureTaskRuntimePhaseGates,
    run: PhaseRun,
    outputMap: FeatureTaskRuntimeWorkflowArtifactMap,
  ): BoundaryBodyDeliveryDecision {
    FeatureTaskRuntimeRunLoopOutputVerification.verifyFindingsBoundaryContext(
      state,
      recorder,
      run,
      outputMap,
    )?.let { return it }
    val dispositions = FeatureTaskRuntimeOutputVerification.dispositionsFrom(outputMap)
    val sections = findingVerificationBoundarySections(state, recorder, phaseGates, run)
    FeatureTaskRuntimeRunLoopOutputVerification.verifyFindingsBoundaryValidationFailure(
      phaseGates,
      sections,
      dispositions,
    )?.let { return it }
    val selections = phaseGates.findingVerificationBoundaryMemory.selectionsRequiringBodyDelivery(
      sections,
      dispositions,
    )
    val delivered = if (selections.isEmpty()) {
      true
    } else {
      recorder.loadFindingVerificationBoundarySelection(
        run.request.workflowId,
      ) != null
    }
    if (delivered) return BoundaryBodyDeliveryDecision.NotApplicable
    recorder.persistFindingVerificationBoundarySelection(
      workflowId = run.request.workflowId,
      selections = selections,
    )
    recorder.persistFindingVerificationCheckpoint(
      workflowId = run.request.workflowId,
      dispositions = dispositions,
    )
    return BoundaryBodyDeliveryDecision.ContinueDecision.of(
      "Selected boundary headings recorded; re-read the briefing with resolved entry bodies and re-emit " +
        "finding_dispositions before verify_findings can settle.",
    )
  }

  internal fun findingVerificationBoundaryDispositionGate(
    state: FeatureTaskRuntimeRunState,
    recorder: FeatureTaskRuntimePhaseRecorder,
    phaseGates: FeatureTaskRuntimePhaseGates,
    run: PhaseRun,
    outputMap: FeatureTaskRuntimeWorkflowArtifactMap,
  ): String? = findingVerificationBoundaryDispositionGateImpl(
    state,
    recorder,
    phaseGates,
    run,

    outputMap,
  )

  internal fun findingVerificationBoundaryDispositionGateImpl(
    state: FeatureTaskRuntimeRunState,
    recorder: FeatureTaskRuntimePhaseRecorder,
    phaseGates: FeatureTaskRuntimePhaseGates,
    run: PhaseRun,
    outputMap: FeatureTaskRuntimeWorkflowArtifactMap,
  ): String? {
    val dispositions = FeatureTaskRuntimeRunLoopOutputVerification.verifyFindingsDispositionGateContext(
      state,
      recorder,
      run,

      outputMap,
    ) ?: return null
    val sections = findingVerificationBoundarySections(state, recorder, phaseGates, run)
    FeatureTaskRuntimeRunLoopOutputVerification.verifyFindingsDispositionGateValidationFailure(
      phaseGates,
      sections,
      dispositions,
    )?.let { return it }
    val persisted = recorder.loadFindingVerificationBoundarySelection(
      run.request.workflowId,
    )
    val memory = phaseGates.findingVerificationBoundaryMemory
    memory.validateBoundarySelectionsDelivered(sections, dispositions, persisted)?.let { return it }
    return if (persisted != null) {
      memory.validateDispositionBoundaryBodies(
        repoRoot = run.request.repoRoot,
        sections = sections,
        dispositions = dispositions,
        persistedSelections = persisted,
      )
    } else {
      null
    }
  }

  internal fun persistVerifyFindingsCheckpointIfPresent(
    recorder: FeatureTaskRuntimePhaseRecorder,
    run: PhaseRun,
    outputText: String,
  ) {
    if (run.phaseId != FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_VERIFY_FINDINGS) return
    val outputMap = JsonCodec.parseObjectOrNull(outputText)
      ?.let(JsonCodec::jsonElementToValue)
      ?.let(JsonCodec::anyToStringAnyMap)
      ?.toWorkflowArtifactMap()
      ?: return
    val dispositions = FeatureTaskRuntimeOutputVerification.dispositionsFrom(outputMap)
    if (dispositions.isEmpty()) return
    recorder.persistFindingVerificationCheckpoint(
      workflowId = run.request.workflowId,
      dispositions = dispositions,
    )
  }

  internal fun reviewFindingIdsForVerification(
    state: FeatureTaskRuntimeRunState,
    recorder: FeatureTaskRuntimePhaseRecorder,
  ): Set<String> {
    val reviewOutput = state.outputFor(FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_REVIEW)
      ?.normalizedOutput?.envelopeWireMap()
      ?: return emptySet()
    val recordedVerdicts = recorder.recordedFindingVerdicts(reviewOutput)
    return GoalSubtaskReviewStructuredFindingsParse.structuredFindings(reviewOutput, recordedVerdicts)
      .mapNotNull { it.findingId }
      .toSet()
  }

  internal fun structuralRepairEvidenceFromSchemaError(
    error: InvalidFeatureTaskRuntimePhaseOutputSchemaError,
  ): FeatureTaskRuntimePhaseOutputRepairEvidence? {
    val originalDigest = error.structuralRepairOriginalDigest
    val repairedDigest = error.structuralRepairRepairedDigest
    val format = error.structuralRepairFormat
    val operation = error.structuralRepairOperation
    val sourceLabel = error.structuralRepairSourceLabel
    val sourceOffset = error.structuralRepairSourceOffset
    val sourceLine = error.structuralRepairSourceLine
    val sourceColumn = error.structuralRepairSourceColumn
    if (
      listOf(
        originalDigest,
        repairedDigest,
        format,
        operation,
        sourceLabel,
        sourceOffset,
        sourceLine,
        sourceColumn,
      ).any { it == null }
    ) {
      return null
    }
    return FeatureTaskRuntimePhaseOutputRepairEvidence(
      format = FeatureTaskRuntimePhaseOutputFormat.fromWire(
        requireNotNull(format),
      ),
      originalDigest = requireNotNull(originalDigest),
      repairedDigest = requireNotNull(repairedDigest),
      operation = FeatureTaskRuntimePhaseOutputRepairOperation.fromWire(
        requireNotNull(operation),
      ),
      sourceLocation = FeatureTaskRuntimePhaseOutputSourceLocation(
        sourceLabel = requireNotNull(sourceLabel),
        offset = requireNotNull(sourceOffset),
        line = requireNotNull(sourceLine),
        column = requireNotNull(sourceColumn),
      ),
    )
  }

  internal fun persistAcceptedOutput(context: FeatureTaskRuntimeRunLoopContext, args: PersistAcceptedOutputArgs): AttemptResult {
    with(context) {
    val run = args.run
    val iteration = args.iteration
    val normalizedOutput = args.normalizedOutput
    val repairEvidence = args.repairEvidence
    val observability = args.observability
    val fileManifest = args.fileManifest
    val repositoryFingerprint = args.repositoryFingerprint
    val outputText = normalizedOutput.canonicalJson
    if (run.validationGateFindings != null) {
      return FeatureTaskRuntimeRunLoopOutputVerification.validationGatePersistedAttempt(
        run,
        iteration,
        normalizedOutput,
        repairEvidence,
        outputText,
      )
    }
    val reviewArgs = PhaseReviewPersistenceArgs(run, iteration, observability, fileManifest)
    if (FeatureTaskRuntimeRunLoopOutputPersistence.isGoalReviewRun(run)) {
      with(FeatureTaskRuntimeRunLoopOutputPersistence) {
        FeatureTaskRuntimeRunLoopOutputPersistence.ReviewOutputPersistenceContext(
          request = context.request,
          state = context.state,
          recorder = context.recorder,
          observability = observability,
          goalContinuationRecorder = context.goalContinuationRecorder,
        ).persistGoalReviewCompletion(
          reviewArgs,
          normalizedOutput,
          repairEvidence,
        )
      }?.let { outcome ->
        return AttemptResult.settled(outcome)
      }
    } else {
      FeatureTaskRuntimeRunLoopOutputVerification.persistStandardAcceptedOutput(context,
        PersistStandardAcceptedOutputArgs(
          accepted = PersistAcceptedOutputArgs(
            run = run,
            iteration = iteration,
            normalizedOutput = normalizedOutput,
            repairEvidence = repairEvidence,
            observability = observability,
            fileManifest = fileManifest,
            repositoryFingerprint = repositoryFingerprint,
          ),
          outputText = outputText,
        ),
      )?.let { return it }
    }
    observability.completedEvent(run.phaseId, run.resolvedAgent.resolvedAgentId, iteration)
    return completedAttemptResult(run, iteration, outputText, normalizedOutput, repairEvidence)

    }}

  internal fun buildRepositoryCheckpoint(
    args: RepositoryCheckpointResolutionArgs,
  ): FeatureTaskRuntimeRepositoryCheckpoint? {
    val run = args.run
    val resolvedBranchRecord = args.recorder.loadResolvedBranch(run.request.workflowId)
    args.session.transitionResolvedBranch(resolvedBranchRecord?.branch)
    val goalReviewState = args.goalContinuationRecorder.reviewState(run.request.workflowId)
    val revisions = FeatureTaskRuntimeRunLoopOutputVerification.resolveCheckpointRevisions(
      args.phaseGates,
      run = run,
      headRevision = resolvedBranchRecord?.branch?.takeIf(String::isNotBlank) ?: "HEAD",

      baseRevision = goalReviewState?.reviewBaseSha ?: resolvedBranchRecord?.reviewBaseSha,
    ) ?: return null
    val ownedPaths = resolveCheckpointOwnedPaths(
      args = args,
      persistedOwnedPaths = resolvedBranchRecord?.workflowOwnedPaths,

      baselineOwnedPaths = resolvedBranchRecord?.baselineOwnedPaths
        ?: goalReviewState?.baselineUntrackedPaths
        ?: resolvedBranchRecord?.baselineUntrackedPaths.orEmpty(),
      revisions = revisions,
    ) ?: return null
    val fingerprint = args.phaseGates.gitOperations.repositoryCheckpointFingerprint(
      run.request.repoRoot,
      revisions.base,
      revisions.head,
      ownedPaths,
    ).takeIf { it is WorkflowGitOperationResult.Ok }?.value?.takeIf(String::isNotBlank) ?: return null
    return FeatureTaskRuntimeRepositoryCheckpoint(
      fingerprint = fingerprint,
      baseRef = revisions.base,
      headRef = revisions.head,
      workingTreeOwnedPaths = ownedPaths,
    )
  }

  internal fun resolveCheckpointOwnedPaths(
    args: RepositoryCheckpointResolutionArgs,
    persistedOwnedPaths: List<String>?,
    baselineOwnedPaths: List<String>,
    revisions: CheckpointRevisions,
  ): List<String>? {
    val run = args.run
    val workingTreePaths = FeatureTaskRuntimeRunLoopOutputVerification.checkpointOwnedPaths(
      args.phaseGates,
      run,
      baselineOwnedPaths,
    ) ?: return null
    val committedPaths = revisions.base?.let { base ->
      args.phaseGates.gitOperations.runtimePhaseChangedPathsBetweenCommits(run.request.repoRoot, base, revisions.head)
        .takeIf { it is WorkflowGitOperationResult.Ok }
        ?.value
        ?.let(FeatureTaskRuntimePhaseSafetyPolicy::lineSeparatedPaths)
        ?: return null
    }.orEmpty()
    val durableInventory = persistedOwnedPaths.orEmpty().filter(String::isNotBlank)
    val discovered = if (args.session.checkpointOwnershipDecided && durableInventory.isNotEmpty()) {
      durableInventory
    } else {
      (durableInventory + workingTreePaths).distinct()
    }
    val inventory = reconcileCheckpointPathInventory(
      repoRoot = run.request.repoRoot,
      issueKey = run.request.issueKey,
      specReference = run.request.runInvariants.specReference,
      workflowId = run.request.workflowId,
      paths = (discovered + committedPaths).distinct(),
    ).sorted()
    return inventory.takeIf {
      args.recorder.recordWorkflowOwnedPaths(
        run.request.workflowId,
        inventory,
      )
    }
  }

  internal fun resolveCheckpointRevisions(
    phaseGates: FeatureTaskRuntimePhaseGates,
    run: PhaseRun,
    headRevision: String,
    baseRevision: String?,
  ): CheckpointRevisions? {
    val immutableHead = phaseGates.gitOperations.resolveCommit(run.request.repoRoot, headRevision)
      .takeIf { it is WorkflowGitOperationResult.Ok }?.value?.takeIf(String::isNotBlank)
      ?: phaseGates.gitOperations.headCommitSha(run.request.repoRoot)
        .takeIf { it is WorkflowGitOperationResult.Ok }?.value?.takeIf(String::isNotBlank)
      ?: return null
    val immutableBase = baseRevision?.let { revision ->
      phaseGates.gitOperations.resolveCommit(run.request.repoRoot, revision)
        .takeIf { it is WorkflowGitOperationResult.Ok }?.value?.takeIf(String::isNotBlank)
        ?: revision.takeIf { it.matches(Regex("^[0-9a-fA-F]{40,64}$")) }
    }
    if (baseRevision != null && immutableBase == null) return null
    return CheckpointRevisions(base = immutableBase, head = immutableHead)
  }

  internal fun checkpointOwnedPaths(
    phaseGates: FeatureTaskRuntimePhaseGates,
    run: PhaseRun,
    baselineOwnedPaths: List<String>,
  ): List<String>? {
    val owned = phaseGates.gitOperations.repositoryOwnedPaths(run.request.repoRoot)
    if (owned !is WorkflowGitOperationResult.Ok) return null
    val baseline = baselineOwnedPaths.toSet()
    val paths = owned.value.orEmpty()
      .split(OWNED_PATH_DELIMITER)
      .map(String::trim)
      .filter(String::isNotBlank)
      .filterNot { it in baseline }
      .filterNot { path -> isFeatureSpecPathForIssue(path, run.request.issueKey) }
      .distinct()
      .sorted()
    return paths
  }

  internal fun verifyFindingsBoundaryContext(
    state: FeatureTaskRuntimeRunState,
    recorder: FeatureTaskRuntimePhaseRecorder,
    run: PhaseRun,
    outputMap: FeatureTaskRuntimeWorkflowArtifactMap,
  ): BoundaryBodyDeliveryDecision? {
    if (run.phaseId != FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_VERIFY_FINDINGS) {
      return BoundaryBodyDeliveryDecision.NotApplicable
    }
    val dispositions = FeatureTaskRuntimeOutputVerification.dispositionsFrom(outputMap)
    if (dispositions.isEmpty()) return BoundaryBodyDeliveryDecision.NotApplicable
    if (validateDispositionCoverage(
        dispositions,
        FeatureTaskRuntimeRunLoopOutputVerification.reviewFindingIdsForVerification(state, recorder),
      ) != null
    ) {
      return BoundaryBodyDeliveryDecision.NotApplicable
    }
    return null
  }

  internal fun verifyFindingsBoundaryValidationFailure(
    phaseGates: FeatureTaskRuntimePhaseGates,
    sections: List<FeatureTaskRuntimeFindingBoundaryMemorySection>,
    dispositions: List<FeatureTaskRuntimeFindingVerificationDisposition>,
  ): BoundaryBodyDeliveryDecision? {
    val memory = phaseGates.findingVerificationBoundaryMemory
    memory.validateDispositionBoundaryContext(sections, dispositions)?.let {
      return BoundaryBodyDeliveryDecision.RejectDecision.of(it)
    }
    memory.validateDispositionBoundaryProvenance(sections, dispositions)?.let {
      return BoundaryBodyDeliveryDecision.RejectDecision.of(it)
    }
    return null
  }

  internal fun verifyFindingsDispositionGateContext(
    state: FeatureTaskRuntimeRunState,
    recorder: FeatureTaskRuntimePhaseRecorder,
    run: PhaseRun,
    outputMap: FeatureTaskRuntimeWorkflowArtifactMap,
  ): List<FeatureTaskRuntimeFindingVerificationDisposition>? {
    if (run.phaseId != FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_VERIFY_FINDINGS) return null
    val dispositions = FeatureTaskRuntimeOutputVerification.dispositionsFrom(outputMap)
    if (dispositions.isEmpty()) return null
    if (validateDispositionCoverage(
        dispositions,
        FeatureTaskRuntimeRunLoopOutputVerification.reviewFindingIdsForVerification(state, recorder),
      ) != null
    ) {
      return null
    }
    return dispositions
  }

  internal fun verifyFindingsDispositionGateValidationFailure(
    phaseGates: FeatureTaskRuntimePhaseGates,
    sections: List<FeatureTaskRuntimeFindingBoundaryMemorySection>,
    dispositions: List<FeatureTaskRuntimeFindingVerificationDisposition>,
  ): String? {
    val memory = phaseGates.findingVerificationBoundaryMemory
    memory.validateDispositionBoundaryContext(sections, dispositions)?.let { return it }
    memory.validateDispositionBoundaryProvenance(sections, dispositions)?.let { return it }
    return null
  }

  internal fun validationGatePersistedAttempt(
    run: PhaseRun,
    iteration: Int,
    normalizedOutput: NormalizedFeatureTaskRuntimePhaseOutput,
    repairEvidence: FeatureTaskRuntimePhaseOutputRepairEvidence?,
    outputText: String,
  ): AttemptResult = AttemptResult.settled(
    PhaseOutcome.completed(
      FeatureTaskRuntimePhaseOutput(
        run.phaseId,
        iteration,
        outputText,
        normalizedOutput,
        repairEvidence,
      ),
    ),
  )

  internal fun persistStandardAcceptedOutput(context: FeatureTaskRuntimeRunLoopContext,
    args: PersistStandardAcceptedOutputArgs,
  ): AttemptResult? {
    with(context) {
    val accepted = args.accepted
    val run = accepted.run
    val iteration = accepted.iteration
    val normalizedOutput = accepted.normalizedOutput
    val repairEvidence = accepted.repairEvidence
    val observability = accepted.observability
    val fileManifest = accepted.fileManifest
    val repositoryFingerprint = accepted.repositoryFingerprint
    val outputText = args.outputText
    FeatureTaskRuntimeRunLoopOutputVerification.persistRejectedVerificationFindingsIfNeeded(context, run, normalizedOutput)
    val persisted = recorder.recordCompletedPhase(
      FeatureTaskRuntimeRunLoopOutputPersistence.phaseStateRequest(
        request,
        state,
        goalContinuationRecorder,
        PhaseStateRequestArgs(
          write = PhaseStateWriteArgs(
            run = run,
            iteration = iteration,
            status = STATUS_COMPLETED,
            finished = true,
            outputArtifact = outputText,
          ),
          extras = PhaseStateRequestAttachments(
            fileManifest = fileManifest,
            normalizedOutput = normalizedOutput,
            repairEvidence = repairEvidence,
            repositoryFingerprint = repositoryFingerprint,
          ),
        ),
      ),
    )
    if (!persisted) {
      return AttemptResult.settled(
        FeatureTaskRuntimeRunLoopPhaseAttempts.blockInPhase(
          request,
          state,
          recorder,
          observability,
          PhaseBlockRequest(
            run = run,
            attemptCount = iteration,
            reason = "Validated phase output could not be persisted to the authoritative workflow record.",
            observability = observability,
            payload = BlockAndPersistPayload(fileManifest = fileManifest),
            failureDisposition = FeatureTaskRuntimeFailureDisposition.PROCESS_FAILURE,
          ),
        ),
      )
    }
    return null

    }}

  private fun persistRejectedVerificationFindingsIfNeeded(context: FeatureTaskRuntimeRunLoopContext,
    run: PhaseRun,
    normalizedOutput: NormalizedFeatureTaskRuntimePhaseOutput,
  ) {
    with(context) {
    if (run.phaseId != FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_VERIFY_FINDINGS) return
    FeatureTaskRuntimeRunLoopOutputPersistence.persistRejectedVerificationFindings(
      PersistRejectedVerificationFindingsArgs(
        state,
        recorder,
        goalContinuationRecorder,
        diagnostics,
        run,
        normalizedOutput.envelopeWireMap(),
      ),
    )

    }}

  internal fun completedAttemptResult(
    run: PhaseRun,
    iteration: Int,
    outputText: String,
    normalizedOutput: NormalizedFeatureTaskRuntimePhaseOutput,
    repairEvidence: FeatureTaskRuntimePhaseOutputRepairEvidence?,
  ): AttemptResult = AttemptResult.settled(
    PhaseOutcome.completed(
      FeatureTaskRuntimePhaseOutput(
        run.phaseId,
        iteration,
        outputText,
        normalizedOutput,
        repairEvidence,
      ),
    ),
  )
}
