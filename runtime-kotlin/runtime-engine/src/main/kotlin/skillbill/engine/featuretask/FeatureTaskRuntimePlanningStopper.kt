package skillbill.engine.featuretask
import me.tatarka.inject.annotations.Inject
import skillbill.application.decomposition.decompositionManifestPath
import skillbill.application.decomposition.parentSpecPath
import skillbill.engine.featuretask.model.FeatureTaskRuntimePlanningStopDecision
import skillbill.engine.featuretask.model.FeatureTaskRuntimeRunEvent
import skillbill.engine.featuretask.model.FeatureTaskRuntimeRunReport
import skillbill.engine.featuretask.model.FeatureTaskRuntimeRunRequest
import skillbill.error.SkillBillRuntimeException
import skillbill.ports.diagnostics.RuntimeDiagnostics
import skillbill.workflow.decomposition.model.SpecSource
import skillbill.workflow.taskruntime.FeatureTaskRuntimePhaseOutputValidator
import skillbill.workflow.taskruntime.FeatureTaskRuntimePhaseWorkflowDefinition
import skillbill.workflow.taskruntime.decomposePlanOutcomeFromPhaseOutput
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeDecomposePlanOutcome
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeDecomposeTerminal
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimePhaseOutput
import skillbill.workflow.taskruntime.model.requireAcceptedOutput
import java.io.IOException

@Inject
class FeatureTaskRuntimePlanningStopper(
  private val outputValidator: FeatureTaskRuntimePhaseOutputValidator,
  private val decompositionPlanner: FeatureTaskRuntimeDecompositionPlanner,
  private val decomposeTerminalRecorder: FeatureTaskRuntimeDecomposeTerminalRecorder,
  private val diagnostics: RuntimeDiagnostics,
) {

  fun resolve(
    request: FeatureTaskRuntimeRunRequest,
    completedOutput: FeatureTaskRuntimePhaseOutput,
    completedPhaseIds: List<String>,
    resolvedBranch: String?,
    specSource: SpecSource,
  ): FeatureTaskRuntimePlanningStopDecision {
    if (isGoalContinuationRun(request)) {
      return FeatureTaskRuntimePlanningStopDecision.Proceed
    }

    val recordedTerminal = decomposeTerminalRecorder.loadDecomposeTerminal(request.workflowId)
    return if (recordedTerminal != null) {
      FeatureTaskRuntimePlanningStopDecision.Decomposed(
        recordedTerminal.toRunReport(request, completedPhaseIds, resolvedBranch),
      )
    } else {
      resolveFreshPlanOutput(request, completedOutput, completedPhaseIds, resolvedBranch, specSource)
    }
  }

  private fun resolveFreshPlanOutput(
    request: FeatureTaskRuntimeRunRequest,
    completedOutput: FeatureTaskRuntimePhaseOutput,
    completedPhaseIds: List<String>,
    resolvedBranch: String?,
    specSource: SpecSource,
  ): FeatureTaskRuntimePlanningStopDecision {
    return try {
      resolveFromPlanOutput(request, completedOutput, completedPhaseIds, resolvedBranch, specSource)
    } catch (error: SkillBillRuntimeException) {
      FeatureTaskRuntimePlanningStopDecision.Blocked(malformedDecomposeReason(error.message.orEmpty()))
    } catch (error: IOException) {
      FeatureTaskRuntimePlanningStopDecision.Blocked(malformedDecomposeReason(error.message.orEmpty()))
    }
  }

  private fun resolveFromPlanOutput(
    request: FeatureTaskRuntimeRunRequest,
    completedOutput: FeatureTaskRuntimePhaseOutput,
    completedPhaseIds: List<String>,
    resolvedBranch: String?,
    specSource: SpecSource,
  ): FeatureTaskRuntimePlanningStopDecision {
    val parsed = outputValidator
      .validatePhaseOutput(completedOutput.payload, FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_PLAN)
      .requireAcceptedOutput(FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_PLAN)
      .normalizedOutput
      .envelopePayload()
    val outcome = decomposePlanOutcomeFromPhaseOutput(parsed, specSource)
      ?: return FeatureTaskRuntimePlanningStopDecision.Proceed
    val terminal = writeDecompositionTerminal(request, outcome)
    decomposeTerminalRecorder.recordDecomposeTerminal(request.workflowId, terminal)
    emitDecomposedAtPlanning(request, terminal)
    return FeatureTaskRuntimePlanningStopDecision.Decomposed(
      terminal.toRunReport(request, completedPhaseIds, resolvedBranch),
    )
  }

  private fun writeDecompositionTerminal(
    request: FeatureTaskRuntimeRunRequest,
    outcome: FeatureTaskRuntimeDecomposePlanOutcome,
  ): FeatureTaskRuntimeDecomposeTerminal {
    val writeResult = decompositionPlanner.writeDecomposition(
      repoRoot = request.repoRoot,
      issueKey = request.issueKey,
      runInvariants = request.runInvariants,
      outcome = outcome,
    )
    return FeatureTaskRuntimeDecomposeTerminal(
      reason = outcome.reason,
      parentSpecPath = writeResult.parentSpecPath,
      decompositionManifestPath = requireNotNull(writeResult.decompositionManifestPath) {
        "Decomposed feature-spec write result must include a decomposition manifest path."
      },
      subtaskSpecPaths = writeResult.subtaskSpecPaths,
    )
  }

  private fun emitDecomposedAtPlanning(
    request: FeatureTaskRuntimeRunRequest,
    terminal: FeatureTaskRuntimeDecomposeTerminal,
  ) {
    emitFeatureTaskRuntimeEventSafely(
      diagnostics = diagnostics,
      seam = "DecomposedAtPlanning event-sink emission",
    ) {
      request.eventSink.emit(
        FeatureTaskRuntimeRunEvent.DecomposedAtPlanning(
          workflowId = request.workflowId,
          phaseId = FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_PLAN,
          reason = terminal.reason,
          subtaskCount = terminal.subtaskCount,
          parentSpecPath = terminal.parentSpecPath,
          decompositionManifestPath = terminal.decompositionManifestPath,
        ),
      )
    }
  }

  private fun FeatureTaskRuntimeDecomposeTerminal.toRunReport(
    request: FeatureTaskRuntimeRunRequest,
    completedPhaseIds: List<String>,
    resolvedBranch: String?,
  ): FeatureTaskRuntimeRunReport.Decomposed = FeatureTaskRuntimeRunReport.Decomposed(
    issueKey = request.issueKey,
    workflowId = request.workflowId,
    featureSize = request.runInvariants.featureSize.name,
    reason = reason,
    completedPhaseIds = completedPhaseIds,
    parentSpecPath = parentSpecPath,
    decompositionManifestPath = decompositionManifestPath,
    subtaskSpecPaths = subtaskSpecPaths,
    resolvedBranch = resolvedBranch,
  )

  private fun malformedDecomposeReason(detail: String): String {
    val bounded = detail.takeIf(String::isNotBlank)?.let {
      if (it.length <= MALFORMED_DETAIL_MAX_CHARS) it else it.take(MALFORMED_DETAIL_MAX_CHARS) + "… [truncated]"
    }
    return "Plan declared mode 'decompose' but emitted a malformed decomposition package; the runtime " +
      "blocks at planning rather than crashing or advancing to implement." +
      (bounded?.let { " Schema problem: $it" } ?: "")
  }

  private companion object {
    const val MALFORMED_DETAIL_MAX_CHARS = 500
  }
}
