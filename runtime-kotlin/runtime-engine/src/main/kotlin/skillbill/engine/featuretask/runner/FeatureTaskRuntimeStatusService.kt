package skillbill.engine.featuretask.runner




import skillbill.engine.featuretask.phase.core.FeatureTaskRuntimeCurrentPhaseExecutionContext
import skillbill.engine.featuretask.phase.core.FeatureTaskRuntimeCurrentPhaseExecutionDeriver
import skillbill.engine.featuretask.phase.record.FeatureTaskRuntimeDecomposeTerminalRecorder
import skillbill.engine.featuretask.phase.record.FeatureTaskRuntimePhaseRecorder
import skillbill.engine.featuretask.runloop.state.FeatureTaskRuntimeRunInvariantsStore
import skillbill.engine.featuretask.runloop.state.FeatureTaskRuntimeRunStateReconstruction
import skillbill.engine.featuretask.lifecycle.continuation.agentAttributionFromPhaseState
import me.tatarka.inject.annotations.Inject
import skillbill.contracts.JsonCodec
import skillbill.contracts.SharedPayloadKeys
import skillbill.contracts.workflow.ValidationEvidencePayloadKeys
import skillbill.engine.featuretask.model.core.FeatureTaskRuntimeDecomposeTerminalStatus
import skillbill.engine.featuretask.model.core.FeatureTaskRuntimeDegradedDiagnosticStatus
import skillbill.engine.featuretask.model.core.FeatureTaskRuntimePhaseStatus
import skillbill.engine.featuretask.model.core.FeatureTaskRuntimeStatusProjection
import skillbill.engine.featuretask.model.core.FeatureTaskRuntimeStatusRequest
import skillbill.workflow.model.WorkflowStepStatus
import skillbill.workflow.model.workflowStepStatus
import skillbill.workflow.taskruntime.FeatureTaskRuntimePhaseWorkflowDefinition
import skillbill.workflow.taskruntime.decodeValidationGateExecutionEvidenceFromArtifact
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeDecomposeTerminal
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimePhaseLedgerAction
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimePhaseLedgerEntry
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimePhaseRecord
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeValidationGateExecutionEvidence
import skillbill.workflow.taskruntime.model.orLegacyValidate

@Inject
class FeatureTaskRuntimeStatusService(
  val recorder: FeatureTaskRuntimePhaseRecorder,
  val runInvariantsStore: FeatureTaskRuntimeRunInvariantsStore,
  private val decomposeTerminalRecorder: FeatureTaskRuntimeDecomposeTerminalRecorder,
) {
  val currentPhaseExecutionDeriver = FeatureTaskRuntimeCurrentPhaseExecutionDeriver()

  fun status(request: FeatureTaskRuntimeStatusRequest): FeatureTaskRuntimeStatusProjection? {
    val records = recorder.loadPhaseRecords(request.workflowId) ?: return null
    val decomposeTerminal = decomposeTerminalRecorder.loadDecomposeTerminal(request.workflowId)
    val ledger = recorder.loadPhaseLedger(request.workflowId).orEmpty()
    return buildStatusProjection(request, records, decomposeTerminal, ledger)
  }

  fun ledgerBlockedPhaseIds(
    ledger: List<FeatureTaskRuntimePhaseLedgerEntry>,
    durableBlockedPhaseIds: Set<String>,
  ): Set<String> = ledger
    .groupBy { it.phaseId }
    .filterKeys { it !in durableBlockedPhaseIds }
    .filterValues { entries ->
      entries.maxByOrNull { it.sequenceNumber }?.action == FeatureTaskRuntimePhaseLedgerAction.BLOCKED
    }
    .keys
}

fun FeatureTaskRuntimeStatusService.buildStatusProjection(
  request: FeatureTaskRuntimeStatusRequest,
  records: Map<String, FeatureTaskRuntimePhaseRecord>,
  decomposeTerminal: FeatureTaskRuntimeDecomposeTerminal?,
  ledger: List<FeatureTaskRuntimePhaseLedgerEntry>,
): FeatureTaskRuntimeStatusProjection {
  val statelessAuditInputs = FeatureTaskRuntimeRunStateReconstruction.normalizeForStatelessAudit(records, ledger)
  val normalizedRecords = statelessAuditInputs.records
  val normalizedLedger = statelessAuditInputs.ledger
  val durableBlockedPhaseIds = normalizedRecords
    .filterValues { record ->
      record.status.workflowStepStatus() == WorkflowStepStatus.BLOCKED
    }
    .keys
  val blockedPhaseIds = (
    durableBlockedPhaseIds +
      ledgerBlockedPhaseIds(normalizedLedger, durableBlockedPhaseIds)
    ).toSet()
  val phases = phaseStatuses(normalizedRecords, blockedPhaseIds, normalizedLedger)
  val terminalDecomposeRecorded = decomposeTerminal != null
  val qualityGateSelection = recorder
    .loadGoalContinuationQualityGateSelection(request.workflowId)
    .orLegacyValidate()
  val currentPhaseId = resolveCurrentPhaseId(
    terminalDecomposeRecorded,
    normalizedRecords,
    phases,
    normalizedLedger,
    qualityGateSelection,
  )
  val gateRunCount = gateRunCountFor(request, currentPhaseId)
  return statusProjectionFrom(
    StatusProjectionParts(
      request = request,
      phases = phases,
      terminalDecomposeRecorded = terminalDecomposeRecorded,
      currentPhaseId = currentPhaseId,
      gateRunCount = gateRunCount,
      records = normalizedRecords,
      ledger = normalizedLedger,
      decomposeTerminal = decomposeTerminal,
    ),
  )
}

private data class StatusProjectionParts(
  val request: FeatureTaskRuntimeStatusRequest,
  val phases: List<FeatureTaskRuntimePhaseStatus>,
  val terminalDecomposeRecorded: Boolean,
  val currentPhaseId: String?,
  val gateRunCount: Int?,
  val records: Map<String, FeatureTaskRuntimePhaseRecord>,
  val ledger: List<FeatureTaskRuntimePhaseLedgerEntry>,
  val decomposeTerminal: FeatureTaskRuntimeDecomposeTerminal?,
)

private fun FeatureTaskRuntimeStatusService.statusProjectionFrom(
  parts: StatusProjectionParts,
): FeatureTaskRuntimeStatusProjection {
  val request = parts.request
  val phases = parts.phases
  val terminalDecomposeRecorded = parts.terminalDecomposeRecorded
  return FeatureTaskRuntimeStatusProjection(
    workflowId = request.workflowId,
    featureSize = runInvariantsStore.resolve(request.workflowId)?.featureSize?.name,
    phases = phases,
    completeCount = phases.count { it.status.workflowStepStatus() == WorkflowStepStatus.COMPLETED },
    pendingCount = if (terminalDecomposeRecorded) {
      0
    } else {
      phases.count {
        it.status.workflowStepStatus()?.let(PHASE_TERMINAL_STATUSES::contains) != true
      }
    },
    blockedCount = if (terminalDecomposeRecorded) {
      0
    } else {
      phases.count { it.status.workflowStepStatus() == WorkflowStepStatus.BLOCKED }
    },
    currentPhaseId = parts.currentPhaseId,
    resolvedBranch = recorder.loadResolvedBranch(request.workflowId)?.branch,
    finalizingAgentId = agentAttributionFromPhaseState(
      recorder,
      request.workflowId,
    ).finalizingAgentId,
    decomposeTerminal = decomposeTerminalStatus(parts.decomposeTerminal),
    gateRunCount = parts.gateRunCount,
    validationGateExecutionEvidence = validationGateExecutionEvidence(parts.records),
    currentPhaseExecution = currentPhaseExecutionDeriver.derive(
      FeatureTaskRuntimeCurrentPhaseExecutionContext(
        currentPhaseId = parts.currentPhaseId,
        records = parts.records,
        phases = parts.phases,
        ledger = parts.ledger,
        gateRunCount = parts.gateRunCount,
      ),
    ),
    degradedDiagnostic = degradedDiagnosticStatus(request.workflowId),
    operatorDecisionPause = operatorDecisionPause(parts.records),
  )
}

private fun validationGateExecutionEvidence(
  records: Map<String, FeatureTaskRuntimePhaseRecord>,
): FeatureTaskRuntimeValidationGateExecutionEvidence? =
  records[FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_VALIDATE]
    ?.outputArtifact
    ?.let(JsonCodec::parseObjectOrNull)
    ?.let(JsonCodec::jsonElementToValue)
    ?.let(JsonCodec::anyToStringAnyMap)
    ?.get(SharedPayloadKeys.PRODUCED_OUTPUTS)
    ?.let(JsonCodec::anyToStringAnyMap)
    ?.get(ValidationEvidencePayloadKeys.VALIDATION_RESULT)
    ?.let(JsonCodec::anyToStringAnyMap)
    ?.let { raw ->
      if (
        !raw.containsKey(ValidationEvidencePayloadKeys.GATE_RUN_COUNT) &&
        !raw.containsKey(ValidationEvidencePayloadKeys.GATE_RUNS)
      ) {
        null
      } else {
        decodeValidationGateExecutionEvidenceFromArtifact(raw, "workflow-status.validate")
      }
    }

private fun FeatureTaskRuntimeStatusService.gateRunCountFor(
  request: FeatureTaskRuntimeStatusRequest,
  currentPhaseId: String?,
): Int? {
  val validationGateRunCount = recorder.loadValidationGateProgress(request.workflowId)
    ?.gateRunCount
  val buildGateRunCount = recorder.loadBuildGateProgress(request.workflowId)
    ?.gateRunCount
  return when (currentPhaseId) {
    FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_BUILD -> buildGateRunCount
    FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_VALIDATE -> validationGateRunCount
    else -> validationGateRunCount ?: buildGateRunCount
  }
}

fun FeatureTaskRuntimeStatusService.degradedDiagnosticStatus(
  workflowId: String,
): FeatureTaskRuntimeDegradedDiagnosticStatus? {
  val diagnosticSignals = recorder.loadDiagnosticSignals(workflowId)
  val latest = diagnosticSignals.lastOrNull() ?: return null
  return FeatureTaskRuntimeDegradedDiagnosticStatus(
    count = diagnosticSignals.size,
    failureClass = latest.failureClass.wireValue,
    phaseId = latest.phaseId,
    attempt = latest.attempt,
  )
}

fun FeatureTaskRuntimeStatusService.decomposeTerminalStatus(
  terminal: FeatureTaskRuntimeDecomposeTerminal?,
): FeatureTaskRuntimeDecomposeTerminalStatus? = terminal?.let {
  FeatureTaskRuntimeDecomposeTerminalStatus(
    reason = it.reason,
    parentSpecPath = it.parentSpecPath,
    decompositionManifestPath = it.decompositionManifestPath,
    subtaskSpecPaths = it.subtaskSpecPaths,
  )
}
