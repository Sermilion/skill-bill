package skillbill.engine.featuretask

import skillbill.contracts.JsonCodec
import skillbill.contracts.SharedPayloadKeys
import skillbill.contracts.workflow.FEATURE_TASK_RUNTIME_CONTRACT_VERSION
import skillbill.engine.featuretask.model.FeatureTaskRuntimeRunRequest
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
    val stillMissing = missingUpstream(run.declaration, state.outputs())?.takeIf { it.isNotEmpty() }
    if (
      stillMissing == null &&
      state.persistedBlockedReason(FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_COMMIT_PUSH)
        ?.contains("requires upstream output", ignoreCase = true) == true
    ) {
      state.clearPersistedBlock(FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_COMMIT_PUSH)
    }
  }

  private fun reconcilePhase(
    phaseId: String,
    headSha: String,
    state: FeatureTaskRuntimeRunState,
    diagnostics: RuntimeDiagnostics,
  ) {
    val record = state.recordFor(phaseId) ?: return
    if (record.status.workflowStepStatus() != WorkflowStepStatus.COMPLETED) return
    if (!record.outputArtifact.isNullOrBlank()) return
    val output = syntheticOutput(phaseId, headSha, record.attemptCount) ?: return
    val accepted = state.outputValidator.validatePhaseOutput(output.payload, phaseId).requireAcceptedOutput(phaseId)
    state.recordCompleted(
      FeatureTaskRuntimePhaseOutput(
        phaseId = phaseId,
        iteration = record.attemptCount.coerceAtLeast(1),
        payload = accepted.normalizedOutput.canonicalJson,
        normalizedOutput = accepted.normalizedOutput,
      ),
    )
    runCatching {
      diagnostics.warning(
        "seam=FeatureTaskRuntimeCommitPushUpstreamHeadFallback.reconcile " +
          "value_used='repository HEAD $headSha' " +
          "value_expected=a settled durable output for phase '$phaseId' " +
          "cause=commit_push resumed while '$phaseId' was completed without output; " +
          "the runtime synthesized a HEAD-backed receipt so finalisation can proceed",
      )
    }
  }

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
