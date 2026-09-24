package skillbill.infrastructure.sqlite.goalrunner.outcome

import skillbill.contracts.SharedPayloadKeys
import skillbill.goalrunner.model.GoalRunnerSupervisionEvent
import skillbill.goalrunner.toPersistenceWire
import skillbill.infrastructure.sqlite.featuretask.artifact.decodePhaseLedger
import skillbill.infrastructure.sqlite.featuretask.artifact.decodePhaseRecords
import skillbill.infrastructure.sqlite.featuretask.artifact.encodeWorkflowArtifact
import skillbill.infrastructure.sqlite.goalrunner.control.workflowFamilyFor
import skillbill.ports.goalrunner.persistence.model.GoalRunnerBlockWrite
import skillbill.ports.persistence.UnitOfWork
import skillbill.ports.workflow.WorkflowStateRepository
import skillbill.ports.workflow.get
import skillbill.ports.workflow.model.WorkflowFamily
import skillbill.ports.workflow.save
import skillbill.workflow.engine.WorkflowEngine
import skillbill.workflow.engine.blockedStepId
import skillbill.workflow.engine.model.DurableWorkflowArtifactFamily
import skillbill.workflow.engine.model.WorkflowArtifactPatch
import skillbill.workflow.engine.model.WorkflowStepUpdates
import skillbill.workflow.engine.model.WorkflowUpdateInput
import skillbill.workflow.engine.model.isTerminalStatus
import skillbill.workflow.model.WorkflowStatus
import skillbill.workflow.model.WorkflowStepStatus
import skillbill.workflow.taskruntime.model.persistence.task.runtime.store.FEATURE_TASK_RUNTIME_PHASE_LEDGER_LIMIT
import skillbill.workflow.taskruntime.model.phase.FeatureTaskRuntimePhaseLedgerAction
import skillbill.workflow.taskruntime.model.phase.FeatureTaskRuntimePhaseLedgerEntry
import skillbill.workflow.taskruntime.model.phase.FeatureTaskRuntimePhaseRecord
import skillbill.workflow.taskruntime.phaseartifacts.asPendingForOperatorResume
import java.time.Clock
import java.time.ZoneOffset

internal class WorkflowGoalRunnerBlockWrites(
  private val engine: WorkflowEngine,
  private val clock: Clock,
) {
  fun markBlocked(
    workflowId: String,
    blockedReason: String,
    lastResumableStep: String,
    supervisionEvent: GoalRunnerSupervisionEvent?,
    workflowStates: WorkflowStateRepository,
  ): String? {
    val family = workflowFamilyFor(workflowStates, workflowId) ?: return null
    val record = family.get(workflowStates, workflowId) ?: return null
    return markBlocked(
      GoalRunnerBlockWrite(
        family = family,
        record = record,
        blockedReason = blockedReason,
        lastResumableStep = lastResumableStep,
        workflowStates = workflowStates,
        supervisionEvent = supervisionEvent,
      ),
    )
  }

  fun markBlocked(write: GoalRunnerBlockWrite): String {
    val steps = write.record.steps
    val definitionStepIds =
      if (write.family == WorkflowFamily.TASK_RUNTIME) {
        write.family.definition.stepIds.filterNot { it in write.family.loopOnlyStepIds }
      } else {
        emptyList()
      }
    val stepId = blockedStepId(write.record, steps, write.lastResumableStep, definitionStepIds)
    val attemptCount = steps.firstOrNull { it.stepId == stepId }?.attemptCount ?: 1
    val updated =
      engine.updateRecord(
        write.family.definition,
        write.record,
        WorkflowUpdateInput(
          terminalInstant = clock.instant(),
          workflowStatus = WorkflowStatus.BLOCKED,
          currentStepId = stepId,
          stepUpdates =
            WorkflowStepUpdates.from(
              listOf(
                mapOf(
                  SharedPayloadKeys.STEP_ID to stepId,
                  SharedPayloadKeys.STATUS to "blocked",
                  "attempt_count" to attemptCount,
                ),
              ),
            ),
          artifactsPatch =
            WorkflowArtifactPatch.from(
              buildMap {
                put("blocked_reason", write.blockedReason)
                write.supervisionEvent?.let { event -> put("supervision_event", event.toPersistenceWire()) }
              },
            ),
          sessionId = write.record.sessionId.orEmpty(),
        ),
      )
    write.family.save(write.workflowStates, updated)
    return stepId
  }

  fun reopenBlockedPhaseForOperatorResume(
    unitOfWork: UnitOfWork,
    workflowId: String,
    preferredPhaseId: String,
    reason: String,
  ): Boolean {
    val family = WorkflowFamily.TASK_RUNTIME
    val existing = family.get(unitOfWork.workflowStates, workflowId) ?: return false
    if (family.definition.isTerminalStatus(existing.workflowStatus)) {
      return false
    }
    val artifacts = existing.artifacts
    val phaseRecords = decodePhaseRecords(artifacts)
    val blockedRecord =
      operatorReopenablePhaseRecord(
        phaseRecords,
        preferredPhaseId,
        existing.workflowStatus,
      ) ?: return true
    family.save(
      unitOfWork.workflowStates,
      engine.updateRecord(
        family.definition,
        existing,
        operatorBlockedPhaseReopenUpdate(blockedRecord, phaseRecords, decodePhaseLedger(artifacts), reason),
      ),
    )
    return true
  }

  private fun operatorReopenablePhaseRecord(
    phaseRecords: Map<String, FeatureTaskRuntimePhaseRecord>,
    preferredPhaseId: String,
    workflowStatus: WorkflowStatus,
  ): FeatureTaskRuntimePhaseRecord? {
    val preferred = phaseRecords[preferredPhaseId]
    return preferred?.takeIf { it.status == WorkflowStepStatus.BLOCKED }
      ?: phaseRecords.values.firstOrNull { it.status == WorkflowStepStatus.BLOCKED }
      ?: preferred?.takeIf {
        workflowStatus == WorkflowStatus.BLOCKED && it.status == WorkflowStepStatus.RUNNING
      }
  }

  private fun operatorBlockedPhaseReopenUpdate(
    blockedRecord: FeatureTaskRuntimePhaseRecord,
    phaseRecords: Map<String, FeatureTaskRuntimePhaseRecord>,
    ledger: List<FeatureTaskRuntimePhaseLedgerEntry>,
    reason: String,
  ): WorkflowUpdateInput {
    val reopened =
      LinkedHashMap(phaseRecords).apply {
        this[blockedRecord.phaseId] = blockedRecord.asPendingForOperatorResume()
      }
    val retryEntry =
      FeatureTaskRuntimePhaseLedgerEntry(
        action = FeatureTaskRuntimePhaseLedgerAction.RETRY,
        sequenceNumber = (ledger.maxOfOrNull { it.sequenceNumber } ?: -1) + 1,
        timestamp = clock.instant().atOffset(ZoneOffset.UTC).toString(),
        phaseId = blockedRecord.phaseId,
        attemptCount = blockedRecord.attemptCount,
        resolvedAgentId = blockedRecord.resolvedAgentId,
      )
    return WorkflowUpdateInput(
      workflowStatus = WorkflowStatus.RUNNING,
      currentStepId = blockedRecord.phaseId,
      stepUpdates =
        WorkflowStepUpdates.from(
          listOf(
            mapOf(
              SharedPayloadKeys.STEP_ID to blockedRecord.phaseId,
              SharedPayloadKeys.STATUS to "pending",
              "attempt_count" to 0,
            ),
          ),
        ),
      artifactsPatch =
        WorkflowArtifactPatch.from(
          mapOf(
            DurableWorkflowArtifactFamily.FEATURE_TASK_RUNTIME_PHASE_RECORDS.entry(
              reopened.mapValues { (_, record) -> record.encodeWorkflowArtifact() },
            ),
            DurableWorkflowArtifactFamily.FEATURE_TASK_RUNTIME_PHASE_LEDGER.entry(
              (ledger.map { it.encodeWorkflowArtifact() } + retryEntry.encodeWorkflowArtifact()).takeLast(
                FEATURE_TASK_RUNTIME_PHASE_LEDGER_LIMIT,
              ),
            ),
            DurableWorkflowArtifactFamily.FEATURE_TASK_RUNTIME_OPERATOR_BLOCK_RETRY.entry(
              mapOf(
                SharedPayloadKeys.PHASE_ID to blockedRecord.phaseId,
                "reason" to reason,
                "retried_at" to clock.instant().atOffset(ZoneOffset.UTC).toString(),
                "previous_blocked_reason" to blockedRecord.blockedReason,
                "previous_blocked_record" to blockedRecord.encodeWorkflowArtifact(),
              ),
            ),
          ),
        ),
      sessionId = "",
    )
  }
}
