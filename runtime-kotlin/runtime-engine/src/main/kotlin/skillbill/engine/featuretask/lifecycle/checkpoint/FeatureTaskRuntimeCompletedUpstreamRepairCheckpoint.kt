package skillbill.engine.featuretask.lifecycle.checkpoint

import skillbill.contracts.SharedPayloadKeys
import skillbill.engine.featuretask.model.subtask.CompletedUpstreamRepairRequest
import skillbill.engine.featuretask.runner.missingUpstream
import skillbill.engine.featuretask.runner.phaseDeclaration
import skillbill.workflow.engine.model.WorkflowArtifactPatch
import skillbill.workflow.engine.model.WorkflowStepUpdates
import skillbill.workflow.engine.model.WorkflowUpdateInput
import skillbill.workflow.model.WorkflowStatus
import skillbill.workflow.model.WorkflowStepStatus
import skillbill.workflow.model.workflowStepStatus
import skillbill.workflow.taskruntime.artifact.asWorkflowArtifactEntry
import skillbill.workflow.taskruntime.model.handoff.task.FeatureTaskRuntimePhaseOutput
import skillbill.workflow.taskruntime.model.persistence.task.runtime.persistence.FEATURE_TASK_RUNTIME_OPERATOR_BLOCK_RETRY_ARTIFACT_KEY
import skillbill.workflow.taskruntime.model.persistence.task.runtime.persistence.FEATURE_TASK_RUNTIME_PHASE_LEDGER_ARTIFACT_KEY
import skillbill.workflow.taskruntime.model.persistence.task.runtime.persistence.FEATURE_TASK_RUNTIME_PHASE_LEDGER_LIMIT
import skillbill.workflow.taskruntime.model.persistence.task.runtime.persistence.FEATURE_TASK_RUNTIME_PHASE_RECORDS_ARTIFACT_KEY
import skillbill.workflow.taskruntime.model.phase.FeatureTaskRuntimePhaseLedgerAction
import skillbill.workflow.taskruntime.model.phase.FeatureTaskRuntimePhaseLedgerEntry
import skillbill.workflow.taskruntime.model.phase.FeatureTaskRuntimePhaseRecord
import skillbill.workflow.taskruntime.phase.task.FeatureTaskRuntimePhaseWorkflowDefinition
import java.time.OffsetDateTime
import java.time.ZoneOffset
fun phasesToReopenForCompletedUpstreamRepair(
  request: CompletedUpstreamRepairRequest,
  recordedOutputs: List<FeatureTaskRuntimePhaseOutput>,
): List<String> {
  val phaseRecords = request.phaseRecords
  val resumePhaseId = request.resumePhaseId
  val featureSize = request.featureSize
  val qualityGateSelection = request.qualityGateSelection
  val stepOrder = FeatureTaskRuntimePhaseWorkflowDefinition.definition.stepIds
  return when {
    phaseRecords[resumePhaseId]?.status?.workflowStepStatus() == WorkflowStepStatus.BLOCKED -> listOf(resumePhaseId)
    else -> buildList {
      add(resumePhaseId)
      phaseRecords.forEach { (phaseId, record) ->
        if (record.status.workflowStepStatus() == WorkflowStepStatus.BLOCKED) {
          val missing = missingUpstream(
            phaseDeclaration(phaseId, featureSize, qualityGateSelection),
            recordedOutputs,
          )
          if (missing?.contains(resumePhaseId) == true) add(phaseId)
        }
      }
    }.distinct().sortedBy { stepOrder.indexOf(it).takeIf { index -> index >= 0 } ?: Int.MAX_VALUE }
  }
}

fun completedUpstreamRepairWorkflowUpdate(
  request: CompletedUpstreamRepairRequest,
  phasesToReopen: List<String>,
  reopenedRecords: Map<String, FeatureTaskRuntimePhaseRecord>,
  retryEntry: FeatureTaskRuntimePhaseLedgerEntry,
): WorkflowUpdateInput = WorkflowUpdateInput(
  workflowStatus = WorkflowStatus.RUNNING,
  currentStepId = request.resumePhaseId,
  stepUpdates = WorkflowStepUpdates.from(
    phasesToReopen.map { phaseId ->
      mapOf(
        SharedPayloadKeys.STEP_ID to phaseId,
        SharedPayloadKeys.STATUS to "pending",
        "attempt_count" to 0,
      )
    },
  ),
  artifactsPatch = WorkflowArtifactPatch.from(
    mapOf(
      FEATURE_TASK_RUNTIME_PHASE_RECORDS_ARTIFACT_KEY to
        reopenedRecords.mapValues { (_, record) -> record.asWorkflowArtifactEntry() },
      FEATURE_TASK_RUNTIME_PHASE_LEDGER_ARTIFACT_KEY to
        (request.ledger.map { it.asWorkflowArtifactEntry() } + retryEntry.asWorkflowArtifactEntry()).takeLast(
          FEATURE_TASK_RUNTIME_PHASE_LEDGER_LIMIT,
        ),
      FEATURE_TASK_RUNTIME_OPERATOR_BLOCK_RETRY_ARTIFACT_KEY to mapOf(
        SharedPayloadKeys.PHASE_ID to request.resumePhaseId,
        "reason" to request.reason,
        "retried_at" to OffsetDateTime.now(ZoneOffset.UTC).toString(),
        "previous_blocked_reason" to "completed_upstream_missing_output",
        "reopened_phase_ids" to phasesToReopen,
      ),
      "goal_continuation_outcome" to null,
    ),
  ),
  sessionId = "",
)

fun completedUpstreamRepairRetryEntry(request: CompletedUpstreamRepairRequest): FeatureTaskRuntimePhaseLedgerEntry =
  FeatureTaskRuntimePhaseLedgerEntry(
    action = FeatureTaskRuntimePhaseLedgerAction.RETRY,
    sequenceNumber = (request.ledger.maxOfOrNull { it.sequenceNumber } ?: -1) + 1,
    timestamp = OffsetDateTime.now(ZoneOffset.UTC).toString(),
    phaseId = request.resumePhaseId,
    attemptCount = requireNotNull(request.phaseRecords[request.resumePhaseId]).attemptCount,
    resolvedAgentId = requireNotNull(request.phaseRecords[request.resumePhaseId]).resolvedAgentId,
  )

fun settledPhaseOutputs(
  phaseRecords: Map<String, FeatureTaskRuntimePhaseRecord>,
): List<FeatureTaskRuntimePhaseOutput> = phaseRecords.values.mapNotNull { record ->
  record.outputArtifact?.takeIf(String::isNotBlank)?.let { artifact ->
    FeatureTaskRuntimePhaseOutput(
      phaseId = record.phaseId,
      iteration = record.attemptCount,
      payload = artifact,
    )
  }
}
