package skillbill.engine.featuretask.phase.record

import skillbill.contracts.JsonCodec
import skillbill.engine.featuretask.model.phase.AppendCheckpointIdentityArgs
import skillbill.engine.featuretask.model.phase.FeatureTaskRuntimePhaseLedgerRequest
import skillbill.engine.featuretask.persist.FeatureTaskRuntimeWorkflowPersistence
import skillbill.engine.featuretask.persist.sha256Hex
import skillbill.engine.featuretask.persist.workflowArtifactEntryMap
import skillbill.engine.featuretask.persist.workflowArtifactEntryMaps
import skillbill.engine.featuretask.phase.core.decodePhaseLedger
import skillbill.engine.featuretask.phase.core.resolvedBranchFromWorkflowArtifacts
import skillbill.engine.featuretask.runloop.observability.fixLoopIteration
import skillbill.error.shellcontent.InvalidFeatureTaskRuntimeCheckpointIdentityVersionError
import skillbill.error.shellcontent.InvalidWorkflowStateSchemaError
import skillbill.ports.db.DatabaseSessionFactory
import skillbill.ports.workflow.get
import skillbill.ports.workflow.model.WorkflowFamily
import skillbill.workflow.goal.model.appendBoundedHistoryBySequence
import skillbill.workflow.taskruntime.artifact.asCheckpointIdentitiesArtifactEntry
import skillbill.workflow.taskruntime.artifact.asQuarantineWorkflowArtifactEntry
import skillbill.workflow.taskruntime.artifact.asWorkflowArtifactEntry
import skillbill.workflow.taskruntime.artifact.decodeCheckpointIdentitiesFromArtifact
import skillbill.workflow.taskruntime.artifact.decodeQuarantineEntriesFromArtifact
import skillbill.workflow.taskruntime.artifact.resolvedBranchFromWorkflowArtifacts
import skillbill.workflow.taskruntime.artifact.validateQuarantineRecord
import skillbill.workflow.taskruntime.model.audit.FEATURE_TASK_RUNTIME_QUARANTINED_RECORDS_ARTIFACT_KEY
import skillbill.workflow.taskruntime.model.audit.FeatureTaskRuntimeQuarantineEntry
import skillbill.workflow.taskruntime.model.audit.QUARANTINE_REJECTION_CLASS_CHECKPOINT_IDENTITY_VERSION
import skillbill.workflow.taskruntime.model.core.FeatureTaskRuntimeResolvedBranch
import skillbill.workflow.taskruntime.model.core.FeatureTaskRuntimeWireArtifactValidator
import skillbill.workflow.taskruntime.model.persistence.task.runtime.checkpoint.FEATURE_TASK_RUNTIME_CHECKPOINT_IDENTITIES_ARTIFACT_KEY
import skillbill.workflow.taskruntime.model.persistence.task.runtime.checkpoint.FeatureTaskRuntimeCheckpointIdentity
import skillbill.workflow.taskruntime.model.persistence.task.runtime.checkpoint.featureTaskRuntimeAppendCheckpointIdentity
import skillbill.workflow.taskruntime.model.persistence.task.runtime.checkpoint.featureTaskRuntimeCheckpointRefName
import skillbill.workflow.taskruntime.model.persistence.task.runtime.checkpoint.featureTaskRuntimeOwnedPathDigest
import skillbill.workflow.taskruntime.model.persistence.task.runtime.persistence.FEATURE_TASK_RUNTIME_PHASE_LEDGER_ARTIFACT_KEY
import skillbill.workflow.taskruntime.model.persistence.task.runtime.persistence.FEATURE_TASK_RUNTIME_PHASE_LEDGER_LIMIT
import skillbill.workflow.taskruntime.model.persistence.task.runtime.persistence.FEATURE_TASK_RUNTIME_RESOLVED_BRANCH_ARTIFACT_KEY
import skillbill.workflow.taskruntime.model.phase.FeatureTaskRuntimePhaseLedgerEntry
import java.time.Clock

class FeatureTaskRuntimePhaseEvidenceRecorder(
  val database: DatabaseSessionFactory,
  val workflowPersistence: FeatureTaskRuntimeWorkflowPersistence,
  val quarantineValidator: FeatureTaskRuntimeWireArtifactValidator,
  val clock: Clock,
) {
  fun appendLedgerEntry(request: FeatureTaskRuntimePhaseLedgerRequest): Boolean =
    database.transaction { unitOfWork ->
      val record =
        WorkflowFamily.TASK_RUNTIME.get(unitOfWork.workflowStates, request.workflowId)
          ?: return@transaction false
      val artifacts = FeatureTaskRuntimeWorkflowPersistence.artifactsFrom(record)
      val existingEntries = decodePhaseLedger(artifacts)
      val nextSequence = (existingEntries.maxOfOrNull { it.sequenceNumber } ?: -1) + 1
      val entry =
        FeatureTaskRuntimePhaseLedgerEntry(
          action = request.action,
          sequenceNumber = nextSequence,
          timestamp = clock.instant().toString(),
          phaseId = request.phaseId,
          attemptCount = request.attemptCount,
          resolvedAgentId = request.resolvedAgentId,
          fixLoopIteration = request.fixLoopIteration,
          blockedReason = request.blockedReason,
          loopId = request.loopId,
          edgeIteration = request.edgeIteration,
        )
      val updatedLedger =
        appendBoundedHistoryBySequence(
          existing = workflowArtifactEntryMaps(existingEntries.map { it.asWorkflowArtifactEntry() }),
          entry = workflowArtifactEntryMap(entry.asWorkflowArtifactEntry()),
          retentionLimit = FEATURE_TASK_RUNTIME_PHASE_LEDGER_LIMIT,
        )
      workflowPersistence.persistArtifactsPatch(
        unitOfWork.workflowStates,
        record,
        mapOf(FEATURE_TASK_RUNTIME_PHASE_LEDGER_ARTIFACT_KEY to updatedLedger),
      )
      true
    }

  fun appendQuarantineEntry(
    workflowId: String,
    entry: FeatureTaskRuntimeQuarantineEntry,
  ): Boolean =
    database.transaction { unitOfWork ->
      val record =
        WorkflowFamily.TASK_RUNTIME.get(unitOfWork.workflowStates, workflowId)
          ?: return@transaction false
      val artifacts = FeatureTaskRuntimeWorkflowPersistence.artifactsFrom(record)
      val existing = quarantineEntriesFrom(artifacts)
      val alreadyRecorded =
        existing.any {
          it.producingPhaseId == entry.producingPhaseId &&
            it.producingIteration == entry.producingIteration &&
            it.regenerationAttempt == entry.regenerationAttempt
        }
      if (alreadyRecorded) {
        return@transaction true
      }
      val wire = (existing + entry).asQuarantineWorkflowArtifactEntry()
      quarantineValidator.validateQuarantineRecord(wire, FEATURE_TASK_RUNTIME_QUARANTINED_RECORDS_ARTIFACT_KEY)
      workflowPersistence.persistArtifactsPatch(
        unitOfWork.workflowStates,
        record,
        mapOf(FEATURE_TASK_RUNTIME_QUARANTINED_RECORDS_ARTIFACT_KEY to wire),
      )
      true
    }

  fun loadQuarantinedRecords(workflowId: String): List<FeatureTaskRuntimeQuarantineEntry>? =
    database.read { unitOfWork ->
      val record =
        WorkflowFamily.TASK_RUNTIME.get(unitOfWork.workflowStates, workflowId)
          ?: return@read null
      quarantineEntriesFrom(FeatureTaskRuntimeWorkflowPersistence.artifactsFrom(record))
    }

  fun recordResolvedBranch(
    workflowId: String,
    resolvedBranch: FeatureTaskRuntimeResolvedBranch,
  ): Boolean =
    database.transaction { unitOfWork ->
      val record =
        WorkflowFamily.TASK_RUNTIME.get(unitOfWork.workflowStates, workflowId)
          ?: return@transaction false
      val artifacts = FeatureTaskRuntimeWorkflowPersistence.artifactsFrom(record)
      if (resolvedBranchFromWorkflowArtifacts(artifacts) != null) {
        return@transaction true
      }
      workflowPersistence.persistArtifactsPatch(
        unitOfWork.workflowStates,
        record,
        mapOf(FEATURE_TASK_RUNTIME_RESOLVED_BRANCH_ARTIFACT_KEY to resolvedBranch.asWorkflowArtifactEntry()),
      )
      true
    }

  fun loadResolvedBranch(workflowId: String): FeatureTaskRuntimeResolvedBranch? =
    database.read { unitOfWork ->
      val record =
        WorkflowFamily.TASK_RUNTIME.get(unitOfWork.workflowStates, workflowId)
          ?: return@read null
      resolvedBranchFromWorkflowArtifacts(FeatureTaskRuntimeWorkflowPersistence.artifactsFrom(record))
    }

  fun appendCheckpointIdentity(args: AppendCheckpointIdentityArgs): Boolean {
    quarantineCheckpointIdentitiesOnVersionDrift(args.workflowId, args.phaseId, args.generation)
    return appendCheckpointIdentityAtCurrentVersion(args)
  }

  fun loadCheckpointIdentities(workflowId: String): List<FeatureTaskRuntimeCheckpointIdentity>? =
    database.read { unitOfWork ->
      val record =
        WorkflowFamily.TASK_RUNTIME.get(unitOfWork.workflowStates, workflowId)
          ?: return@read null
      checkpointIdentitiesFrom(FeatureTaskRuntimeWorkflowPersistence.artifactsFrom(record))
    }

  fun quarantineCheckpointIdentities(workflowId: String): Boolean =
    database.transaction { unitOfWork ->
      val record =
        WorkflowFamily.TASK_RUNTIME.get(unitOfWork.workflowStates, workflowId)
          ?: return@transaction false
      workflowPersistence.persistArtifactsPatch(
        unitOfWork.workflowStates,
        record,
        mapOf(
          FEATURE_TASK_RUNTIME_CHECKPOINT_IDENTITIES_ARTIFACT_KEY to
            emptyList<FeatureTaskRuntimeCheckpointIdentity>().asCheckpointIdentitiesArtifactEntry(),
        ),
      )
      true
    }

  fun recordWorkflowOwnedPaths(
    workflowId: String,
    ownedPaths: List<String>,
  ): Boolean =
    database.transaction { unitOfWork ->
      val record =
        WorkflowFamily.TASK_RUNTIME.get(unitOfWork.workflowStates, workflowId)
          ?: return@transaction false
      val resolved =
        resolvedBranchFromWorkflowArtifacts(FeatureTaskRuntimeWorkflowPersistence.artifactsFrom(record))
          ?: return@transaction false
      val updated = resolved.copy(workflowOwnedPaths = ownedPaths.distinct().sorted())
      workflowPersistence.persistArtifactsPatch(
        unitOfWork.workflowStates,
        record,
        mapOf(FEATURE_TASK_RUNTIME_RESOLVED_BRANCH_ARTIFACT_KEY to updated.asWorkflowArtifactEntry()),
      )
      true
    }
}

fun FeatureTaskRuntimePhaseEvidenceRecorder.quarantineEntriesFrom(
  artifacts: Map<String, Any?>,
): List<FeatureTaskRuntimeQuarantineEntry> {
  val raw = artifacts[FEATURE_TASK_RUNTIME_QUARANTINED_RECORDS_ARTIFACT_KEY] ?: return emptyList()
  val map =
    JsonCodec.anyToStringAnyMap(raw)
      ?: throw InvalidWorkflowStateSchemaError("Feature-task-runtime quarantine record must be an object.")
  quarantineValidator.validateQuarantineRecord(map, FEATURE_TASK_RUNTIME_QUARANTINED_RECORDS_ARTIFACT_KEY)
  return decodeQuarantineEntriesFromArtifact(raw)
}

fun FeatureTaskRuntimePhaseEvidenceRecorder.checkpointIdentitiesFrom(
  artifacts: Map<String, Any?>,
): List<FeatureTaskRuntimeCheckpointIdentity> =
  decodeCheckpointIdentitiesFromArtifact(
    artifacts[FEATURE_TASK_RUNTIME_CHECKPOINT_IDENTITIES_ARTIFACT_KEY],
  )

fun FeatureTaskRuntimePhaseEvidenceRecorder.appendCheckpointIdentityAtCurrentVersion(
  args: AppendCheckpointIdentityArgs,
): Boolean =
  database.transaction { unitOfWork ->
    val record =
      WorkflowFamily.TASK_RUNTIME.get(unitOfWork.workflowStates, args.workflowId)
        ?: return@transaction false
    val artifacts = FeatureTaskRuntimeWorkflowPersistence.artifactsFrom(record)
    val existing = checkpointIdentitiesFrom(artifacts)
    val sequenceNumber = (existing.maxOfOrNull { it.sequenceNumber } ?: -1) + 1
    val entry =
      FeatureTaskRuntimeCheckpointIdentity(
        sequenceNumber = sequenceNumber,
        issueKey = args.issueKey,
        subtaskId = args.subtaskId,
        checkpointRef = featureTaskRuntimeCheckpointRefName(args.issueKey, args.subtaskId, sequenceNumber),
        branch = args.branch,
        phaseId = args.phaseId,
        generation = args.generation,
        ownedPathDigest = featureTaskRuntimeOwnedPathDigest(args.ownedPaths),
        ownedPathCount = args.ownedPaths.filter(String::isNotBlank).distinct().size,
        commitSha = args.commitSha,
        recordedAt = clock.instant().toString(),
        loopId = args.loopId,
        parentSha = args.parentSha,
      )
    val updated = featureTaskRuntimeAppendCheckpointIdentity(existing, entry)
    workflowPersistence.persistArtifactsPatch(
      unitOfWork.workflowStates,
      record,
      mapOf(
        FEATURE_TASK_RUNTIME_CHECKPOINT_IDENTITIES_ARTIFACT_KEY to updated.asCheckpointIdentitiesArtifactEntry(),
      ),
    )
    true
  }

fun FeatureTaskRuntimePhaseEvidenceRecorder.quarantineCheckpointIdentitiesOnVersionDrift(
  workflowId: String,
  phaseId: String,
  generation: Int,
) {
  val rejected =
    database.read { unitOfWork ->
      val record =
        WorkflowFamily.TASK_RUNTIME.get(unitOfWork.workflowStates, workflowId)
          ?: return@read null
      val artifacts = FeatureTaskRuntimeWorkflowPersistence.artifactsFrom(record)
      try {
        checkpointIdentitiesFrom(artifacts)
        null
      } catch (error: InvalidFeatureTaskRuntimeCheckpointIdentityVersionError) {
        error to artifacts[FEATURE_TASK_RUNTIME_CHECKPOINT_IDENTITIES_ARTIFACT_KEY].toString()
      }
    } ?: return
  val (error, rejectedPayload) = rejected
  val iteration = (generation + 1).coerceAtLeast(1)
  appendQuarantineEntry(
    workflowId,
    FeatureTaskRuntimeQuarantineEntry(
      producingPhaseId = phaseId,
      consumingPhaseId = phaseId,
      producingIteration = iteration,
      rejectionClass = QUARANTINE_REJECTION_CLASS_CHECKPOINT_IDENTITY_VERSION,
      rejectionDetail =
        "seam=FeatureTaskRuntimePhaseRecorder.appendCheckpointIdentity " +
          "expected=${error.expectedContractVersion} actual=${error.actualContractVersion} " +
          "cause=checkpoint-identity store predates the current contract; reset and regenerated forward",
      regenerationAttempt = 1,
      quarantinedAtIteration = iteration,
      diagnosticIdentity = null,
      rejectedRecordByteSize = rejectedPayload.toByteArray().size.toLong(),
      rejectedRecordSha256 = sha256Hex(rejectedPayload),
      diagnosticDegraded = true,
    ),
  )
  quarantineCheckpointIdentities(workflowId)
}
