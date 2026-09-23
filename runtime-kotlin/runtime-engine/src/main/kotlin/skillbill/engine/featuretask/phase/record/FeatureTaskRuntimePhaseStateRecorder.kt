package skillbill.engine.featuretask.phase.record

import skillbill.contracts.JsonCodec
import skillbill.contracts.SharedPayloadKeys
import skillbill.engine.featuretask.model.phase.FeatureTaskRuntimePhaseStateRequest
import skillbill.engine.featuretask.persist.FeatureTaskRuntimeWorkflowPersistence
import skillbill.engine.featuretask.persist.RuntimeOwnedPersistenceBoundary
import skillbill.engine.featuretask.persist.WorkflowRowAdvance
import skillbill.engine.featuretask.persist.attemptStatusFor
import skillbill.engine.featuretask.persist.durationMillis
import skillbill.engine.featuretask.persist.stepUpdatesFrom
import skillbill.engine.featuretask.persist.workflowArtifactEntryMap
import skillbill.engine.featuretask.persist.workflowArtifactEntryMaps
import skillbill.engine.featuretask.persist.workflowStatusFor
import skillbill.engine.featuretask.phase.core.decodePhaseLedger
import skillbill.engine.featuretask.phase.core.decodePhaseRecords
import skillbill.engine.featuretask.phase.core.operatorBlockRetryFromWorkflowArtifacts
import skillbill.engine.featuretask.review.core.FeatureTaskRuntimeOutputVerification
import skillbill.ports.db.DatabaseSessionFactory
import skillbill.ports.workflow.get
import skillbill.ports.workflow.model.WorkflowFamily
import skillbill.workflow.goal.model.appendBoundedHistoryBySequence
import skillbill.workflow.model.WorkflowStepStatus
import skillbill.workflow.model.workflowStepStatus
import skillbill.workflow.taskruntime.artifact.asWorkflowArtifactEntry
import skillbill.workflow.taskruntime.artifact.decodeImplementationAttemptsFromArtifact
import skillbill.workflow.taskruntime.artifact.envelopeWireMap
import skillbill.workflow.taskruntime.artifact.implementationAttemptRecordWorkflowArtifact
import skillbill.workflow.taskruntime.artifact.operatorBlockRetryFromWorkflowArtifacts
import skillbill.workflow.taskruntime.artifact.validateImplementationAttemptRecord
import skillbill.workflow.taskruntime.model.core.FeatureTaskRuntimeWireArtifactValidator
import skillbill.workflow.taskruntime.model.persistence.task.runtime.implementation.FeatureTaskRuntimeImplementationAttempt
import skillbill.workflow.taskruntime.model.persistence.task.runtime.implementation.FeatureTaskRuntimeImplementationAttemptStatus
import skillbill.workflow.taskruntime.model.persistence.task.runtime.implementation.featureTaskRuntimeAppendImplementationAttempt
import skillbill.workflow.taskruntime.model.persistence.task.runtime.persistence.FEATURE_TASK_RUNTIME_FINDING_VERIFICATION_BOUNDARY_SELECTION_ARTIFACT_KEY
import skillbill.workflow.taskruntime.model.persistence.task.runtime.persistence.FEATURE_TASK_RUNTIME_FINDING_VERIFICATION_CHECKPOINT_ARTIFACT_KEY
import skillbill.workflow.taskruntime.model.persistence.task.runtime.persistence.FEATURE_TASK_RUNTIME_FINDING_VERIFICATION_DISPOSITIONS_ARTIFACT_KEY
import skillbill.workflow.taskruntime.model.persistence.task.runtime.persistence.FEATURE_TASK_RUNTIME_IMPLEMENTATION_ATTEMPTS_ARTIFACT_KEY
import skillbill.workflow.taskruntime.model.persistence.task.runtime.persistence.FEATURE_TASK_RUNTIME_PHASE_LEDGER_ARTIFACT_KEY
import skillbill.workflow.taskruntime.model.persistence.task.runtime.persistence.FEATURE_TASK_RUNTIME_PHASE_LEDGER_LIMIT
import skillbill.workflow.taskruntime.model.persistence.task.runtime.persistence.FEATURE_TASK_RUNTIME_PHASE_RECORDS_ARTIFACT_KEY
import skillbill.workflow.taskruntime.model.phase.FeatureTaskRuntimePhaseLedgerAction
import skillbill.workflow.taskruntime.model.phase.FeatureTaskRuntimePhaseLedgerAction.COMPLETE
import skillbill.workflow.taskruntime.model.phase.FeatureTaskRuntimePhaseLedgerEntry
import skillbill.workflow.taskruntime.model.phase.FeatureTaskRuntimePhaseRecord
import skillbill.workflow.taskruntime.model.repair.task.FeatureTaskRuntimeOperatorBlockRetry
import skillbill.workflow.taskruntime.phase.task.FeatureTaskRuntimePhaseWorkflowDefinition
import java.time.Clock

class FeatureTaskRuntimePhaseStateRecorder(
  val database: DatabaseSessionFactory,
  val workflowPersistence: FeatureTaskRuntimeWorkflowPersistence,
  val runtimeOwnedPersistence: RuntimeOwnedPersistenceBoundary,
  val implementationAttemptValidator: FeatureTaskRuntimeWireArtifactValidator,
  val clock: Clock,
) {
  fun recordPhaseState(request: FeatureTaskRuntimePhaseStateRequest): Boolean =
    database.transaction { unitOfWork ->
      val record =
        WorkflowFamily.TASK_RUNTIME.get(unitOfWork.workflowStates, request.workflowId)
          ?: return@transaction false
      val artifacts = FeatureTaskRuntimeWorkflowPersistence.artifactsFrom(record)
      val existingRecords = decodePhaseRecords(artifacts)
      val now = clock.instant().toString()
      val previous = existingRecords[request.phaseId]
      val phaseRecord = phaseRecordFor(request, previous, now)
      val updatedRecords = LinkedHashMap(existingRecords).apply { put(request.phaseId, phaseRecord) }
      val patch =
        mapOf(
          FEATURE_TASK_RUNTIME_PHASE_RECORDS_ARTIFACT_KEY to
            updatedRecords.mapValues { (_, value) -> value.asWorkflowArtifactEntry() },
        ) + implementationAttemptPatch(artifacts, request, attemptStatusFor(request)) +
          findingVerificationCheckpointPatch(request)
      workflowPersistence.persistArtifactsPatch(
        unitOfWork.workflowStates,
        record,
        patch,
        WorkflowRowAdvance(
          currentStepId = request.phaseId,
          workflowStatus = workflowStatusFor(request),
          stepUpdates = stepUpdatesFrom(updatedRecords),
        ),
      )
      true
    }

  fun recordCompletedPhase(request: FeatureTaskRuntimePhaseStateRequest): Boolean {
    require(request.status.workflowStepStatus() == WorkflowStepStatus.COMPLETED && request.finished)
    return recordCompletedPhaseWrite(request)
  }

  fun recordIncompleteImplementationAttempt(request: FeatureTaskRuntimePhaseStateRequest): Boolean =
    database.transaction { unitOfWork ->
      val record =
        WorkflowFamily.TASK_RUNTIME.get(unitOfWork.workflowStates, request.workflowId)
          ?: return@transaction false
      val patch =
        implementationAttemptPatch(
          FeatureTaskRuntimeWorkflowPersistence.artifactsFrom(record),
          request,
          FeatureTaskRuntimeImplementationAttemptStatus.INCOMPLETE,
        )
      if (patch.isEmpty()) return@transaction false
      workflowPersistence.persistArtifactsPatch(unitOfWork.workflowStates, record, patch)
      true
    }

  fun loadImplementationAttempts(workflowId: String): List<FeatureTaskRuntimeImplementationAttempt>? =
    database.read { unitOfWork ->
      val record =
        WorkflowFamily.TASK_RUNTIME.get(unitOfWork.workflowStates, workflowId)
          ?: return@read null
      implementationAttemptsFrom(FeatureTaskRuntimeWorkflowPersistence.artifactsFrom(record))
    }

  fun clearBackwardEdgeContext(
    workflowId: String,
    phaseIds: Collection<String>,
  ): Boolean =
    database.transaction { unitOfWork ->
      val record =
        WorkflowFamily.TASK_RUNTIME.get(unitOfWork.workflowStates, workflowId)
          ?: return@transaction false
      val existingRecords = decodePhaseRecords(FeatureTaskRuntimeWorkflowPersistence.artifactsFrom(record))
      val cleared = LinkedHashMap(existingRecords)
      phaseIds.forEach { phaseId ->
        val previous = existingRecords[phaseId] ?: return@forEach
        if (previous.loopId == null && previous.edgeIteration == null) {
          return@forEach
        }
        cleared[phaseId] = previous.copy(loopId = null, edgeIteration = null)
      }
      if (cleared == existingRecords) {
        return@transaction true
      }
      workflowPersistence.persistArtifactsPatch(
        unitOfWork.workflowStates,
        record,
        mapOf(
          FEATURE_TASK_RUNTIME_PHASE_RECORDS_ARTIFACT_KEY to
            cleared.mapValues { (_, value) -> value.asWorkflowArtifactEntry() },
        ),
      )
      true
    }

  fun loadPhaseRecords(workflowId: String): Map<String, FeatureTaskRuntimePhaseRecord>? =
    database.read { unitOfWork ->
      val record =
        WorkflowFamily.TASK_RUNTIME.get(unitOfWork.workflowStates, workflowId)
          ?: return@read null
      decodePhaseRecords(FeatureTaskRuntimeWorkflowPersistence.artifactsFrom(record))
    }

  fun loadOperatorBlockRetry(workflowId: String): FeatureTaskRuntimeOperatorBlockRetry? =
    database.read { unitOfWork ->
      val record =
        WorkflowFamily.TASK_RUNTIME.get(unitOfWork.workflowStates, workflowId)
          ?: return@read null
      val artifacts = FeatureTaskRuntimeWorkflowPersistence.artifactsFrom(record)
      val retry = operatorBlockRetryFromWorkflowArtifacts(artifacts) ?: return@read null
      val phaseEntries = decodePhaseLedger(artifacts).filter { it.phaseId == retry.phaseId }
      val latestRetry =
        phaseEntries.lastOrNull { it.action == FeatureTaskRuntimePhaseLedgerAction.RETRY }
          ?: return@read null
      val settledAfterRetry =
        phaseEntries.any { entry ->
          entry.sequenceNumber > latestRetry.sequenceNumber &&
            entry.action in
            setOf(
              FeatureTaskRuntimePhaseLedgerAction.BLOCKED,
              FeatureTaskRuntimePhaseLedgerAction.COMPLETE,
            )
        }
      retry.takeUnless { settledAfterRetry }
    }

  fun loadPhaseLedger(workflowId: String): List<FeatureTaskRuntimePhaseLedgerEntry>? =
    database.read { unitOfWork ->
      val record =
        WorkflowFamily.TASK_RUNTIME.get(unitOfWork.workflowStates, workflowId)
          ?: return@read null
      decodePhaseLedger(FeatureTaskRuntimeWorkflowPersistence.artifactsFrom(record))
    }
}

fun featureTaskRuntimePhaseRecordFor(
  request: FeatureTaskRuntimePhaseStateRequest,
  previous: FeatureTaskRuntimePhaseRecord?,
  now: String,
): FeatureTaskRuntimePhaseRecord {
  val firstStartedAt = previous?.firstStartedAt ?: now
  val startedAt =
    if (request.status.workflowStepStatus() == WorkflowStepStatus.RUNNING || previous == null) {
      now
    } else {
      previous.startedAt
    }
  val carryForward =
    previous != null &&
      previous.attemptCount == request.attemptCount &&
      previous.resolvedAgentId == request.resolvedAgentId
  val launched =
    when {
      request.launchOutcomeKnown -> request.launchedModel to request.launchedEffort
      carryForward -> previous.launchedModel to previous.launchedEffort
      else -> null to null
    }
  return FeatureTaskRuntimePhaseRecord(
    phaseId = request.phaseId,
    status =
      requireNotNull(request.status.workflowStepStatus()) {
        "Unknown feature-task-runtime phase status '${request.status}'."
      },
    attemptCount = request.attemptCount,
    startedAt = startedAt,
    firstStartedAt = firstStartedAt,
    finishedAt = if (request.finished) now else null,
    durationMillis = if (request.finished) durationMillis(startedAt, now) else null,
    resolvedAgentId = request.resolvedAgentId,
    outputArtifact = request.outputArtifact ?: previous?.outputArtifact,
    rejectedOutput = request.rejectedOutput,
    blockedReason = request.blockedReason,
    failureDisposition = request.failureDisposition,
    fileManifestBefore = request.fileManifestBefore,
    fileManifestAfter = request.fileManifestAfter,
    fileManifestIntroduced = request.fileManifestIntroduced,
    loopId = request.loopId,
    edgeIteration = request.edgeIteration,
    reviewPassNumber = request.reviewPassNumber,
    repairEvidence = request.repairEvidence,
    launchedModel = launched.first,
    launchedEffort = launched.second,
    reviewRunId = request.reviewRunId ?: previous?.reviewRunId,
  )
}

fun FeatureTaskRuntimePhaseStateRecorder.implementationAttemptsFrom(
  artifacts: Map<String, Any?>,
): List<FeatureTaskRuntimeImplementationAttempt> {
  val raw =
    artifacts[FEATURE_TASK_RUNTIME_IMPLEMENTATION_ATTEMPTS_ARTIFACT_KEY]
      ?: return emptyList()
  return decodeImplementationAttemptsFromArtifact(raw)
}

fun FeatureTaskRuntimePhaseStateRecorder.implementationAttemptPatch(
  artifacts: Map<String, Any?>,
  request: FeatureTaskRuntimePhaseStateRequest,
  attemptStatus: FeatureTaskRuntimeImplementationAttemptStatus,
): Map<String, Any?> {
  if (!FeatureTaskRuntimePhaseWorkflowDefinition.isMutatingPhase(request.phaseId)) return emptyMap()
  val produced =
    request.normalizedOutput?.envelopeWireMap()
      ?.let { JsonCodec.anyToStringAnyMap(it[SharedPayloadKeys.PRODUCED_OUTPUTS]) }
  val value = produced?.get(SharedPayloadKeys.VALUE)?.toString()?.trim().orEmpty()
  if (produced == null || value.isBlank()) return emptyMap()
  val prompt = produced[SharedPayloadKeys.PROMPT]?.toString()?.trim()?.takeIf(String::isNotBlank)
  val existing = implementationAttemptsFrom(artifacts)
  val appended =
    featureTaskRuntimeAppendImplementationAttempt(
      existing = existing,
      entry =
        FeatureTaskRuntimeImplementationAttempt(
          sequenceNumber = (existing.maxOfOrNull { it.sequenceNumber } ?: -1) + 1,
          phaseId = request.phaseId,
          attemptNumber = request.attemptCount,
          agentId = request.resolvedAgentId,
          status = attemptStatus,
          recordedAt = clock.instant().toString(),
          value = value,
          loopId = request.loopId,
          edgeIteration = request.edgeIteration,
          failureDisposition = request.failureDisposition,
          prompt = prompt,
        ),
    )
  val wire = implementationAttemptRecordWorkflowArtifact(appended)
  implementationAttemptValidator.validateImplementationAttemptRecord(
    wire,
    FEATURE_TASK_RUNTIME_IMPLEMENTATION_ATTEMPTS_ARTIFACT_KEY,
  )
  return mapOf(FEATURE_TASK_RUNTIME_IMPLEMENTATION_ATTEMPTS_ARTIFACT_KEY to wire)
}

fun FeatureTaskRuntimePhaseStateRecorder.findingVerificationCheckpointPatch(
  request: FeatureTaskRuntimePhaseStateRequest,
): Map<String, Any?> {
  if (request.phaseId != FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_VERIFY_FINDINGS) return emptyMap()
  if (request.finished && request.status.workflowStepStatus() == WorkflowStepStatus.COMPLETED) {
    val dispositions =
      request.normalizedOutput?.envelopeWireMap()
        ?.let(FeatureTaskRuntimeOutputVerification::dispositionsFrom)
        .orEmpty()
    return buildMap {
      put(FEATURE_TASK_RUNTIME_FINDING_VERIFICATION_CHECKPOINT_ARTIFACT_KEY, null)
      put(FEATURE_TASK_RUNTIME_FINDING_VERIFICATION_BOUNDARY_SELECTION_ARTIFACT_KEY, null)
      if (dispositions.isNotEmpty()) {
        put(
          FEATURE_TASK_RUNTIME_FINDING_VERIFICATION_DISPOSITIONS_ARTIFACT_KEY,
          dispositions.map { it.asWorkflowArtifactEntry() },
        )
      }
    }
  }
  val checkpoint = request.findingVerificationCheckpoint?.takeIf { it.isNotEmpty() } ?: return emptyMap()
  return mapOf(
    FEATURE_TASK_RUNTIME_FINDING_VERIFICATION_CHECKPOINT_ARTIFACT_KEY to
      checkpoint.map { it.asWorkflowArtifactEntry() },
  )
}

fun FeatureTaskRuntimePhaseStateRecorder.recordCompletedPhaseWrite(
  request: FeatureTaskRuntimePhaseStateRequest,
): Boolean =
  runtimeOwnedPersistence.requiredWrite(
    seam = "FeatureTaskRuntimePhaseRecorder.recordCompletedPhase",
    expected = "runtime-owned completed phase state",
  ) { unitOfWork ->
    val record =
      WorkflowFamily.TASK_RUNTIME.get(unitOfWork.workflowStates, request.workflowId)
        ?: return@requiredWrite false
    val artifacts = FeatureTaskRuntimeWorkflowPersistence.artifactsFrom(record)
    val existingRecords = decodePhaseRecords(artifacts)
    val updatedRecords =
      LinkedHashMap(existingRecords).apply {
        put(request.phaseId, phaseRecordFor(request, existingRecords[request.phaseId], clock.instant().toString()))
      }
    val ledger = decodePhaseLedger(artifacts)
    val completion =
      FeatureTaskRuntimePhaseLedgerEntry(
        action = COMPLETE,
        sequenceNumber = (ledger.maxOfOrNull { it.sequenceNumber } ?: -1) + 1,
        timestamp = clock.instant().toString(),
        phaseId = request.phaseId,
        attemptCount = request.attemptCount,
        resolvedAgentId = request.resolvedAgentId,
        loopId = request.loopId,
        edgeIteration = request.edgeIteration,
      )
    val updatedLedger =
      appendBoundedHistoryBySequence(
        workflowArtifactEntryMaps(ledger.map { it.asWorkflowArtifactEntry() }),
        workflowArtifactEntryMap(completion.asWorkflowArtifactEntry()),
        FEATURE_TASK_RUNTIME_PHASE_LEDGER_LIMIT,
      )
    workflowPersistence.persistArtifactsPatch(
      unitOfWork.workflowStates,
      record,
      mapOf(
        FEATURE_TASK_RUNTIME_PHASE_RECORDS_ARTIFACT_KEY to
          updatedRecords.mapValues { (_, value) -> value.asWorkflowArtifactEntry() },
        FEATURE_TASK_RUNTIME_PHASE_LEDGER_ARTIFACT_KEY to updatedLedger,
      ) + implementationAttemptPatch(artifacts, request, FeatureTaskRuntimeImplementationAttemptStatus.COMPLETED) +
        findingVerificationCheckpointPatch(request),
      WorkflowRowAdvance(request.phaseId, workflowStatusFor(request), stepUpdatesFrom(updatedRecords)),
    )
    true
  }

fun FeatureTaskRuntimePhaseStateRecorder.phaseRecordFor(
  request: FeatureTaskRuntimePhaseStateRequest,
  previous: FeatureTaskRuntimePhaseRecord?,
  now: String,
): FeatureTaskRuntimePhaseRecord = featureTaskRuntimePhaseRecordFor(request, previous, now)
