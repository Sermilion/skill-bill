package skillbill.cli.featuretask

import skillbill.contracts.SharedPayloadKeys
import skillbill.contracts.workflow.ValidationEvidencePayloadKeys
import skillbill.engine.featuretask.model.FeatureTaskRuntimePhaseStatus
import skillbill.engine.featuretask.model.FeatureTaskRuntimeStatusProjection
import skillbill.workflow.taskruntime.presentationWireMap

internal fun FeatureTaskRuntimeStatusProjection?.toRuntimeStatusCliMap(workflowId: String): Map<String, Any?> =
  this?.let {
    linkedMapOf<String, Any?>(
      SharedPayloadKeys.STATUS to "ok",
      SharedPayloadKeys.WORKFLOW_ID to it.workflowId,
      "feature_size" to it.featureSize,
      "complete_count" to it.completeCount,
      "pending_count" to it.pendingCount,
      "blocked_count" to it.blockedCount,
      "current_phase" to it.currentPhaseId,
      "resolved_branch" to it.resolvedBranch,
      "finalizing_agent_id" to it.finalizingAgentId,
      "gate_run_count" to it.gateRunCount,
      ValidationEvidencePayloadKeys.VALIDATION_STATUS to
        it.validationGateExecutionEvidence?.validationStatus,
      ValidationEvidencePayloadKeys.CHECKS to it.validationGateExecutionEvidence?.checks,
      ValidationEvidencePayloadKeys.GATE_RUN_COUNT to it.validationGateExecutionEvidence?.gateRunCount,
      ValidationEvidencePayloadKeys.GATE_RUNS to
        it.validationGateExecutionEvidence?.gateRuns?.map { run -> run.presentationWireMap() },
      "degraded_diagnostic" to it.degradedDiagnostic?.let { degraded ->
        linkedMapOf(
          "count" to degraded.count,
          "failure_class" to degraded.failureClass,
          SharedPayloadKeys.PHASE_ID to degraded.phaseId,
          "attempt" to degraded.attempt,
        )
      },
      "decompose_terminal" to it.decomposeTerminal?.let { terminal ->
        linkedMapOf(
          "reason" to terminal.reason,
          "parent_spec_path" to terminal.parentSpecPath,
          "decomposition_manifest_path" to terminal.decompositionManifestPath,
          "subtask_spec_paths" to terminal.subtaskSpecPaths,
          "subtask_count" to terminal.subtaskCount,
          "guidance" to DECOMPOSE_GUIDANCE,
        )
      },
      "phases" to it.phases.map(FeatureTaskRuntimePhaseStatus::toRuntimePhaseStatusCliMap),
    )
  } ?: linkedMapOf(
    SharedPayloadKeys.STATUS to "not_found",
    SharedPayloadKeys.WORKFLOW_ID to workflowId,
    "feature_size" to null,
    "complete_count" to 0,
    "pending_count" to 0,
    "blocked_count" to 0,
    "current_phase" to null,
    "resolved_branch" to null,
    "finalizing_agent_id" to null,
    "degraded_diagnostic" to null,
    "decompose_terminal" to null,
    "phases" to emptyList<Map<String, Any?>>(),
  )

internal fun FeatureTaskRuntimePhaseStatus.toRuntimePhaseStatusCliMap(): Map<String, Any?> = linkedMapOf(
  SharedPayloadKeys.PHASE_ID to phaseId,
  SharedPayloadKeys.STATUS to status,
  "attempt_count" to attemptCount,
  "resolved_agent_id" to resolvedAgentId,
  "execution_origin" to executionOrigin,
  "continuation_kind" to continuationKind,
  "finished" to finished,
)

internal fun Map<String, Any?>.runtimeStatusExitCode(): Int = if (this[SharedPayloadKeys.STATUS] == "ok") 0 else 1

internal fun runtimeStatusText(payload: Map<String, Any?>): String = buildString {
  appendLine("feature-task-runtime: ${payload[SharedPayloadKeys.WORKFLOW_ID]}")
  appendLine("status: ${payload[SharedPayloadKeys.STATUS]}")
  appendLine("feature_size: ${payload["feature_size"] ?: "unknown"}")
  appendLine("complete: ${payload["complete_count"]}")
  appendLine("pending: ${payload["pending_count"]}")
  appendLine("blocked: ${payload["blocked_count"]}")
  appendLine("current_phase: ${payload["current_phase"] ?: "none"}")
  appendLine("resolved_branch: ${payload["resolved_branch"] ?: "none"}")
  appendLine("finalizing_agent: ${payload["finalizing_agent_id"] ?: "none"}")
  appendRuntimeValidationGateEvidence(payload)
  (payload["degraded_diagnostic"] as? Map<*, *>)?.let { degraded ->
    appendLine("degraded_diagnostic_count: ${degraded["count"]}")
    appendLine("degraded_diagnostic_failure_class: ${degraded["failure_class"]}")
    appendLine("degraded_diagnostic_phase: ${degraded[SharedPayloadKeys.PHASE_ID]}")
    appendLine("degraded_diagnostic_attempt: ${degraded["attempt"]}")
  }
  (payload["decompose_terminal"] as? Map<*, *>)?.let { terminal ->
    appendLine("decomposition_reason: ${terminal["reason"]}")
    appendLine("subtask_count: ${terminal["subtask_count"]}")
    appendLine("parent_spec_path: ${terminal["parent_spec_path"]}")
    appendLine("decomposition_manifest_path: ${terminal["decomposition_manifest_path"]}")
    (terminal["subtask_spec_paths"] as? List<*>).orEmpty().forEach { appendLine("subtask_spec_path: $it") }
    appendLine("guidance: ${terminal["guidance"]}")
  }
  (payload["phases"] as? List<*>).orEmpty().forEach { rawPhase ->
    val phase = rawPhase as? Map<*, *> ?: return@forEach
    appendLine(
      "phase: id=${phase[SharedPayloadKeys.PHASE_ID]} " +
        "status=${phase[SharedPayloadKeys.STATUS]} " +
        "attempt=${phase["attempt_count"]} " +
        "agent=${phase["resolved_agent_id"] ?: "none"} " +
        "origin=${phase["execution_origin"] ?: "none"} " +
        "finished=${phase["finished"]}",
    )
  }
}

private fun StringBuilder.appendRuntimeValidationGateEvidence(payload: Map<String, Any?>) {
  val checks = payload[ValidationEvidencePayloadKeys.CHECKS] as? List<*> ?: return
  appendLine("validation_gate_status: ${payload[ValidationEvidencePayloadKeys.VALIDATION_STATUS]}")
  appendLine("validation_gate_checks: ${checks.joinToString(",")}")
  appendLine("validation_gate_run_count: ${payload[ValidationEvidencePayloadKeys.GATE_RUN_COUNT]}")
  (payload[ValidationEvidencePayloadKeys.GATE_RUNS] as? List<*>).orEmpty().forEach { rawRun ->
    val run = rawRun as? Map<*, *> ?: return@forEach
    val runChecks = (run[ValidationEvidencePayloadKeys.EXECUTED_CHECKS] as? List<*>).orEmpty()
    appendLine(
      "validation_gate_run: cache_mode=${run[ValidationEvidencePayloadKeys.CACHE_MODE]} " +
        "outcome=${run[ValidationEvidencePayloadKeys.OUTCOME]} " +
        "executed_work_units=${run[ValidationEvidencePayloadKeys.EXECUTED_WORK_UNITS]} " +
        "checks=${runChecks.joinToString(",")}",
    )
  }
}
