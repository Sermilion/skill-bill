package skillbill.engine.goalplanning

import me.tatarka.inject.annotations.Inject
import skillbill.contracts.JsonCodec
import skillbill.contracts.SharedPayloadKeys
import skillbill.engine.goalrunner.planning.model.expectedProvenance
import skillbill.engine.planningprojection.producerProjectionGateReason
import skillbill.engine.planningprojection.requireValidPlanningProjection
import skillbill.error.shellcontent.IncompatibleGoalPlanningPreparationRecoveryError
import skillbill.error.shellcontent.InvalidFeatureTaskRuntimePhaseOutputSchemaError
import skillbill.error.shellcontent.InvalidGoalPlanningPreparationSchemaError
import skillbill.ports.db.DatabaseSessionFactory
import skillbill.ports.goalrunner.model.GoalPlanningContractProvenance
import skillbill.ports.goalrunner.model.GoalPlanningIdentity
import skillbill.ports.goalrunner.model.GoalPlanningPreparationProgress
import skillbill.ports.goalrunner.model.GoalPlanningPreparationRecord
import skillbill.ports.goalrunner.model.GoalSubtaskPlanCheckpoint
import skillbill.ports.goalrunner.model.GovernedGoalSubtaskDescriptor
import skillbill.ports.goalrunner.model.SharedGoalPreplanCheckpoint
import skillbill.ports.taskruntime.FeatureTaskRuntimePhaseOutputValidator
import skillbill.ports.taskruntime.FeatureTaskRuntimeWireArtifactValidator
import skillbill.ports.taskruntime.validateGoalPlanningPreparationEnvelope
import skillbill.text.sha256HexUtf8
import skillbill.workflow.model.WorkflowStepStatus
import skillbill.workflow.model.workflowStepStatus
import skillbill.workflow.taskruntime.artifact.asWorkflowArtifactEntry
import skillbill.workflow.taskruntime.artifact.envelopeWireMap
import skillbill.workflow.taskruntime.model.phase.FeatureTaskRuntimePhaseOutputRepairEvidence
import skillbill.workflow.taskruntime.model.phase.requireAcceptedOutput

@Inject
class GoalPlanningPreparationCheckpoint(
  private val database: DatabaseSessionFactory,
  private val envelopeValidator: FeatureTaskRuntimeWireArtifactValidator,
  private val phaseOutputValidator: FeatureTaskRuntimePhaseOutputValidator,
  planningProjectionValidator: FeatureTaskRuntimeWireArtifactValidator,
) {
  private val gate =
    GoalPlanningPreparationProjectionGate(envelopeValidator, phaseOutputValidator, planningProjectionValidator)
  private val preparationValidator =
    GoalPlanningPreparationValidator(phaseOutputValidator, planningProjectionValidator)

  fun checkpoint(record: GoalPlanningPreparationRecord) {
    val canonical = preparationValidator.canonicalize(record)
    envelopeValidator.validateGoalPlanningPreparationEnvelope(
      canonical.toEnvelopeMap(),
      "${canonical.parentGoalWorkflowId}#${canonical.subtaskId}",
    )
    database.selfManagedWrite { unitOfWork ->
      unitOfWork.goalPlanningPreparations.markPrepared(canonical)
    }
  }

  fun validate(record: GoalPlanningPreparationRecord) {
    val sourceLabel = "${record.parentGoalWorkflowId}#${record.subtaskId}"
    envelopeValidator.validateGoalPlanningPreparationEnvelope(record.toEnvelopeMap(), sourceLabel)
    preparationValidator.validate(record)
  }

  fun checkpointSharedPreplan(checkpoint: SharedGoalPreplanCheckpoint) {
    val canonical = gate.canonicalizeSharedPreplan(checkpoint)
    gate.validateSharedPreplan(canonical)
    database.selfManagedWrite { it.goalPlanningPreparations.checkpointSharedPreplan(canonical) }
  }

  fun checkpointSubtaskPlan(checkpoint: GoalSubtaskPlanCheckpoint) {
    val canonical = gate.canonicalizeSubtaskPlan(checkpoint)
    gate.validateSubtaskPlan(canonical)
    database.selfManagedWrite { it.goalPlanningPreparations.checkpointSubtaskPlan(canonical) }
  }

  fun recheckpointSharedPreplan(
    checkpoint: SharedGoalPreplanCheckpoint,
    cascadePlanSubtaskIds: List<Int> = emptyList(),
  ) {
    val canonical = gate.canonicalizeSharedPreplan(checkpoint)
    gate.validateSharedPreplan(canonical)
    val stored = database.read { it.goalPlanningPreparations.findSharedPreplan(canonical.identity) }
    if (stored != null && gate.sharedPreplanIsRegenerable(stored)) {
      database.selfManagedWrite {
        it.goalPlanningPreparations.replaceSharedPreplan(canonical, stored.payloadSha256, cascadePlanSubtaskIds)
      }
    } else {
      database.selfManagedWrite { it.goalPlanningPreparations.checkpointSharedPreplan(canonical) }
    }
  }

  val sharedPreplanRefresh: GoalPlanningSharedPreplanRefresh =
    GoalPlanningSharedPreplanRefresh(database, gate)

  fun recheckpointSubtaskPlan(checkpoint: GoalSubtaskPlanCheckpoint) {
    val canonical = gate.canonicalizeSubtaskPlan(checkpoint)
    gate.validateSubtaskPlan(canonical)
    val stored =
      findStoredSubtaskPlan(
        canonical.identity,
        canonical.subtaskId,
        canonical.governedSubSpecPath,
      )
    if (stored != null && gate.subtaskPlanIsRegenerable(stored)) {
      database.selfManagedWrite { it.goalPlanningPreparations.replaceSubtaskPlan(canonical) }
    } else {
      database.selfManagedWrite { it.goalPlanningPreparations.checkpointSubtaskPlan(canonical) }
    }
  }

  fun findStoredSubtaskPlan(
    identity: GoalPlanningIdentity,
    subtaskId: Int,
    governedSubSpecPath: String,
  ): GoalSubtaskPlanCheckpoint? =
    database.read {
      it.goalPlanningPreparations.findSubtaskPlan(identity, subtaskId, governedSubSpecPath)
    }

  fun findSharedPreplan(identity: GoalPlanningIdentity): SharedGoalPreplanCheckpoint? =
    database.read { it.goalPlanningPreparations.findSharedPreplan(identity) }
      ?.takeIf { gate.sharedPreplanRejection(it) == null }

  fun findSubtaskPlan(
    identity: GoalPlanningIdentity,
    subtaskId: Int,
    governedSubSpecPath: String,
    expectedDescriptor: GovernedGoalSubtaskDescriptor? = null,
  ): GoalSubtaskPlanCheckpoint? =
    database.read {
      it.goalPlanningPreparations.findSubtaskPlan(identity, subtaskId, governedSubSpecPath)
    }?.let { plan ->
      val projectionRejection = gate.subtaskPlanRejection(plan)
      if (
        expectedDescriptor != null &&
        plan.manifestOrder != expectedDescriptor.manifestOrder
      ) {
        throw IncompatibleGoalPlanningPreparationRecoveryError(
          identity.parentGoalWorkflowId,
          subtaskId,
          "stored manifest order differs from the authoritative decomposition manifest",
        )
      }
      if (
        expectedDescriptor != null &&
        plan.subSpecHash != expectedDescriptor.subSpecHash
      ) {
        throw IncompatibleGoalPlanningPreparationRecoveryError(
          identity.parentGoalWorkflowId,
          subtaskId,
          "stored governed sub-spec hash differs from the current governed sub-spec",
        )
      }
      plan.takeIf { projectionRejection == null }
    }

  fun recoveryProgress(
    identity: GoalPlanningIdentity,
    orderedDescriptors: List<GovernedGoalSubtaskDescriptor>,
    expectedProvenance: GoalPlanningContractProvenance,
  ): GoalPlanningPreparationProgress {
    val sharedPrepared = findSharedPreplan(identity) != null
    val prepared =
      orderedDescriptors.mapNotNull { descriptor ->
        findSubtaskPlan(
          identity,
          descriptor.subtaskId,
          descriptor.governedSubSpecPath,
          descriptor,
        )?.also { plan ->
          if (plan.provenance != expectedProvenance) {
            throw IncompatibleGoalPlanningPreparationRecoveryError(
              identity.parentGoalWorkflowId,
              descriptor.subtaskId,
              "stored plan provenance differs from the governing shared preplan",
            )
          }
          val parsed =
            JsonCodec.parseObjectOrNull(plan.planPayload)
              ?.let(JsonCodec::jsonElementToValue)
              ?.let(JsonCodec::anyToStringAnyMap)
          val status = parsed?.get(SharedPayloadKeys.STATUS)?.toString()
          val produced = parsed?.get(SharedPayloadKeys.PRODUCED_OUTPUTS) as? Map<*, *>
          if (status.workflowStepStatus() != WorkflowStepStatus.COMPLETED || produced?.isEmpty() != false) {
            throw IncompatibleGoalPlanningPreparationRecoveryError(
              identity.parentGoalWorkflowId,
              descriptor.subtaskId,
              "stored plan payload has status '$status' but must be completed with non-empty produced_outputs",
            )
          }
        }
      }
    val preparedIds = prepared.mapTo(mutableSetOf()) { it.subtaskId }
    return GoalPlanningPreparationProgress(
      sharedPreplanPrepared = sharedPrepared,
      preparedPlanCount = prepared.size,
      expectedPlanCount = orderedDescriptors.size,
      missingSubtaskIds = orderedDescriptors.filterNot { it.subtaskId in preparedIds }.map { it.subtaskId },
    )
  }
}

class GoalPlanningSharedPreplanRefresh(
  private val database: DatabaseSessionFactory,
  private val gate: GoalPlanningPreparationProjectionGate,
) {
  fun listPreparedPlanSubtaskIds(parentGoalWorkflowId: String): List<Int> =
    database.read {
      it.goalPlanningPreparations.listPreparedPlanSubtaskIds(parentGoalWorkflowId)
    }

  fun advanceSharedPreplanProvenance(
    identity: GoalPlanningIdentity,
    expectedPayloadSha256: String,
    provenance: GoalPlanningContractProvenance,
  ) {
    database.selfManagedWrite {
      it.goalPlanningPreparations.advanceSharedPreplanProvenance(identity, expectedPayloadSha256, provenance)
    }
  }

  fun replaceSharedPreplanForRefresh(
    checkpoint: SharedGoalPreplanCheckpoint,
    expectedPayloadSha256: String,
    cascadePlanSubtaskIds: List<Int>,
  ): SharedGoalPreplanCheckpoint {
    val canonical = gate.canonicalizeSharedPreplan(checkpoint)
    gate.validateSharedPreplan(canonical)
    database.selfManagedWrite {
      it.goalPlanningPreparations.replaceSharedPreplan(canonical, expectedPayloadSha256, cascadePlanSubtaskIds)
    }
    return canonical
  }
}

class GoalPlanningPreparationProjectionGate(
  private val envelopeValidator: FeatureTaskRuntimeWireArtifactValidator,
  private val phaseOutputValidator: FeatureTaskRuntimePhaseOutputValidator,
  private val planningProjectionValidator: FeatureTaskRuntimeWireArtifactValidator,
) {
  fun canonicalizeSharedPreplan(checkpoint: SharedGoalPreplanCheckpoint): SharedGoalPreplanCheckpoint {
    val accepted =
      phaseOutputValidator.validatePhaseOutput(checkpoint.preplanPayload, "preplan")
        .requireAcceptedOutput("preplan")
    val canonical = accepted.normalizedOutput.canonicalJson
    return checkpoint.copy(
      preplanPayload = canonical,
      payloadSha256 = sha256HexUtf8(canonical),
      repairEvidence =
        planningRepairEvidenceFor(
          phaseId = "preplan",
          sourcePayload = checkpoint.preplanPayload,
          acceptedEvidence = accepted.repairEvidence,
          storedEvidence = checkpoint.repairEvidence,
          sourceLabel = checkpoint.identity.parentGoalWorkflowId,
        ),
    )
  }

  fun canonicalizeSubtaskPlan(checkpoint: GoalSubtaskPlanCheckpoint): GoalSubtaskPlanCheckpoint {
    val accepted =
      phaseOutputValidator.validatePhaseOutput(checkpoint.planPayload, "plan")
        .requireAcceptedOutput("plan")
    val canonical = accepted.normalizedOutput.canonicalJson
    return checkpoint.copy(
      planPayload = canonical,
      payloadSha256 = sha256HexUtf8(canonical),
      repairEvidence =
        planningRepairEvidenceFor(
          phaseId = "plan",
          sourcePayload = checkpoint.planPayload,
          acceptedEvidence = accepted.repairEvidence,
          storedEvidence = checkpoint.repairEvidence,
          sourceLabel = "${checkpoint.identity.parentGoalWorkflowId}#${checkpoint.subtaskId}",
        ),
    )
  }

  fun validateSharedPreplan(checkpoint: SharedGoalPreplanCheckpoint) {
    val (label, envelope) = sharedPreplanEnvelope(checkpoint)
    envelope.requirePrepared(label)
  }

  fun validateSubtaskPlan(checkpoint: GoalSubtaskPlanCheckpoint) {
    val (label, envelope) = subtaskPlanEnvelope(checkpoint)
    envelope.requirePrepared(label)
    requireValidPlanningProjection(envelope, "plan", label, planningProjectionValidator)
  }

  fun sharedPreplanRejection(checkpoint: SharedGoalPreplanCheckpoint): String? =
    planningRecordRejection {
      sharedPreplanEnvelope(checkpoint)
      null
    }

  fun subtaskPlanRejection(checkpoint: GoalSubtaskPlanCheckpoint): String? =
    planningRecordRejection {
      val (_, envelope) = subtaskPlanEnvelope(checkpoint)
      producerProjectionGateReason("plan", envelope, planningProjectionValidator)
    }

  private fun sharedPreplanEnvelope(checkpoint: SharedGoalPreplanCheckpoint): Pair<String, Map<String, Any?>> {
    val label = checkpoint.identity.parentGoalWorkflowId
    envelopeValidator.validateGoalPlanningPreparationEnvelope(checkpoint.toEnvelopeMap(), label)
    val normalized =
      phaseOutputValidator.validatePhaseOutput(checkpoint.preplanPayload, "preplan")
        .requireAcceptedOutput("preplan")
        .normalizedOutput
    requirePlanningPayloadHash(checkpoint.payloadSha256, normalized.canonicalJson, label)
    return label to normalized.envelopeWireMap()
  }

  private fun subtaskPlanEnvelope(checkpoint: GoalSubtaskPlanCheckpoint): Pair<String, Map<String, Any?>> {
    val label = "${checkpoint.identity.parentGoalWorkflowId}#${checkpoint.subtaskId}"
    envelopeValidator.validateGoalPlanningPreparationEnvelope(checkpoint.toEnvelopeMap(), label)
    val normalized =
      phaseOutputValidator.validatePhaseOutput(checkpoint.planPayload, "plan")
        .requireAcceptedOutput("plan")
        .normalizedOutput
    requirePlanningPayloadHash(checkpoint.payloadSha256, normalized.canonicalJson, label)
    return label to normalized.envelopeWireMap()
  }

  fun sharedPreplanIsRegenerable(stored: SharedGoalPreplanCheckpoint): Boolean = sharedPreplanRejection(stored) != null

  fun subtaskPlanIsRegenerable(stored: GoalSubtaskPlanCheckpoint): Boolean = subtaskPlanRejection(stored) != null
}

private fun requirePlanningPayloadHash(
  expected: String,
  payload: String,
  label: String,
) {
  if (sha256HexUtf8(payload) != expected) {
    throw InvalidGoalPlanningPreparationSchemaError(
      label,
      "payload_sha256",
      "payload_sha256 does not match the exact UTF-8 payload bytes",
    )
  }
}

private fun planningRepairEvidenceFor(
  phaseId: String,
  sourcePayload: String,
  acceptedEvidence: FeatureTaskRuntimePhaseOutputRepairEvidence?,
  storedEvidence: FeatureTaskRuntimePhaseOutputRepairEvidence?,
  sourceLabel: String,
): FeatureTaskRuntimePhaseOutputRepairEvidence? {
  acceptedEvidence?.let { evidence ->
    if (evidence.originalDigest != sha256HexUtf8(sourcePayload)) {
      throw InvalidGoalPlanningPreparationSchemaError(
        sourceLabel,
        "$phaseId.repair_evidence.original_digest",
        "repair evidence does not describe the checkpoint input bytes",
      )
    }
  }
  return acceptedEvidence ?: storedEvidence
}

private fun planningRecordRejection(compute: () -> String?): String? =
  try {
    compute()
  } catch (error: InvalidGoalPlanningPreparationSchemaError) {
    "stored record failed its durable contract: ${error.message.orEmpty()}"
  } catch (error: InvalidFeatureTaskRuntimePhaseOutputSchemaError) {
    "stored record failed its durable contract: ${error.message.orEmpty()}"
  }

private fun Map<String, Any?>.requirePrepared(label: String) {
  if (get(SharedPayloadKeys.STATUS).workflowStepStatus() != WorkflowStepStatus.COMPLETED ||
    (get(SharedPayloadKeys.PRODUCED_OUTPUTS) as? Map<*, *>)?.isEmpty() != false
  ) {
    throw InvalidGoalPlanningPreparationSchemaError(
      label,
      "payload",
      "phase output must be completed with non-empty produced_outputs",
    )
  }
}

private fun SharedGoalPreplanCheckpoint.toEnvelopeMap(): Map<String, Any?> =
  linkedMapOf(
    SharedPayloadKeys.CONTRACT_VERSION to contractVersion,
    "record_type" to "shared_preplan",
    "identity" to identity.asMap(),
    "preparation_status" to preparationStatus.wireValue,
    "provenance" to provenance.asMap(),
    "payload_sha256" to payloadSha256,
    "preplan_payload" to preplanPayload,
    "repair_evidence" to repairEvidence?.asWorkflowArtifactEntry(),
  ).filterValues { it != null }

private fun GoalSubtaskPlanCheckpoint.toEnvelopeMap(): Map<String, Any?> =
  linkedMapOf(
    SharedPayloadKeys.CONTRACT_VERSION to contractVersion,
    "record_type" to "subtask_plan",
    "identity" to identity.asMap(),
    SharedPayloadKeys.SUBTASK_ID to subtaskId,
    "manifest_order" to manifestOrder,
    "governed_sub_spec_path" to governedSubSpecPath,
    "sub_spec_hash" to subSpecHash, "preparation_status" to preparationStatus.wireValue,
    "provenance" to provenance.asMap(), "payload_sha256" to payloadSha256, "plan_payload" to planPayload,
    "repair_evidence" to repairEvidence?.asWorkflowArtifactEntry(),
  ).filterValues { it != null }

private fun GoalPlanningIdentity.asMap() =
  linkedMapOf(
    "parent_goal_workflow_id" to parentGoalWorkflowId,
    "normalized_issue_key" to normalizedIssueKey,
    "repository_identity" to repositoryIdentity,
  )

private fun GoalPlanningContractProvenance.asMap() =
  linkedMapOf(
    "parent_spec_hash" to parentSpecHash,
    "decomposition_manifest_hash" to decompositionManifestHash,
    "planning_contract_id" to planningContractId,
    "planning_contract_version" to planningContractVersion,
    "phase_output_contract_id" to phaseOutputContractId,
    "phase_output_contract_version" to phaseOutputContractVersion,
  )
