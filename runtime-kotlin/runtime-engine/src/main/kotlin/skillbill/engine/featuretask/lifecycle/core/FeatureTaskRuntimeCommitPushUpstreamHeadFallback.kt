package skillbill.engine.featuretask.lifecycle.core




import skillbill.engine.featuretask.phase.core.FeatureTaskRuntimePhaseGates
import skillbill.engine.featuretask.runloop.state.FeatureTaskRuntimeRunState
import skillbill.engine.featuretask.runloop.core.PhaseRun
import skillbill.engine.featuretask.runner.missingUpstream
import skillbill.contracts.JsonCodec
import skillbill.contracts.SharedPayloadKeys
import skillbill.contracts.workflow.FEATURE_TASK_RUNTIME_CONTRACT_VERSION
import skillbill.engine.diagnostics.RuntimeDiagnosticsBestEffortWarning
import skillbill.engine.featuretask.model.core.FeatureTaskRuntimeRunRequest
import skillbill.engine.featuretask.validation.FeatureTaskRuntimeBuildGateCoordinator
import skillbill.ports.diagnostics.RuntimeDiagnostics
import skillbill.ports.workflow.gitops.model.WorkflowGitOperationResult
import skillbill.workflow.model.WorkflowStepStatus
import skillbill.workflow.model.workflowStepStatus
import skillbill.workflow.taskruntime.FeatureTaskRuntimePhaseWorkflowDefinition
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimePhaseOutput
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeValidationGateRunRecord
import skillbill.workflow.taskruntime.model.requireAcceptedOutput

private const val HISTORY_RESULT_KEY = "history_result"
private const val CHANGED_PATHS_KEY = "changed_paths"
private const val DECISIONS_RECORDED_KEY = "decisions_recorded"

internal object FeatureTaskRuntimeCommitPushUpstreamHeadFallback {
  fun reconcile(
    request: FeatureTaskRuntimeRunRequest,
    run: PhaseRun,
    state: FeatureTaskRuntimeRunState,
    phaseGates: FeatureTaskRuntimePhaseGates,
    diagnostics: RuntimeDiagnostics,
  ) {
    if (run.phaseId != FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_COMMIT_PUSH) return
    val headSha = phaseGates.gitOperations.headCommitSha(request.repoRoot)
      .takeIf { it is WorkflowGitOperationResult.Ok }
      ?.value
      ?.trim()
      ?.takeIf(String::isNotBlank)
      ?: return
    val missing = missingUpstream(run.declaration, state.outputs()) ?: return
    if (missing.isEmpty()) return
    missing.forEach { phaseId ->
      reconcilePhase(phaseId, headSha, state, diagnostics)
    }
    clearUpstreamPersistedBlockIfRecovered(run, state)
  }

  internal fun clearUpstreamPersistedBlockIfRecovered(run: PhaseRun, state: FeatureTaskRuntimeRunState) {
    if (run.phaseId != FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_COMMIT_PUSH) return
    val reason = state.persistedBlockedReason(FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_COMMIT_PUSH)
      ?: return
    if (!reason.contains("requires upstream output", ignoreCase = true)) return
    if (missingUpstream(run.declaration, state.outputs())?.isNotEmpty() == true) return
    state.clearPersistedBlock(FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_COMMIT_PUSH)
  }

  private fun reconcilePhase(
    phaseId: String,
    headSha: String,
    state: FeatureTaskRuntimeRunState,
    diagnostics: RuntimeDiagnostics,
  ) {
    val record = state.recordFor(phaseId)
    val attemptCount = record?.attemptCount?.coerceAtLeast(1) ?: 1
    val output = phaseId
      .takeIf(::supportsHeadFallback)
      ?.takeIf { state.outputFor(it) == null }
      ?.takeIf {
        record == null || record.status.workflowStepStatus() == WorkflowStepStatus.COMPLETED
      }
      ?.let { syntheticOutput(it, headSha, attemptCount) }
    val accepted = output?.let {
      runCatching {
        state.outputValidator.validatePhaseOutput(it.payload, phaseId).requireAcceptedOutput(phaseId)
      }.getOrNull()
    }
    if (output != null && accepted != null) {
      state.recordCompleted(
        FeatureTaskRuntimePhaseOutput(
          phaseId = phaseId,
          iteration = attemptCount,
          payload = accepted.normalizedOutput.canonicalJson,
          normalizedOutput = accepted.normalizedOutput,
        ),
      )
      RuntimeDiagnosticsBestEffortWarning.record(
        diagnostics,
        "seam=FeatureTaskRuntimeCommitPushUpstreamHeadFallback.reconcile " +
          "value_used='repository HEAD $headSha' " +
          "value_expected=a settled durable output for phase '$phaseId' " +
          "cause=commit_push resumed while '$phaseId' was completed without output; " +
          "the runtime synthesized a HEAD-backed receipt so finalisation can proceed",
      )
    }
  }

  private fun supportsHeadFallback(phaseId: String): Boolean =
    phaseId == FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_BUILD ||
      phaseId == FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_WRITE_HISTORY

  private fun syntheticOutput(phaseId: String, headSha: String, attemptCount: Int): FeatureTaskRuntimePhaseOutput? =
    when (phaseId) {
      FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_BUILD ->
        FeatureTaskRuntimeBuildGateCoordinator.runtimeOwnedBuildOutput(
          repositoryCheckpoint = headSha,
          measurements = listOf(
            FeatureTaskRuntimeValidationGateRunRecord(
              durationMs = 0,
              outcome = "passed",
              cacheMode = "cache_eligible",
              executedWorkUnits = 0,
              executedChecks = emptyList(),
            ),
          ),
        ).let { built ->
          FeatureTaskRuntimePhaseOutput(
            phaseId = built.phaseId,
            iteration = attemptCount.coerceAtLeast(1),
            payload = built.payload,
          )
        }
      FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_WRITE_HISTORY -> {
        val payload = JsonCodec.mapToJsonString(
          mapOf(
            SharedPayloadKeys.CONTRACT_VERSION to FEATURE_TASK_RUNTIME_CONTRACT_VERSION,
            SharedPayloadKeys.PHASE_ID to FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_WRITE_HISTORY,
            SharedPayloadKeys.STATUS to "completed",
            SharedPayloadKeys.SUMMARY to "Boundary history settled from repository HEAD.",
            SharedPayloadKeys.PRODUCED_OUTPUTS to mapOf(
              HISTORY_RESULT_KEY to mapOf(
                CHANGED_PATHS_KEY to emptyList<String>(),
                DECISIONS_RECORDED_KEY to emptyList<String>(),
              ),
            ),
          ),
        )
        FeatureTaskRuntimePhaseOutput(
          phaseId = FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_WRITE_HISTORY,
          iteration = attemptCount.coerceAtLeast(1),
          payload = payload,
        )
      }
      else -> null
    }
}
