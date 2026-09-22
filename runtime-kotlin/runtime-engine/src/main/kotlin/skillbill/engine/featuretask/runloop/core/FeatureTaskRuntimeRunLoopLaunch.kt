package skillbill.engine.featuretask.runloop.core

import skillbill.application.review.spec.toProjectionPayload
import skillbill.contracts.JsonCodec
import skillbill.engine.featuretask.model.core.FeatureTaskRuntimeRunRequest
import skillbill.engine.featuretask.model.phase.FeatureTaskRuntimePhaseLaunchBriefing
import skillbill.engine.featuretask.model.phase.FeatureTaskRuntimeProjectionRejection
import skillbill.engine.featuretask.persist.workflowArtifactEntryMap
import skillbill.engine.featuretask.phase.core.FeatureTaskRuntimePhaseFileManifest
import skillbill.engine.featuretask.phase.core.FeatureTaskRuntimePhaseGates
import skillbill.engine.featuretask.phase.core.FeatureTaskRuntimePhaseSafetyPolicy
import skillbill.engine.featuretask.phase.core.toMeasurementFailureClassification
import skillbill.engine.featuretask.phase.prompt.directives.PriorAttemptCorrection
import skillbill.engine.featuretask.phase.record.FeatureTaskRuntimePhaseRecorder
import skillbill.engine.featuretask.review.finding.promptSection
import skillbill.engine.featuretask.review.finding.resolvedBodiesPromptSection
import skillbill.engine.featuretask.runloop.output.FeatureTaskRuntimeRunLoopOutputPersistence
import skillbill.engine.featuretask.runloop.output.FeatureTaskRuntimeRunLoopOutputVerification
import skillbill.engine.featuretask.runloop.state.FeatureTaskRuntimeRunState
import skillbill.engine.featuretask.runloop.state.featureTaskRuntimeChildOutput
import skillbill.engine.featuretask.runner.LaunchResult
import skillbill.engine.featuretask.runner.infraFailureReason
import skillbill.engine.featuretask.runner.providerLimitPauseReason
import skillbill.engine.featuretask.runner.providerLimitSignal
import skillbill.error.shellcontent.InvalidFeatureTaskRuntimeHandoffProjectionError
import skillbill.error.shellcontent.InvalidFeatureTaskRuntimePhaseBriefingFramingError
import skillbill.error.shellcontent.InvalidFeatureTaskRuntimePlanningProjectionSchemaError
import skillbill.error.shellcontent.InvalidWorkflowStateSchemaError
import skillbill.goalrunner.subtaskreview.GoalSubtaskReviewSummaryReducer
import skillbill.goalrunner.subtaskreview.model.StructuredGoalReviewFinding
import skillbill.goalrunner.subtaskreview.verificationBoundaryFindingPaths
import skillbill.ports.agentrun.model.AgentRunLaunchFacts
import skillbill.ports.agentrun.model.AgentRunLaunchOutcome
import skillbill.ports.agentrun.model.SkillRunRequest
import skillbill.ports.agentrun.model.UnsupportedAgentRunLaunch
import skillbill.ports.goalrunner.runner.model.GoalRunnerSubtaskLaunchRequest
import skillbill.ports.repository.toFileLocation
import skillbill.ports.workflow.gitops.model.WorkflowGitOperationResult
import skillbill.ports.workflow.gitops.pathContentIdentities
import skillbill.ports.workflow.gitops.repositoryCheckpointFingerprint
import skillbill.ports.workflow.gitops.repositoryOwnedPaths
import skillbill.ports.workflow.gitops.runtimePhaseChangedPathsBetweenCommits
import skillbill.ports.workflow.gitops.runtimePhaseHeadCommit
import skillbill.review.context.model.execution.SpecIntentProjectionResolveRequest
import skillbill.review.context.model.execution.SpecIntentResolution
import skillbill.review.context.model.hunk.ReviewContextBudgetPolicy
import skillbill.telemetry.estimation.estimateTokens
import skillbill.workflow.taskruntime.artifact.asWorkflowArtifactEntry
import skillbill.workflow.taskruntime.artifact.envelopeWireMap
import skillbill.workflow.taskruntime.model.audit.QUARANTINE_REJECTION_CLASS_PLANNING_PROJECTION
import skillbill.workflow.taskruntime.model.handoff.task.FeatureTaskRuntimePhaseOutput
import skillbill.workflow.taskruntime.model.handoff.task.FeatureTaskRuntimeProducerIteration
import skillbill.workflow.taskruntime.model.handoff.task.FeatureTaskRuntimeProjectionFailureClassification
import skillbill.workflow.taskruntime.model.handoff.task.NormalizedFeatureTaskRuntimePhaseOutput
import skillbill.workflow.taskruntime.model.phase.FeatureTaskRuntimeFailureDisposition
import skillbill.workflow.taskruntime.model.repair.task.FeatureTaskRuntimeCorrectiveRepairContext
import skillbill.workflow.taskruntime.phase.task.FeatureTaskRuntimePhaseWorkflowDefinition
import java.nio.file.Path
import kotlin.time.Duration.Companion.minutes

object FeatureTaskRuntimeRunLoopLaunch {
  internal fun findingPathsForBoundaryMemory(finding: StructuredGoalReviewFinding): List<String> =
    GoalSubtaskReviewSummaryReducer.verificationBoundaryFindingPaths(finding)

  internal fun verifyFindingsSpecIntentSection(
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

  internal fun FeatureTaskRuntimeRunLoopContext.launchAndCapture(
    run: PhaseRun,
    state: FeatureTaskRuntimeRunState,
    priorCorrection: PriorAttemptCorrection? = null,
  ): LaunchResult {
    val before =
      when (val captured = FeatureTaskRuntimeRunLoopLaunch.captureLaunchBeforeState(phaseGates, run)) {
        is LaunchCaptureBeforeResult.Ready -> captured.state
        is LaunchCaptureBeforeResult.Failed ->
          return FeatureTaskRuntimeRunLoopLaunch.launchCaptureInfraFailure(
            run.phaseId,
            captured.detail,
            childNeverLaunched = true,
          )
      }
    val prepared =
      when (
        val preparation = FeatureTaskRuntimeRunLoopLaunch.prepareLaunchForCapture(this, run, state, priorCorrection)
      ) {
        is PreparedLaunchReady -> preparation.value
        is LaunchPreparationRejected -> return preparation.result
        is LaunchMeasurementContextReady -> error("Unexpected launch preparation result.")
      }
    val (
      isReviewPhase,
      isVerifyFindingsPhase,
    ) = FeatureTaskRuntimeRunLoopLaunch.isReadOnlyLaunchPhase(run.phaseId)
    val outcome = executeSubtaskLaunch(run, prepared, isReviewPhase, isVerifyFindingsPhase)
    FeatureTaskRuntimeRunLoopLaunch.recordLaunchTokenUsage(
      run,
      prepared.briefing,
      outcome,
      state,
    )
    val fileManifest =
      when (
        val captured = FeatureTaskRuntimeRunLoopLaunch.buildLaunchFileManifest(phaseGates, run, before)
      ) {
        is LaunchCaptureAfterResult.Ready -> captured.manifest
        is LaunchCaptureAfterResult.Failed ->
          return FeatureTaskRuntimeRunLoopLaunch.launchCaptureInfraFailure(
            run.phaseId,
            captured.detail,
            childNeverLaunched = false,
          )
      }
    capturePhaseContentIdentities(request, session, phaseGates, run.phaseId)
    return FeatureTaskRuntimeRunLoopLaunch.reconcileLaunch(run.phaseId, outcome, fileManifest)
  }

  internal fun capturePhaseContentIdentities(
    request: FeatureTaskRuntimeRunRequest,
    session: FeatureTaskRuntimeRunLoopSession,
    phaseGates: FeatureTaskRuntimePhaseGates,
    phaseId: String,
  ) {
    val owned = phaseGates.gitOperations.repositoryOwnedPaths(request.repoRoot)
    if (owned !is WorkflowGitOperationResult.Ok) return
    val paths = owned.value.orEmpty().split(OWNED_PATH_DELIMITER).map(String::trim).filter(String::isNotBlank)
    val identities = phaseGates.gitOperations.pathContentIdentities(request.repoRoot, paths)
    if (identities !is WorkflowGitOperationResult.Ok) return
    session.recordPhaseContentIdentities(
      phaseId,
      parseContentIdentities(identities.value.orEmpty()),
    )
  }

  internal fun parseContentIdentities(raw: String): Map<String, String> =
    raw
      .split(OWNED_PATH_DELIMITER)
      .filter(String::isNotBlank)
      .mapNotNull { record ->
        val identity = record.substringBefore('\t', missingDelimiterValue = "")
        val path = record.substringAfter('\t', missingDelimiterValue = "")
        if (identity.isBlank() || path.isBlank()) null else path to identity
      }
      .toMap()

  internal fun prepareLaunchForCapture(
    context: FeatureTaskRuntimeRunLoopContext,
    run: PhaseRun,
    state: FeatureTaskRuntimeRunState,
    priorCorrection: PriorAttemptCorrection?,
  ): LaunchPreparation {
    with(context) {
      val measurementContext =
        when (
          val resolution = FeatureTaskRuntimeRunLoopLaunch.resolveLaunchMeasurementContext(context, run)
        ) {
          is LaunchMeasurementContextReady -> resolution.value
          is LaunchPreparationRejected -> return resolution
          is PreparedLaunchReady -> error("Unexpected launch measurement result.")
        }
      return FeatureTaskRuntimeRunLoopLaunch.prepareDeclaredLaunch(
        context,
        DeclaredLaunchArgs(
          run,
          state,
          priorCorrection,
          measurementContext,
        ),
      )
    }
  }

  internal data class LaunchCaptureBeforeState(
    val beforeManifest: String,
    val beforeCommit: String,
  )

  internal sealed interface LaunchCaptureBeforeResult {
    data class Ready(val state: LaunchCaptureBeforeState) : LaunchCaptureBeforeResult

    data class Failed(val detail: String) : LaunchCaptureBeforeResult
  }

  internal sealed interface LaunchCaptureAfterResult {
    data class Ready(val manifest: FeatureTaskRuntimePhaseFileManifest) : LaunchCaptureAfterResult

    data class Failed(val detail: String) : LaunchCaptureAfterResult
  }

  internal fun captureLaunchBeforeState(
    phaseGates: FeatureTaskRuntimePhaseGates,
    run: PhaseRun,
  ): FeatureTaskRuntimeRunLoopLaunch.LaunchCaptureBeforeResult {
    val before = phaseGates.gitOperations.worktreeStatus(run.request.repoRoot)
    if (before !is WorkflowGitOperationResult.Ok) {
      return FeatureTaskRuntimeRunLoopLaunch.LaunchCaptureBeforeResult.Failed("before-file manifest: ${before.error}")
    }
    val beforeCommit = phaseGates.gitOperations.runtimePhaseHeadCommit(run.request.repoRoot)
    if (beforeCommit !is WorkflowGitOperationResult.Ok) {
      return FeatureTaskRuntimeRunLoopLaunch.LaunchCaptureBeforeResult.Failed("before commit")
    }
    return FeatureTaskRuntimeRunLoopLaunch.LaunchCaptureBeforeResult.Ready(
      FeatureTaskRuntimeRunLoopLaunch.LaunchCaptureBeforeState(
        beforeManifest = before.value.orEmpty(),
        beforeCommit = beforeCommit.value.orEmpty(),
      ),
    )
  }

  internal fun launchCaptureInfraFailure(
    phaseId: String,
    detail: String,
    childNeverLaunched: Boolean,
  ): LaunchResult =
    LaunchResult.infraFailure(
      "Feature-task-runtime phase '$phaseId' could not capture its $detail",
      childNeverLaunched = childNeverLaunched,
    )

  internal fun FeatureTaskRuntimeRunLoopContext.executeSubtaskLaunch(
    run: PhaseRun,
    prepared: PreparedLaunch,
    isReviewPhase: Boolean,
    isVerifyFindingsPhase: Boolean,
  ): AgentRunLaunchOutcome {
    val launched = FeatureTaskRuntimeRunLoopOutputPersistence.launchedModelDirective(run)
    return subtaskLauncher.launch(
      GoalRunnerSubtaskLaunchRequest(
        invokedAgentId = run.resolvedAgent.invokedAgentId,
        configuredAgentOverrideId = run.resolvedAgent.configuredAgentOverrideId,
        skillRunRequest =
          SkillRunRequest(
            issueKey = run.request.issueKey,
            repoRoot = run.request.repoRoot,
            timeout = run.request.timeout,
            modelOverride = launched.modelOverride,
            effortOverride = launched.effortOverride,
            compaction = run.compaction,
            promptOverride = prepared.prompt,
            readOnlyPhase = isReviewPhase || isVerifyFindingsPhase,
            progressIdleTimeout =
              READ_ONLY_PHASE_PROGRESS_IDLE_TIMEOUT_MINUTES.minutes
                .takeIf { isReviewPhase || isVerifyFindingsPhase },
            activityStampSink =
              activityStampWriter.sink(
                workflowId = run.request.workflowId,
                parentWorkflowId = run.request.goalContinuation?.parentWorkflowId,
              ),
            worktreeEditObserver =
              worktreeEditJournalWriter.observer(
                repoRoot = run.request.repoRoot,
                resolveWorkflowId = { run.request.workflowId },
                resolvePhaseId = { run.phaseId },
              ),
          ),
      ),
    )
  }

  internal fun buildLaunchFileManifest(
    phaseGates: FeatureTaskRuntimePhaseGates,
    run: PhaseRun,
    before: FeatureTaskRuntimeRunLoopLaunch.LaunchCaptureBeforeState,
  ): FeatureTaskRuntimeRunLoopLaunch.LaunchCaptureAfterResult {
    val after = phaseGates.gitOperations.worktreeStatus(run.request.repoRoot)
    if (after !is WorkflowGitOperationResult.Ok) {
      return FeatureTaskRuntimeRunLoopLaunch.LaunchCaptureAfterResult.Failed("after-file manifest")
    }
    val afterCommit = phaseGates.gitOperations.runtimePhaseHeadCommit(run.request.repoRoot)
    if (afterCommit !is WorkflowGitOperationResult.Ok) {
      return FeatureTaskRuntimeRunLoopLaunch.LaunchCaptureAfterResult.Failed("after commit")
    }
    val committedPaths =
      phaseGates.gitOperations.runtimePhaseChangedPathsBetweenCommits(
        run.request.repoRoot,
        before.beforeCommit,
        afterCommit.value.orEmpty(),
      )
    if (committedPaths !is WorkflowGitOperationResult.Ok) {
      return FeatureTaskRuntimeRunLoopLaunch.LaunchCaptureAfterResult.Failed("committed file changes")
    }
    return FeatureTaskRuntimeRunLoopLaunch.LaunchCaptureAfterResult.Ready(
      FeatureTaskRuntimePhaseFileManifest(
        before = FeatureTaskRuntimePhaseSafetyPolicy.changedPaths(before.beforeManifest),
        after =
          (
            FeatureTaskRuntimePhaseSafetyPolicy.changedPaths(after.value) +
              FeatureTaskRuntimePhaseSafetyPolicy.lineSeparatedPaths(committedPaths.value.orEmpty())
          ).distinct().sorted(),
      ),
    )
  }

  internal fun recordLaunchTokenUsage(
    run: PhaseRun,
    briefing: FeatureTaskRuntimePhaseLaunchBriefing,
    outcome: AgentRunLaunchOutcome,
    state: FeatureTaskRuntimeRunState,
  ) {
    if (outcome is AgentRunLaunchFacts) {
      val inputTokens = estimateTokens(briefing.briefingText)
      val outputTokens = estimateTokens(outcome.stdout)
      state.recordPhaseTokenUsage(run.phaseId, inputTokens, outputTokens)
    }
  }

  internal fun isReadOnlyLaunchPhase(phaseId: String): Pair<Boolean, Boolean> {
    val isReviewPhase = phaseId == FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_REVIEW
    val isVerifyFindingsPhase = phaseId == FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_VERIFY_FINDINGS
    return isReviewPhase to isVerifyFindingsPhase
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
  ): LaunchPreparation = FeatureTaskRuntimeRunLoopLaunch.prepareDeclaredLaunchBody(context, args)

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

  internal fun outputEnvelopeOf(output: FeatureTaskRuntimePhaseOutput): Map<String, Any?>? =
    output.normalizedOutput?.envelopeWireMap()?.takeIf { it.isNotEmpty() }
      ?: JsonCodec.parseObjectOrNull(output.payload)?.let(JsonCodec::jsonElementToValue)
        ?.let(JsonCodec::anyToStringAnyMap)

  internal fun reconcileLaunch(
    phaseId: String,
    outcome: AgentRunLaunchOutcome,
    fileManifest: FeatureTaskRuntimePhaseFileManifest,
  ): LaunchResult =
    when (outcome) {
      is UnsupportedAgentRunLaunch ->
        LaunchResult.infraFailure(
          "Feature-task-runtime phase '$phaseId' could not launch an agent: ${outcome.reason}",
          fileManifest,
          childNeverLaunched = true,
        )
      is AgentRunLaunchFacts ->
        providerLimitSignal(outcome)
          ?.let { LaunchResult.providerLimited(providerLimitPauseReason(phaseId, it), fileManifest) }
          ?: infraFailureReason(phaseId, outcome)
            ?.let {
              LaunchResult.infraFailure(
                it,
                fileManifest,
                childNeverLaunched = outcome.spawnFailed || !outcome.processStarted,
                childOutput = featureTaskRuntimeChildOutput(outcome),
              )
            }
          ?: LaunchResult.captured(
            CapturedPhaseOutput(
              text = outcome.stdout,
              bytes = outcome.stdoutBytes,
              truncated = outcome.stdoutTruncated,
              byteSize = outcome.stdoutByteSize,
              sha256 = outcome.stdoutSha256,
            ),
            fileManifest = fileManifest,
          )
    }

  internal fun launchPreparationRejected(
    recorder: FeatureTaskRuntimePhaseRecorder,
    args: LaunchPreparationRejectedArgs,
  ): LaunchPreparationRejected {
    FeatureTaskRuntimeRunLoopLaunch.recordLaunchSeamRejection(
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
          FeatureTaskRuntimeRunLoopOutputPersistence.prepareLaunch(
            context,
            run = run,
            priorCorrection = priorCorrection,
            repositoryCheckpoint = measurementContext.repositoryCheckpoint,
          ),
        )
      } catch (error: InvalidFeatureTaskRuntimeHandoffProjectionError) {
        rejectedHandoffLaunch(recorder, run, state, error, measurementContext)
      } catch (error: InvalidFeatureTaskRuntimePhaseBriefingFramingError) {
        rejectedBriefingLaunch(recorder, run, state, error, measurementContext)
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

  private fun rejectedBriefingLaunch(
    recorder: FeatureTaskRuntimePhaseRecorder,
    run: PhaseRun,
    state: FeatureTaskRuntimeRunState,
    error: InvalidFeatureTaskRuntimePhaseBriefingFramingError,
    context: LaunchRejectionMeasurementContext,
  ): LaunchPreparationRejected =
    launchPreparationRejected(
      recorder,
      LaunchPreparationRejectedArgs(
        run = run,
        state = state,
        classification = FeatureTaskRuntimeProjectionFailureClassification.BUDGET_OVERFLOW,
        sourceLabel = "phase_briefing",
        measurement = context,
        message =
          "Feature-task-runtime phase '${run.phaseId}' could not fit its launch briefing under " +
            "the byte ceiling: ${error.message}",
      ),
    )

  private fun rejectedPlanningProjectionLaunch(
    recorder: FeatureTaskRuntimePhaseRecorder,
    run: PhaseRun,
    state: FeatureTaskRuntimeRunState,
    error: InvalidFeatureTaskRuntimePlanningProjectionSchemaError,
    context: LaunchRejectionMeasurementContext,
  ): LaunchPreparationRejected {
    FeatureTaskRuntimeRunLoopLaunch.recordLaunchSeamRejection(
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
}

internal sealed interface AttemptResult {
  data class Settled(val outcome: PhaseOutcome) : AttemptResult

  data class SchemaInvalid(
    val operatorReason: String,
    val retryReason: String,
    override val fileManifest: FeatureTaskRuntimePhaseFileManifest,
    override val rejectedOutput: String?,
    override val malformedOutput: Boolean,
    override val correctiveRepairContext: FeatureTaskRuntimeCorrectiveRepairContext?,
  ) : AttemptResult

  data class IncompleteWork(
    val operatorReason: String,
    val continuationReason: String,
    override val fileManifest: FeatureTaskRuntimePhaseFileManifest,
    val normalizedOutput: NormalizedFeatureTaskRuntimePhaseOutput,
  ) : AttemptResult

  data class RetryableTerminal(
    val operatorReason: String,
    val retryReason: String,
    override val fileManifest: FeatureTaskRuntimePhaseFileManifest,
    val failureDisposition: FeatureTaskRuntimeFailureDisposition,
  ) : AttemptResult

  data class BoundaryBodyDelivery(
    val continuationReason: String,
    override val fileManifest: FeatureTaskRuntimePhaseFileManifest,
  ) : AttemptResult

  data class FindingsOwed(
    val kind: FindingsOwedKind,
    val operatorReason: String,
    val retryReason: String,
    val refs: Set<String>,
    val detail: String?,
    override val fileManifest: FeatureTaskRuntimePhaseFileManifest,
  ) : AttemptResult

  data class AuditRetry(
    val focusHint: String,
    override val fileManifest: FeatureTaskRuntimePhaseFileManifest,
  ) : AttemptResult

  data class ValidationRemaining(
    val remainingFingerprint: String,
    val remainingDetail: String,
    override val fileManifest: FeatureTaskRuntimePhaseFileManifest,
  ) : AttemptResult

  val settledOutcome: PhaseOutcome? get() = (this as? Settled)?.outcome
  val schemaInvalidOperatorReason: String? get() = (this as? SchemaInvalid)?.operatorReason
  val schemaInvalidRetryReason: String? get() = (this as? SchemaInvalid)?.retryReason
  val fileManifest: FeatureTaskRuntimePhaseFileManifest?
    get() =
      when (this) {
        is Settled -> null
        is SchemaInvalid -> fileManifest
        is IncompleteWork -> fileManifest
        is RetryableTerminal -> fileManifest
        is FindingsOwed -> fileManifest
        is BoundaryBodyDelivery -> fileManifest
        is AuditRetry -> fileManifest
        is ValidationRemaining -> fileManifest
      }
  val rejectedOutput: String? get() = (this as? SchemaInvalid)?.rejectedOutput
  val malformedOutput: Boolean get() = (this as? SchemaInvalid)?.malformedOutput == true
  val correctiveRepairContext: FeatureTaskRuntimeCorrectiveRepairContext?
    get() = (this as? SchemaInvalid)?.correctiveRepairContext

  val retryableOperatorReason: String?
    get() =
      when (this) {
        is Settled -> null
        is SchemaInvalid -> operatorReason
        is IncompleteWork -> operatorReason
        is RetryableTerminal -> operatorReason
        is FindingsOwed -> operatorReason
        is BoundaryBodyDelivery -> null
        is AuditRetry -> null
        is ValidationRemaining -> remainingDetail
      }

  val semanticRetryReason: String?
    get() =
      when (this) {
        is Settled -> null
        is SchemaInvalid -> retryReason
        is IncompleteWork -> null
        is RetryableTerminal -> null
        is FindingsOwed -> null
        is BoundaryBodyDelivery -> null
        is AuditRetry -> null
        is ValidationRemaining -> remainingDetail
      }

  val retryableTerminalRetryReason: String? get() = (this as? RetryableTerminal)?.retryReason

  val retryableTerminalDisposition: FeatureTaskRuntimeFailureDisposition?
    get() = (this as? RetryableTerminal)?.failureDisposition

  val findingsOwedKind: FindingsOwedKind? get() = (this as? FindingsOwed)?.kind

  val findingsOwedRefs: Set<String>? get() = (this as? FindingsOwed)?.refs

  val findingsOwedRetryReason: String? get() = (this as? FindingsOwed)?.retryReason

  val findingsOwedDetail: String? get() = (this as? FindingsOwed)?.detail

  val incompleteWorkContinuationReason: String? get() = (this as? IncompleteWork)?.continuationReason
  val incompleteWorkOutput: NormalizedFeatureTaskRuntimePhaseOutput?
    get() = (this as? IncompleteWork)?.normalizedOutput
  val boundaryBodyDeliveryContinuationReason: String?
    get() = (this as? BoundaryBodyDelivery)?.continuationReason

  val auditRetryFocusHint: String? get() = (this as? AuditRetry)?.focusHint

  val auditRetryContinuation: Boolean get() = this is AuditRetry

  val validationRemainingFingerprint: String? get() = (this as? ValidationRemaining)?.remainingFingerprint

  val validationRemainingDetail: String? get() = (this as? ValidationRemaining)?.remainingDetail

  companion object {
    fun settled(outcome: PhaseOutcome): AttemptResult = Settled(outcome)

    fun boundaryBodyDelivery(
      continuationReason: String,
      fileManifest: FeatureTaskRuntimePhaseFileManifest,
    ): AttemptResult = BoundaryBodyDelivery(continuationReason, fileManifest)

    fun incompleteWork(
      operatorReason: String,
      continuationReason: String,
      fileManifest: FeatureTaskRuntimePhaseFileManifest,
      normalizedOutput: NormalizedFeatureTaskRuntimePhaseOutput,
    ): AttemptResult = IncompleteWork(operatorReason, continuationReason, fileManifest, normalizedOutput)

    fun auditRetry(
      focusHint: String,
      fileManifest: FeatureTaskRuntimePhaseFileManifest,
    ): AttemptResult = AuditRetry(focusHint, fileManifest)

    fun validationRemaining(
      remainingFingerprint: String,
      remainingDetail: String,
      fileManifest: FeatureTaskRuntimePhaseFileManifest,
    ): AttemptResult = ValidationRemaining(remainingFingerprint, remainingDetail, fileManifest)

    fun unaccountedItems(
      phaseId: String,
      itemNoun: String,
      unaccountedRefs: List<String>,
      retryReason: String,
      fileManifest: FeatureTaskRuntimePhaseFileManifest,
    ): AttemptResult =
      FindingsOwed(
        kind = FindingsOwedKind.OMITTED,
        operatorReason =
          "Phase '$phaseId' left carried $itemNoun unaccounted for in its output: " +
            unaccountedRefs.joinToString(", ") + ".",
        retryReason = retryReason,
        refs = unaccountedRefs.toSet(),
        detail = null,
        fileManifest = fileManifest,
      )

    fun unresolvedFindings(
      unresolvedRefs: Set<String>,
      detail: String,
      retryReason: String,
      fileManifest: FeatureTaskRuntimePhaseFileManifest,
    ): AttemptResult =
      FindingsOwed(
        kind = FindingsOwedKind.UNRESOLVED,
        operatorReason =
          "Phase 'implement_fix' reported carried review findings still open after " +
            "its attempt: ${unresolvedRefs.joinToString(", ")}.",
        retryReason = retryReason,
        refs = unresolvedRefs,
        detail = detail,
        fileManifest = fileManifest,
      )

    fun retryableTerminal(
      operatorReason: String,
      fileManifest: FeatureTaskRuntimePhaseFileManifest,
      failureDisposition: FeatureTaskRuntimeFailureDisposition,
    ): AttemptResult = RetryableTerminal(operatorReason, operatorReason, fileManifest, failureDisposition)

    fun schemaInvalid(
      operatorReason: String,
      fileManifest: FeatureTaskRuntimePhaseFileManifest,
      malformedOutput: Boolean = false,
      retryReason: String = operatorReason,
      correctiveRepairContext: FeatureTaskRuntimeCorrectiveRepairContext? = null,
    ): AttemptResult =
      SchemaInvalid(
        operatorReason = operatorReason,
        retryReason = retryReason,
        fileManifest = fileManifest,
        rejectedOutput = null,
        malformedOutput = malformedOutput,
        correctiveRepairContext = correctiveRepairContext,
      )
  }
}

internal sealed interface PhaseOutcome {
  data class Completed(val output: FeatureTaskRuntimePhaseOutput) : PhaseOutcome

  data class Blocked(val reason: String) : PhaseOutcome

  data class Paused(val reason: String) : PhaseOutcome

  data class RegenerateProducer(val producerPhaseId: String) : PhaseOutcome

  val completedOutput: FeatureTaskRuntimePhaseOutput? get() = (this as? Completed)?.output

  val blockedReason: String? get() = (this as? Blocked)?.reason

  val pausedReason: String? get() = (this as? Paused)?.reason

  val regenerationTargetPhaseId: String? get() = (this as? RegenerateProducer)?.producerPhaseId

  companion object {
    fun completed(output: FeatureTaskRuntimePhaseOutput): PhaseOutcome = Completed(output)

    fun blocked(reason: String): PhaseOutcome = Blocked(reason)

    fun paused(reason: String): PhaseOutcome = Paused(reason)

    fun regenerateProducer(producerPhaseId: String): PhaseOutcome = RegenerateProducer(producerPhaseId)
  }
}

internal sealed interface GoalReviewRunPreparation {
  data object CarryForward : GoalReviewRunPreparation

  class Blocked(
    val reason: String,
    val failureDisposition: FeatureTaskRuntimeFailureDisposition,
  ) : GoalReviewRunPreparation
}

internal data class GoalReviewRunReady(val run: PhaseRun) : GoalReviewRunPreparation

const val READ_ONLY_PHASE_PROGRESS_IDLE_TIMEOUT_MINUTES = 30L

const val LEGACY_PLANNING_PROJECTION_LAUNCH_SEAM_REJECTION =
  "rejected an upstream bounded planning projection at the launch seam"

const val OWNED_PATH_DELIMITER = '\u0000'
