package skillbill.engine.featuretask.slot.attempt

import skillbill.application.decomposition.baseBranch
import skillbill.application.decomposition.branchName
import skillbill.application.review.service.RuntimeOwnedReviewMode
import skillbill.application.review.spec.toProjectionPayload
import skillbill.contracts.JsonCodec
import skillbill.engine.featuretask.lifecycle.continuation.isGoalContinuationRun
import skillbill.engine.featuretask.model.phase.FeatureTaskRuntimePhaseLaunchBriefing
import skillbill.engine.featuretask.model.phase.FeatureTaskRuntimePhaseSettlementTarget
import skillbill.engine.featuretask.model.phase.FeatureTaskRuntimeProjectionRejection
import skillbill.engine.featuretask.persist.workflowArtifactEntryMap
import skillbill.engine.featuretask.phase.briefing.FeatureTaskRuntimePhaseBriefingAssembler
import skillbill.engine.featuretask.phase.core.FeatureTaskRuntimePhaseGates
import skillbill.engine.featuretask.phase.core.toMeasurementFailureClassification
import skillbill.engine.featuretask.phase.prompt.compose.FeatureTaskRuntimePhasePromptComposeInputs
import skillbill.engine.featuretask.phase.prompt.compose.FeatureTaskRuntimePhasePromptComposer
import skillbill.engine.featuretask.phase.prompt.directives.PriorAttemptCorrection
import skillbill.engine.featuretask.phase.record.FeatureTaskRuntimePhaseRecorder
import skillbill.engine.featuretask.review.finding.promptSection
import skillbill.engine.featuretask.review.finding.resolvedBodiesPromptSection
import skillbill.engine.featuretask.runloop.core.DeclaredLaunchArgs
import skillbill.engine.featuretask.runloop.core.FeatureTaskRuntimeRunLoopContext
import skillbill.engine.featuretask.runloop.core.FeatureTaskRuntimeRunLoopSession
import skillbill.engine.featuretask.runloop.core.LaunchMeasurementContextReady
import skillbill.engine.featuretask.runloop.core.LaunchPreparation
import skillbill.engine.featuretask.runloop.core.LaunchPreparationRejected
import skillbill.engine.featuretask.runloop.core.LaunchPreparationRejectedArgs
import skillbill.engine.featuretask.runloop.core.LaunchRejectionMeasurementContext
import skillbill.engine.featuretask.runloop.core.LaunchSeamRejectionArgs
import skillbill.engine.featuretask.runloop.core.PhaseRun
import skillbill.engine.featuretask.runloop.core.PreparedLaunch
import skillbill.engine.featuretask.runloop.core.PreparedLaunchReady
import skillbill.engine.featuretask.runloop.core.RepositoryCheckpointResolutionArgs
import skillbill.engine.featuretask.runloop.core.qualityGateSelection
import skillbill.engine.featuretask.runloop.core.resolveLaunchRejectionAttribution
import skillbill.engine.featuretask.runloop.output.FeatureTaskRuntimeRunLoopOutputVerification
import skillbill.engine.featuretask.runloop.phase.FeatureTaskRuntimeRunLoopPhaseBlocking
import skillbill.engine.featuretask.runloop.settlement.FeatureTaskRuntimeRunLoopValidationScope
import skillbill.engine.featuretask.runloop.state.FeatureTaskRuntimeRunState
import skillbill.engine.featuretask.runner.LaunchResult
import skillbill.error.shellcontent.InvalidFeatureTaskRuntimeHandoffProjectionError
import skillbill.error.shellcontent.InvalidFeatureTaskRuntimePlanningProjectionSchemaError
import skillbill.error.shellcontent.InvalidWorkflowStateSchemaError
import skillbill.ports.repository.toFileLocation
import skillbill.review.context.model.execution.SpecIntentProjectionResolveRequest
import skillbill.review.context.model.execution.SpecIntentResolution
import skillbill.review.context.model.hunk.ReviewContextBudgetPolicy
import skillbill.review.context.model.launch.CodeReviewExecutionMode
import skillbill.workflow.model.ValidationDepth
import skillbill.workflow.model.goalreview.FeatureTaskRuntimeReviewPassSequence
import skillbill.workflow.model.goalreview.ReviewPassResolution
import skillbill.workflow.taskruntime.artifact.asWorkflowArtifactEntry
import skillbill.workflow.taskruntime.handoff.FeatureTaskRuntimeHandoffContract
import skillbill.workflow.taskruntime.model.audit.QUARANTINE_REJECTION_CLASS_PLANNING_PROJECTION
import skillbill.workflow.taskruntime.model.core.FeatureTaskRuntimeRepositoryCheckpoint
import skillbill.workflow.taskruntime.model.core.FeatureTaskRuntimeResolvedBranch
import skillbill.workflow.taskruntime.model.handoff.task.FeatureTaskRuntimeHandoffAssemblyRequest
import skillbill.workflow.taskruntime.model.handoff.task.FeatureTaskRuntimePhaseHandoff
import skillbill.workflow.taskruntime.model.handoff.task.FeatureTaskRuntimeProducerIteration
import skillbill.workflow.taskruntime.model.handoff.task.FeatureTaskRuntimeProjectionFailureClassification
import skillbill.workflow.taskruntime.phase.task.FeatureTaskRuntimePhaseWorkflowDefinition
import java.nio.file.Path

object PhaseLaunchPreparation {
  internal fun prepareLaunchForCapture(
    context: FeatureTaskRuntimeRunLoopContext,
    run: PhaseRun,
    state: FeatureTaskRuntimeRunState,
    iteration: Int?,
    priorCorrection: PriorAttemptCorrection?,
    taskDirective: String,
  ): LaunchPreparation {
    with(context) {
      val measurementContext =
        when (
          val resolution = PhaseLaunchPreparation.resolveLaunchMeasurementContext(context, run)
        ) {
          is LaunchMeasurementContextReady -> resolution.value
          is LaunchPreparationRejected -> return resolution
          is PreparedLaunchReady -> error("Unexpected launch measurement result.")
        }
      return PhaseLaunchPreparation.prepareDeclaredLaunch(
        context,
        DeclaredLaunchArgs(
          run,
          state,
          iteration,
          priorCorrection,
          measurementContext,
          taskDirective,
        ),
      )
    }
  }

  internal fun resolveLaunchMeasurementContext(
    context: FeatureTaskRuntimeRunLoopContext,
    run: PhaseRun,
  ): LaunchPreparation {
    with(context) {
      val producerIteration =
        run.declaration.projectionDeclarations
          .map { declaration ->
            val phaseId = declaration.producerIteration.phaseId
            state.outputFor(phaseId)?.let { FeatureTaskRuntimeProducerIteration(phaseId, it.iteration) }
              ?: declaration.producerIteration
          }
          .maxByOrNull(FeatureTaskRuntimeProducerIteration::iteration)
          ?: FeatureTaskRuntimeProducerIteration(run.phaseId, 1)
      return try {
        LaunchMeasurementContextReady(
          LaunchRejectionMeasurementContext(
            producerIteration = producerIteration,
            repositoryCheckpoint =
              with(FeatureTaskRuntimeRunLoopOutputVerification) {
                resolveRepositoryCheckpoint(
                  RepositoryCheckpointResolutionArgs(
                    recorder = recorder,
                    goalContinuationRecorder = goalContinuationRecorder,
                    phaseGates = phaseGates,
                    session = session,
                    run = run,
                  ),
                )
              },
          ),
        )
      } catch (error: InvalidFeatureTaskRuntimeHandoffProjectionError) {
        recordLaunchSeamRejection(
          recorder,
          LaunchSeamRejectionArgs(
            run = run,
            state = state,
            classification = FeatureTaskRuntimeProjectionFailureClassification.BUDGET_OVERFLOW,
            sourceLabel = error.projectionName,
            fallbackProducerIteration = producerIteration,
            repositoryCheckpoint = null,
          ),
        )
        LaunchPreparationRejected(
          LaunchResult.projectionRejected(
            "Feature-task-runtime phase '${run.phaseId}' could not resolve its repository checkpoint: ${error.message}",
          ),
        )
      }
    }
  }

  internal fun prepareDeclaredLaunch(
    context: FeatureTaskRuntimeRunLoopContext,
    args: DeclaredLaunchArgs,
  ): LaunchPreparation = PhaseLaunchPreparation.prepareDeclaredLaunchBody(context, args)

  internal fun recordLaunchSeamRejection(
    recorder: FeatureTaskRuntimePhaseRecorder,
    args: LaunchSeamRejectionArgs,
  ) {
    val run = args.run
    val state = args.state
    val classification = args.classification
    val sourceLabel = args.sourceLabel
    val fallbackProducerIteration = args.fallbackProducerIteration
    val repositoryCheckpoint = args.repositoryCheckpoint
    val attribution =
      resolveLaunchRejectionAttribution(
        declarations = run.declaration.projectionDeclarations,
        projectionName = sourceLabel,
        currentProducerIteration = { phaseId -> state.outputFor(phaseId)?.iteration },
        fallbackProducerIteration = fallbackProducerIteration,
      )
    recorder.recordProjectionRejection(
      FeatureTaskRuntimeProjectionRejection(
        workflowId = run.request.workflowId,
        consumerPhaseId = run.phaseId,
        projectionContractId = attribution.projectionContractId,
        producerIteration = attribution.producerIteration,
        repositoryCheckpointFingerprint = repositoryCheckpoint?.fingerprint,
        failureClassification = classification,
        sourceLabel = sourceLabel,
      ),
    )
  }

  internal fun launchPreparationRejected(
    recorder: FeatureTaskRuntimePhaseRecorder,
    args: LaunchPreparationRejectedArgs,
  ): LaunchPreparationRejected {
    PhaseLaunchPreparation.recordLaunchSeamRejection(
      recorder,
      LaunchSeamRejectionArgs(
        run = args.run,
        state = args.state,
        classification = args.classification,
        sourceLabel = args.sourceLabel,
        fallbackProducerIteration = args.measurement.producerIteration,
        repositoryCheckpoint = args.measurement.repositoryCheckpoint,
      ),
    )
    return LaunchPreparationRejected(LaunchResult.projectionRejected(args.message))
  }

  internal fun prepareDeclaredLaunchBody(
    context: FeatureTaskRuntimeRunLoopContext,
    args: DeclaredLaunchArgs,
  ): LaunchPreparation {
    with(context) {
      val run = args.run
      val state = args.state
      val priorCorrection = args.priorCorrection
      val measurementContext = args.context
      return try {
        PreparedLaunchReady(
          PhaseLaunchPreparation.prepareLaunch(
            context,
            run = run,
            iteration = args.iteration,
            priorCorrection = priorCorrection,
            repositoryCheckpoint = measurementContext.repositoryCheckpoint,
            taskDirective = args.taskDirective,
          ),
        )
      } catch (error: InvalidFeatureTaskRuntimeHandoffProjectionError) {
        rejectedHandoffLaunch(recorder, run, state, error, measurementContext)
      } catch (error: InvalidFeatureTaskRuntimePlanningProjectionSchemaError) {
        rejectedPlanningProjectionLaunch(recorder, run, state, error, measurementContext)
      } catch (error: InvalidWorkflowStateSchemaError) {
        rejectedDurableBriefingLaunch(recorder, run, state, error, measurementContext)
      }
    }
  }

  private fun rejectedHandoffLaunch(
    recorder: FeatureTaskRuntimePhaseRecorder,
    run: PhaseRun,
    state: FeatureTaskRuntimeRunState,
    error: InvalidFeatureTaskRuntimeHandoffProjectionError,
    context: LaunchRejectionMeasurementContext,
  ): LaunchPreparationRejected =
    launchPreparationRejected(
      recorder,
      LaunchPreparationRejectedArgs(
        run = run,
        state = state,
        classification = error.failureKind.toMeasurementFailureClassification(),
        sourceLabel = error.projectionName,
        measurement = context,
        message =
          "Feature-task-runtime phase '${run.phaseId}' could not build its declared handoff " +
            "projection: ${error.message}",
      ),
    )

  private fun rejectedPlanningProjectionLaunch(
    recorder: FeatureTaskRuntimePhaseRecorder,
    run: PhaseRun,
    state: FeatureTaskRuntimeRunState,
    error: InvalidFeatureTaskRuntimePlanningProjectionSchemaError,
    context: LaunchRejectionMeasurementContext,
  ): LaunchPreparationRejected {
    PhaseLaunchPreparation.recordLaunchSeamRejection(
      recorder,
      LaunchSeamRejectionArgs(
        run = run,
        state = state,
        classification = FeatureTaskRuntimeProjectionFailureClassification.INVALID_CONTRACT,
        sourceLabel = error.projectionName ?: "planning_projection",
        fallbackProducerIteration = context.producerIteration,
        repositoryCheckpoint = context.repositoryCheckpoint,
      ),
    )
    return LaunchPreparationRejected(
      LaunchResult.recordRejected(
        QUARANTINE_REJECTION_CLASS_PLANNING_PROJECTION,
        error.message.orEmpty(),
      ),
    )
  }

  private fun rejectedDurableBriefingLaunch(
    recorder: FeatureTaskRuntimePhaseRecorder,
    run: PhaseRun,
    state: FeatureTaskRuntimeRunState,
    error: InvalidWorkflowStateSchemaError,
    context: LaunchRejectionMeasurementContext,
  ): LaunchPreparationRejected =
    launchPreparationRejected(
      recorder,
      LaunchPreparationRejectedArgs(
        run = run,
        state = state,
        classification = FeatureTaskRuntimeProjectionFailureClassification.UNSUPPORTED_VERSION,
        sourceLabel = "durable_briefing",
        measurement = context,
        message =
          "Feature-task-runtime phase '${run.phaseId}' rejected a durable handoff envelope at " +
            "the launch seam: ${error.message}",
      ),
    )

  internal fun prepareLaunch(
    context: FeatureTaskRuntimeRunLoopContext,
    run: PhaseRun,
    iteration: Int?,
    priorCorrection: PriorAttemptCorrection?,
    repositoryCheckpoint: FeatureTaskRuntimeRepositoryCheckpoint?,
    taskDirective: String,
  ): PreparedLaunch {
    with(context) {
      val resolvedBranchRecord = recorder.loadResolvedBranch(run.request.workflowId)
      val handoff =
        assembleLaunchHandoff(
          context,
          run,
          repositoryCheckpoint,
          resolvedBranchRecord,
        )
      recorder.validateHandoffDeclarations(handoff.projectionDeclarations)
      val sharedEvidence =
        FeatureTaskRuntimeRunLoopOutputVerification.resolveSharedReviewEvidence(
          phaseGates,
          run,
          repositoryCheckpoint,
        )
      val briefing =
        FeatureTaskRuntimePhaseBriefingAssembler.assemble(
          handoff,
          run.request.workflowId,
          phaseGates.planningProjectionValidator,
          run.request.agentAddonSelection,
          sharedEvidence?.reference,
        )
      if (!run.policy.singleAgentSession) {
        recorder.recordPhaseBriefing(
          run.request.workflowId,
          briefing,
          sharedEvidence?.measurement,
        )
      }
      val prompt =
        PhaseLaunchPreparation.composeLaunchPrompt(
          context,
          run,
          PhaseLaunchPreparation
            .composeLaunchPromptInputs(context, run, handoff, priorCorrection, briefing, taskDirective)
            .copy(
              phaseSettlement = iteration?.let { FeatureTaskRuntimePhaseSettlementTarget(run.request.workflowId, it) },
            ),
        )
      return PreparedLaunch(briefing, prompt)
    }
  }

  private fun assembleLaunchHandoff(
    context: FeatureTaskRuntimeRunLoopContext,
    run: PhaseRun,
    repositoryCheckpoint: FeatureTaskRuntimeRepositoryCheckpoint?,
    resolvedBranchRecord: FeatureTaskRuntimeResolvedBranch?,
  ) = with(context) {
    FeatureTaskRuntimeHandoffContract.assembleHandoff(
      FeatureTaskRuntimeHandoffAssemblyRequest(
        declaration = run.declaration,
        runInvariants = run.request.runInvariants,
        recordedOutputs = state.outputs(run.declaration.consumedUpstreamPhaseIds),
        drivingVerdict = run.reentry?.drivingVerdict,
        repairLedger = null,
        repositoryCheckpoint = repositoryCheckpoint,
        expectedRepositoryCheckpoint =
          expectedCheckpointForLaunch(run, repositoryCheckpoint)
            ?.let(::FeatureTaskRuntimeRepositoryCheckpoint),
        branchIdentity = resolvedBranchRecord?.branch,
        baseBranch = resolvedBranchRecord?.baseBranch ?: "main",
        validationDepth = run.request.goalContinuation?.validationDepth ?: ValidationDepth.DEFAULT,
        qualityGateSelection = qualityGateSelection(request),
      ),
    ).copy(
      recordedFindingVerdicts =
        FeatureTaskRuntimeRunLoopOutputVerification.recordedFindingVerdictsForFixHandoff(
          recorder,
          run,
          state,
        ),
    )
  }

  private fun composeLaunchPrompt(
    context: FeatureTaskRuntimeRunLoopContext,
    run: PhaseRun,
    inputs: FeatureTaskRuntimePhasePromptComposeInputs,
  ): String {
    with(context) {
      return FeatureTaskRuntimePhasePromptComposer.compose(inputs) +
        verifyFindingsSpecIntentSection(state, recorder, session, phaseGates, run)
    }
  }

  private fun verifyFindingsSpecIntentSection(
    state: FeatureTaskRuntimeRunState,
    recorder: FeatureTaskRuntimePhaseRecorder,
    session: FeatureTaskRuntimeRunLoopSession,
    phaseGates: FeatureTaskRuntimePhaseGates,
    run: PhaseRun,
  ): String {
    if (run.phaseId != FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_VERIFY_FINDINGS) return ""
    val checkpoint =
      recorder.loadFindingVerificationCheckpoint(
        run.request.workflowId,
      )
    val boundarySelection =
      recorder.loadFindingVerificationBoundarySelection(
        run.request.workflowId,
      )?.takeIf { it.isNotEmpty() }
    val resolution =
      phaseGates.specIntentProjectionResolver.resolve(
        SpecIntentProjectionResolveRequest(
          repoRoot = run.request.repoRoot.toFileLocation(),
          explicitSpecPath = Path.of(run.request.runInvariants.specReference).toFileLocation(),
          branchName = session.resolvedBranch ?: "HEAD",
          changedPaths = emptyList(),
          budget = ReviewContextBudgetPolicy.DEFAULT,
        ),
      )
    val boundarySections =
      FeatureTaskRuntimeRunLoopOutputVerification
        .findingVerificationBoundarySections(state, recorder, phaseGates, run)
    return buildString {
      when (resolution) {
        is SpecIntentResolution.Resolved -> {
          appendLine()
          appendLine("## Spec intent projection (verify_findings)")
          appendLine(JsonCodec.mapToJsonString(resolution.projection.toProjectionPayload()))
        }
        is SpecIntentResolution.None -> Unit
      }
      append(phaseGates.findingVerificationBoundaryMemory.promptSection(boundarySections))
      if (boundarySelection != null) {
        append(
          phaseGates.findingVerificationBoundaryMemory.resolvedBodiesPromptSection(
            repoRoot = run.request.repoRoot,
            sections = boundarySections,
            selectionsByFindingId = boundarySelection,
          ),
        )
      }
      if (!checkpoint.isNullOrEmpty()) {
        appendLine()
        appendLine("## Persisted verify_findings checkpoint")
        appendLine(
          "Reuse these in-flight dispositions verbatim unless repository evidence contradicts them; " +
            "do not mint a second verification pass.",
        )
        appendLine(
          checkpoint.joinToString(prefix = "[", postfix = "]") { disposition ->
            JsonCodec.mapToJsonString(workflowArtifactEntryMap(disposition.asWorkflowArtifactEntry()))
          },
        )
      }
    }
  }

  private fun composeLaunchPromptInputs(
    context: FeatureTaskRuntimeRunLoopContext,
    run: PhaseRun,
    handoff: FeatureTaskRuntimePhaseHandoff,
    priorCorrection: PriorAttemptCorrection?,
    briefing: FeatureTaskRuntimePhaseLaunchBriefing,
    taskDirective: String,
  ): FeatureTaskRuntimePhasePromptComposeInputs {
    with(context) {
      val context = this
      val resolvedBranchRecord = recorder.loadResolvedBranch(run.request.workflowId)
      val (passNumber, depthResolution, executedTier) =
        PhaseLaunchPreparation.resolveReviewPromptTier(context, run, state)
      return FeatureTaskRuntimePhasePromptComposeInputs(
        issueKey = run.request.issueKey,
        briefing = briefing,
        suppressDecomposition = isGoalContinuationRun(run.request),
        codeReviewMode = executedTier,
        reviewPassNumber = passNumber,
        goalSubtaskReviewInput = run.goalReviewInput,
        baselineUntrackedPaths = resolvedBranchRecord?.baselineUntrackedPaths.orEmpty(),
        resolvedReviewTier = depthResolution?.let { executedTier },
        reviewDecidingRule = depthResolution?.decidingRule,
        repairLedger = handoff.repairLedger,
        priorReviewContext = null,
        priorSchemaFailure = priorCorrection?.schemaGateReason,
        priorTerminalFailure = priorCorrection?.retryableTerminalReason,
        priorFindingCoverage = priorCorrection?.findingCoverageReason,
        correctiveRepairContext = priorCorrection?.correctiveRepairContext,
        operatorBlockRetry =
          session.operatorBlockRetry
            ?.takeIf { it.phaseId == run.phaseId && !session.operatorBlockRetryCompleted },
        implementationContinuation =
          FeatureTaskRuntimeRunLoopOutputVerification.implementationContinuationFor(recorder, run),
        validationGateFindings = run.validationGateFindings,
        validationGateTriagePlan = run.validationGateTriagePlan,
        validationGateRepair = run.validationGateRepair,
        validationGateTriage = run.validationGateTriage,
        agentRunValidateFallback = run.agentRunValidateFallback,
        packBuildCommand =
          FeatureTaskRuntimeRunLoopValidationScope.packBuildCommand(
            phaseGates,
            recorder,
            goalContinuationRecorder,
            session,
            run,
          ),
        auditRetryFocusHint =
          session.auditRetryFocusHint?.takeIf {
            run.phaseId == FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_AUDIT
          },
        mutating = run.policy.mutating,
        singleAgentSession = run.policy.singleAgentSession,
        taskDirective = taskDirective,
      )
    }
  }

  private fun resolveReviewPromptTier(
    context: FeatureTaskRuntimeRunLoopContext,
    run: PhaseRun,
    state: FeatureTaskRuntimeRunState,
  ): Triple<Int?, ReviewPassResolution?, CodeReviewExecutionMode> {
    with(context) {
      val passNumber =
        FeatureTaskRuntimeRunLoopPhaseBlocking.reviewPassNumber(request, goalContinuationRecorder, run, state)
      val resolution =
        passNumber?.let { pass ->
          FeatureTaskRuntimeReviewPassSequence.resolveForPass(run.request.runInvariants.codeReviewMode, pass)
        }
      val executedTier =
        RuntimeOwnedReviewMode.execute(
          resolution?.resolvedTier ?: run.request.runInvariants.codeReviewMode,
        )
      resolution?.let {
        FeatureTaskRuntimeRunLoopPhaseBlocking.persistResolvedReviewTier(
          request,
          goalContinuationRecorder,
          run,
          it,
        )
      }
      return Triple(passNumber, resolution, executedTier)
    }
  }
}

private fun expectedCheckpointForLaunch(
  run: PhaseRun,
  repositoryCheckpoint: FeatureTaskRuntimeRepositoryCheckpoint?,
): String? =
  if (
    run.phaseId == FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_REVIEW &&
    run.reentry?.loopId == FeatureTaskRuntimePhaseWorkflowDefinition.REVIEW_FIX_LOOP_ID
  ) {
    repositoryCheckpoint?.fingerprint
  } else {
    run.reentry?.expectedRepositoryCheckpoint ?: repositoryCheckpoint?.fingerprint
  }
