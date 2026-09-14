package skillbill.engine.featuretask
import skillbill.application.idestatus.AgentActivityStampWriter
import skillbill.engine.featuretask.model.FeatureTaskRuntimeRunReport
import skillbill.engine.featuretask.model.FeatureTaskRuntimeRunRequest
import skillbill.engine.recovery.recommendedDurableChildRecoveryCommand
import skillbill.ports.diagnostics.RuntimeDiagnostics
import skillbill.ports.goalrunner.runner.GoalRunnerSubtaskLauncher
import skillbill.workflow.decomposition.model.SpecSource
import skillbill.workflow.model.WorkflowStepStatus
import skillbill.workflow.model.workflowStepStatus
import skillbill.workflow.taskruntime.FeatureTaskRuntimePhaseOutputValidator
import skillbill.workflow.taskruntime.FeatureTaskRuntimePhaseWorkflowDefinition
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeOperatorBlockRetry
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeProducerIteration
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeTransitionDeclaration
import skillbill.workflow.taskruntime.model.PhaseHandoffProjectionDeclaration
import java.nio.file.Path
import java.time.Clock

internal data class FeatureTaskRuntimeRunLoopContext(
  val request: FeatureTaskRuntimeRunRequest,
  val state: FeatureTaskRuntimeRunState,
  val observability: FeatureTaskRuntimeRunObservability,
  val specSource: SpecSource,
  val transitions: FeatureTaskRuntimeTransitionDeclaration,
  val phaseTokenAccumulator: MutableMap<String, Pair<Int, Int>>,
)

internal data class LaunchRejectionAttribution(
  val projectionContractId: String,
  val producerIteration: FeatureTaskRuntimeProducerIteration,
)

internal fun resolveLaunchRejectionAttribution(
  declarations: List<PhaseHandoffProjectionDeclaration>,
  projectionName: String,
  currentProducerIteration: (String) -> Int?,
  fallbackProducerIteration: FeatureTaskRuntimeProducerIteration,
): LaunchRejectionAttribution {
  val declaration = declarations.singleOrNull { it.projectionName == projectionName }
    ?: return LaunchRejectionAttribution(
      projectionContractId = "feature_task_runtime.$projectionName",
      producerIteration = fallbackProducerIteration,
    )
  val declaredProducer = declaration.producerIteration
  return LaunchRejectionAttribution(
    projectionContractId = declaration.projectionContractId,
    producerIteration = FeatureTaskRuntimeProducerIteration(
      phaseId = declaredProducer.phaseId,
      iteration = currentProducerIteration(declaredProducer.phaseId) ?: declaredProducer.iteration,
    ),
  )
}

private const val FEATURE_SPEC_ROOT = ".feature-specs"

fun isFeatureSpecPathForIssue(path: String, issueKey: String): Boolean {
  val normalized = path.trim().trimEnd('/')
  if (normalized == FEATURE_SPEC_ROOT) return true
  if (!normalized.startsWith("$FEATURE_SPEC_ROOT/")) return false
  val issueDirectory = normalized.removePrefix("$FEATURE_SPEC_ROOT/").substringBefore('/')
  val key = issueKey.trim()
  return issueDirectory == key || issueDirectory.startsWith("$key-")
}

fun reconcileCheckpointPathInventory(
  repoRoot: Path,
  issueKey: String,
  specReference: String,
  paths: List<String>,
): List<String> {
  val specPath = Path.of(specReference)
    .let { path -> if (path.isAbsolute) repoRoot.relativize(path) else path }
    .normalize()
    .toString()
  return paths.filterNot { path ->
    path == specPath || isFeatureSpecPathForIssue(path, issueKey)
  }.distinct()
}

fun resolveReviewPassNumber(reservedPassNumber: Int?, completedReviewPassCount: Int): Int {
  reservedPassNumber?.let { pass ->
    require(pass == 1) { "Review reservation allows only pass 1, was $pass." }
  }
  require(completedReviewPassCount <= 1) {
    "Review completed-pass count cannot exceed one, was $completedReviewPassCount."
  }
  return 1
}

class FeatureTaskRuntimeRunLoop internal constructor(
  val recorder: FeatureTaskRuntimePhaseRecorder,
  val goalContinuationRecorder: FeatureTaskRuntimeGoalContinuationRecorder,
  val outputValidator: FeatureTaskRuntimePhaseOutputValidator,
  val phaseGates: FeatureTaskRuntimePhaseGates,
  val subtaskLauncher: GoalRunnerSubtaskLauncher,
  val phaseSettlementService: FeatureTaskPhaseSettlementService,
  val activityStampWriter: AgentActivityStampWriter,
  val clock: Clock,
  context: FeatureTaskRuntimeRunLoopContext,
  val diagnostics: RuntimeDiagnostics,
) {
  val request = context.request
  val state = context.state
  val observability = context.observability
  val specSource = context.specSource
  val transitions = context.transitions
  val phaseTokenAccumulator = context.phaseTokenAccumulator
  val branchSetupRunner get() = phaseGates.branchSetupRunner
  val planningStopper get() = phaseGates.planningStopper
  val gitOperations get() = phaseGates.gitOperations
  val planningProjectionValidator get() = phaseGates.planningProjectionValidator
  val buildReceiptValidator get() = phaseGates.buildReceiptValidator
  val validationGateCoordinator get() = phaseGates.validationGateCoordinator
  val buildGateCoordinator get() = phaseGates.buildGateCoordinator

  internal val session = FeatureTaskRuntimeRunLoopSession(
    operatorBlockRetry = recorder
      .loadOperatorBlockRetry(request.workflowId)
      ?.takeIf { retry ->
        state.recordFor(retry.phaseId)?.status.let { status ->
          status == null || status.workflowStepStatus() == WorkflowStepStatus.PENDING
        }
      },
    initialPendingReentry = null,
  )

  init {
    val resumed = FeatureTaskRuntimeRunLoopDrive.resumedReentry(
      request, state, recorder,
      goalContinuationRecorder,
      outputValidator,
      transitions,
    )
    session.transitionReentryPair(resumed, resumed)
  }


  fun drive() {
    FeatureTaskRuntimeRunLoopDrive.invalidateReviewGenerationIfNeeded(request, state, recorder, session, transitions)
    FeatureTaskRuntimeRunLoopDrive.runPhaseDriveLoop(
      request, state, recorder, observability, session,
      goalContinuationRecorder,
      outputValidator,
      diagnostics,
      phaseGates,
      transitions,
      specSource,
      phaseTokenAccumulator,
      subtaskLauncher,
      ::advance,
    )
  }

  internal fun advance(phaseId: String): PhaseSettlement {
    FeatureTaskRuntimeRunLoopDrive.phaseEntryBlockReason(
      request, state, recorder, session,
      goalContinuationRecorder,
      outputValidator,
      diagnostics,
      phaseGates,
      transitions,
      specSource,
      phaseTokenAccumulator,
      phaseId,
    )?.let { reason ->
      FeatureTaskRuntimeRunLoopPlanningBranch.blockAt(request, state, session, phaseId, reason)
      return PhaseSettlement.stop()
    }
    if (phaseId == FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_REVIEW && isGoalContinuationRun(request)) {
      val carriedForward = FeatureTaskRuntimeRunLoopDrive.carriedForwardGoalReviewSettlement(
        request, state, recorder, session,
        goalContinuationRecorder,
        outputValidator,
        transitions,
      )
      if (carriedForward != null) {
        return carriedForward
      }
    }
    val reason = FeatureTaskRuntimeRunLoopDrive.advancePhaseReason(
      request, state, recorder, observability, session,
      goalContinuationRecorder,
      phaseSettlementService,
      outputValidator,
      diagnostics,
      phaseGates,
      clock,
      transitions,
      specSource,
      phaseTokenAccumulator,
      subtaskLauncher,
      activityStampWriter,
      phaseId,
    )
    return FeatureTaskRuntimeRunLoopDrive.settleAdvanceOutcome(request, state, session, phaseId, reason)
  }

  fun report(): FeatureTaskRuntimeRunReport {
    val branch = session.resolvedBranch
      ?: recorder.loadResolvedBranch(request.workflowId)?.branch
    return session.decomposed ?: session.paused?.let { report ->
      if (report.resolvedBranch == null && branch != null) report.copy(resolvedBranch = branch) else report
    } ?: session.blocked?.let { report ->
      if (report.resolvedBranch == null && branch != null) report.copy(resolvedBranch = branch) else report
    } ?: FeatureTaskRuntimeRunReport.Completed(
      issueKey = request.issueKey,
      workflowId = request.workflowId,
      featureSize = request.runInvariants.featureSize.name,
      completedPhaseIds = state.completedPhaseIds(),
      resolvedBranch = branch,
    )
  }

  fun applyOperatorDecision(): String? = buildString {
    append("Operator decisions over review remediation are removed; ")
    append("the run advances to validate after one implement_fix round.")
    request.goalContinuation?.let {
      append(
        " Recover with: '${recommendedDurableChildRecoveryCommand(
          it.parentIssueKey,
          it.subtaskId,
          null,
          null,
        )}'.",
      )
    }
  }
}

