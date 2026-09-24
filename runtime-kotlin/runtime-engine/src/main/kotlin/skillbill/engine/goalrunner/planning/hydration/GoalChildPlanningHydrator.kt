package skillbill.engine.goalrunner.planning.hydration

import skillbill.contracts.SharedPayloadKeys
import skillbill.engine.featuretask.persist.durationMillis
import skillbill.engine.featuretask.persist.workflowArtifactEntryMap
import skillbill.engine.goalrunner.planning.model.GoalChildPlanningHydration
import skillbill.engine.goalrunner.planning.recovery.GoalPlanningRecoveryKind
import skillbill.engine.goalrunner.planning.recovery.classifyGoalPlanningRecovery
import skillbill.engine.planningprojection.requireValidPlanningProjection
import skillbill.error.shellcontent.IncompatibleGoalPlanningPreparationRecoveryError
import skillbill.error.shellcontent.InvalidFeatureTaskRuntimePhaseOutputSchemaError
import skillbill.error.shellcontent.InvalidGoalPlanningPreparationSchemaError
import skillbill.ports.goalrunner.GoalRunnerPersistenceSession
import skillbill.ports.goalrunner.model.GoalSubtaskPlanCheckpoint
import skillbill.ports.goalrunner.model.SharedGoalPreplanCheckpoint
import skillbill.ports.goalrunner.runner.model.GoalChildPlanningHydrationRequest
import skillbill.ports.goalrunner.runner.model.GoalRunnerChildWorkflowSetup
import skillbill.ports.taskruntime.FeatureTaskRuntimePhaseOutputValidator
import skillbill.ports.taskruntime.FeatureTaskRuntimeWireArtifactValidator
import skillbill.text.sha256HexUtf8
import skillbill.workflow.engine.model.DurableWorkflowArtifactFamily
import skillbill.workflow.engine.model.WorkflowStateSnapshot
import skillbill.workflow.model.WorkflowStepStatus
import skillbill.workflow.model.workflowStepStatus
import skillbill.workflow.taskruntime.artifact.asWorkflowArtifactEntry
import skillbill.workflow.taskruntime.artifact.envelopeWireMap
import skillbill.workflow.taskruntime.model.persistence.task.runtime.goal.FeatureTaskRuntimeGoalPlanningImport
import skillbill.workflow.taskruntime.model.phase.AcceptedFeatureTaskRuntimePhaseOutput
import skillbill.workflow.taskruntime.model.phase.FeatureTaskRuntimePhaseExecutionOrigin
import skillbill.workflow.taskruntime.model.phase.FeatureTaskRuntimePhaseLedgerAction
import skillbill.workflow.taskruntime.model.phase.FeatureTaskRuntimePhaseLedgerEntry
import skillbill.workflow.taskruntime.model.phase.FeatureTaskRuntimePhaseOutputRepairEvidence
import skillbill.workflow.taskruntime.model.phase.FeatureTaskRuntimePhaseRecord
import skillbill.workflow.taskruntime.model.phase.requireAcceptedOutput
import java.time.Clock

private data class PreparedGoalPlanning(
  val shared: SharedGoalPreplanCheckpoint,
  val plan: GoalSubtaskPlanCheckpoint,
)

class GoalChildPlanningHydrator(
  phaseOutputValidator: FeatureTaskRuntimePhaseOutputValidator,
  planningProjectionValidator: FeatureTaskRuntimeWireArtifactValidator,
  private val clock: Clock,
) {
  private val payloadValidator = PreparedPlanningPayloadValidator(phaseOutputValidator, planningProjectionValidator)
  private val importMatcher = GoalChildPlanningImportMatcher(payloadValidator)

  fun hydrate(
    unitOfWork: GoalRunnerPersistenceSession,
    setup: GoalRunnerChildWorkflowSetup,
    request: GoalChildPlanningHydrationRequest,
  ): GoalChildPlanningHydration {
    val prepared = loadRequiredPreparation(unitOfWork, setup, request)
    requireMatchingPreparation(setup, request, prepared)
    val preplan =
      payloadValidator.requireValid(
        "preplan",
        prepared.shared.preplanPayload,
        prepared.shared.payloadSha256,
        setup.workflowId,
      )
    val plan =
      payloadValidator.requireValid(
        "plan",
        prepared.plan.planPayload,
        prepared.plan.payloadSha256,
        setup.workflowId,
      )
    return createHydration(request, prepared, preplan, plan)
  }

  fun requireMatchingImport(
    unitOfWork: GoalRunnerPersistenceSession,
    existing: WorkflowStateSnapshot,
    setup: GoalRunnerChildWorkflowSetup,
  ) {
    val request =
      requireNotNull(setup.planningHydration) {
        "Prepared goal child '${setup.subtaskId}' requires planning hydration."
      }
    importMatcher.firstDivergence(unitOfWork, existing, setup, request)?.let { divergence ->
      throw IncompatibleGoalPlanningPreparationRecoveryError(
        request.identity.parentGoalWorkflowId,
        setup.subtaskId,
        "existing child planning import conflicts with request: $divergence",
      )
    }
  }

  private fun loadRequiredPreparation(
    unitOfWork: GoalRunnerPersistenceSession,
    setup: GoalRunnerChildWorkflowSetup,
    request: GoalChildPlanningHydrationRequest,
  ): PreparedGoalPlanning {
    val shared =
      unitOfWork.goalPlanningPreparations.findSharedPreplan(request.identity)
        ?: throw InvalidGoalPlanningPreparationSchemaError(
          setup.workflowId,
          "preplan",
          "shared preplan is missing",
        )
    val plan =
      unitOfWork.goalPlanningPreparations.findSubtaskPlan(
        request.identity,
        request.descriptor.subtaskId,
        request.descriptor.governedSubSpecPath,
      ) ?: throw InvalidGoalPlanningPreparationSchemaError(
        setup.workflowId,
        "plan",
        "subtask plan is missing",
      )
    return PreparedGoalPlanning(shared, plan)
  }

  private fun requireMatchingPreparation(
    setup: GoalRunnerChildWorkflowSetup,
    request: GoalChildPlanningHydrationRequest,
    prepared: PreparedGoalPlanning,
  ) {
    val matches =
      listOf(
        prepared.shared.provenance.copy(parentSpecHash = request.provenance.parentSpecHash) == request.provenance,
        prepared.plan.provenance.copy(parentSpecHash = request.provenance.parentSpecHash) == request.provenance,
        prepared.plan.manifestOrder == request.descriptor.manifestOrder,
      ).all { it }
    if (!matches) {
      throw IncompatibleGoalPlanningPreparationRecoveryError(
        request.identity.parentGoalWorkflowId,
        setup.subtaskId,
        "stored planning provenance or selected subtask descriptor differs from the hydration request",
      )
    }
  }

  private fun createHydration(
    request: GoalChildPlanningHydrationRequest,
    prepared: PreparedGoalPlanning,
    preplan: AcceptedFeatureTaskRuntimePhaseOutput,
    plan: AcceptedFeatureTaskRuntimePhaseOutput,
  ): GoalChildPlanningHydration {
    val importedAt = clock.instant().toString()
    val records =
      createImportedRecords(
        preplan.forPlanningImport(prepared.shared.preplanPayload, prepared.shared.repairEvidence),
        plan.forPlanningImport(prepared.plan.planPayload, prepared.plan.repairEvidence),
        importedAt,
      )
    return GoalChildPlanningHydration(
      currentStepId = "implement",
      stepUpdates = records.keys.map(::completedStep),
      artifacts =
        mapOf(
          DurableWorkflowArtifactFamily.FEATURE_TASK_RUNTIME_PHASE_RECORDS.entry(records),
          DurableWorkflowArtifactFamily.FEATURE_TASK_RUNTIME_PHASE_LEDGER.entry(createImportedLedger(importedAt)),
          DurableWorkflowArtifactFamily.FEATURE_TASK_RUNTIME_GOAL_PLANNING_IMPORT.entry(
            createProvenance(request, prepared),
          ),
        ),
    )
  }

  private fun AcceptedFeatureTaskRuntimePhaseOutput.forPlanningImport(
    storedPayload: String,
    storedEvidence: FeatureTaskRuntimePhaseOutputRepairEvidence?,
  ): AcceptedFeatureTaskRuntimePhaseOutput =
    copy(
      normalizedOutput =
        if (repairEvidence == null) {
          normalizedOutput.copy(canonicalJson = storedPayload)
        } else {
          normalizedOutput
        },
      repairEvidence = repairEvidence ?: storedEvidence,
    )

  private fun createImportedRecords(
    preplan: AcceptedFeatureTaskRuntimePhaseOutput,
    plan: AcceptedFeatureTaskRuntimePhaseOutput,
    importedAt: String,
  ): Map<String, Map<String, Any?>> =
    linkedMapOf(
      "preplan" to
        importedRecord(
          "preplan",
          preplan.normalizedOutput.canonicalJson,
          preplan.repairEvidence,
          importedAt,
        ).asWorkflowArtifactEntry().let(::workflowArtifactEntryMap),
      "plan" to
        importedRecord(
          "plan",
          plan.normalizedOutput.canonicalJson,
          plan.repairEvidence,
          importedAt,
        ).asWorkflowArtifactEntry().let(::workflowArtifactEntryMap),
    )

  private fun createImportedLedger(importedAt: String): List<Map<String, Any?>> =
    PLANNING_PHASE_IDS.mapIndexed { sequence, phaseId ->
      FeatureTaskRuntimePhaseLedgerEntry(
        action = FeatureTaskRuntimePhaseLedgerAction.COMPLETE,
        sequenceNumber = sequence,
        timestamp = importedAt,
        phaseId = phaseId,
        attemptCount = 1,
        executionOrigin = FeatureTaskRuntimePhaseExecutionOrigin.GOAL_PLANNING_HYDRATED,
      ).asWorkflowArtifactEntry().let(::workflowArtifactEntryMap)
    }

  private fun createProvenance(
    request: GoalChildPlanningHydrationRequest,
    prepared: PreparedGoalPlanning,
  ): Map<String, Any?> =
    FeatureTaskRuntimeGoalPlanningImport(
      parentGoalWorkflowId = request.identity.parentGoalWorkflowId,
      normalizedIssueKey = request.identity.normalizedIssueKey,
      repositoryIdentity = request.identity.repositoryIdentity,
      parentSpecHash = request.provenance.parentSpecHash,
      decompositionManifestHash = request.provenance.decompositionManifestHash,
      planningContractId = request.provenance.planningContractId,
      planningContractVersion = request.provenance.planningContractVersion,
      phaseOutputContractId = request.provenance.phaseOutputContractId,
      phaseOutputContractVersion = request.provenance.phaseOutputContractVersion,
      subtaskId = request.descriptor.subtaskId,
      manifestOrder = request.descriptor.manifestOrder,
      governedSubSpecPath = request.descriptor.governedSubSpecPath,
      subSpecHash = request.descriptor.subSpecHash,
      preplanPayloadSha256 = prepared.shared.payloadSha256,
      planPayloadSha256 = prepared.plan.payloadSha256,
    ).asWorkflowArtifactEntry().let(::workflowArtifactEntryMap)
}

private class PreparedPlanningPayloadValidator(
  private val phaseOutputValidator: FeatureTaskRuntimePhaseOutputValidator,
  private val planningProjectionValidator: FeatureTaskRuntimeWireArtifactValidator,
) {
  fun requireValid(
    phaseId: String,
    payload: String,
    expectedDigest: String,
    workflowId: String,
  ): AcceptedFeatureTaskRuntimePhaseOutput {
    val originalDigest = sha256HexUtf8(payload)
    if (originalDigest != expectedDigest) {
      invalidPlanningPreparation(workflowId, "$phaseId.payload_sha256", "payload digest differs")
    }
    val accepted = phaseOutputValidator.validatePhaseOutput(payload, phaseId).requireAcceptedOutput(phaseId)
    val repairEvidence = accepted.repairEvidence
    if (repairEvidence != null && repairEvidence.originalDigest != originalDigest) {
      invalidPlanningPreparation(
        workflowId,
        "$phaseId.repair_evidence.original_digest",
        "repair evidence does not describe the stored payload bytes",
      )
    }
    val decoded = accepted.normalizedOutput.envelopeWireMap()
    if (
      decoded[SharedPayloadKeys.PHASE_ID] != phaseId ||
      decoded[SharedPayloadKeys.STATUS].workflowStepStatus() != WorkflowStepStatus.COMPLETED
    ) {
      invalidPlanningPreparation(
        workflowId,
        "$phaseId.payload",
        "imported phase output must match its phase and be completed",
      )
    }
    requireValidPlanningProjection(
      envelope = decoded,
      phaseId = phaseId,
      sourceLabel = workflowId,
      planningProjectionValidator = planningProjectionValidator,
      fieldPath = "$phaseId.payload",
    )
    return accepted
  }
}

private fun invalidPlanningPreparation(
  workflowId: String,
  fieldPath: String,
  reason: String,
): Nothing = throw InvalidGoalPlanningPreparationSchemaError(workflowId, fieldPath, reason)

private class GoalChildPlanningImportMatcher(
  private val payloadValidator: PreparedPlanningPayloadValidator,
) {
  fun firstDivergence(
    unitOfWork: GoalRunnerPersistenceSession,
    existing: WorkflowStateSnapshot,
    setup: GoalRunnerChildWorkflowSetup,
    request: GoalChildPlanningHydrationRequest,
  ): String? {
    val artifacts = existing.artifacts
    val expected =
      DurableWorkflowArtifactFamily.FEATURE_TASK_RUNTIME_GOAL_PLANNING_IMPORT.value(artifacts) as? Map<*, *>
        ?: return "child carries no goal planning import artifact"
    val shared = unitOfWork.goalPlanningPreparations.findSharedPreplan(request.identity)
    val plan =
      unitOfWork.goalPlanningPreparations.findSubtaskPlan(
        request.identity,
        request.descriptor.subtaskId,
        request.descriptor.governedSubSpecPath,
      )
    validateAvailablePayloads(shared, plan, setup, request)
    val provenanceDivergence = provenanceDivergence(expected, request)
    return when {
      provenanceDivergence != null -> provenanceDivergence
      !preparedMatches(shared, plan, request) ->
        "parent planning checkpoints are missing or have incompatible provenance"
      !ledgerMatches(artifacts) ->
        "phase ledger no longer opens with the goal planning import prefix"
      !planningPhasesSettled(artifacts, existing) ->
        "child planning phases are not settled as completed"
      else -> null
    }
  }

  private fun validateAvailablePayloads(
    shared: SharedGoalPreplanCheckpoint?,
    plan: GoalSubtaskPlanCheckpoint?,
    setup: GoalRunnerChildWorkflowSetup,
    request: GoalChildPlanningHydrationRequest,
  ) {
    shared?.let {
      requireImportedPayloadValid("preplan", it.preplanPayload, it.payloadSha256, setup, request)
    }
    plan?.let {
      requireImportedPayloadValid("plan", it.planPayload, it.payloadSha256, setup, request)
    }
  }

  private fun requireImportedPayloadValid(
    phaseId: String,
    payload: String,
    digest: String,
    setup: GoalRunnerChildWorkflowSetup,
    request: GoalChildPlanningHydrationRequest,
  ) {
    try {
      payloadValidator.requireValid(phaseId, payload, digest, setup.workflowId)
    } catch (error: InvalidGoalPlanningPreparationSchemaError) {
      throw importedPayloadRecoveryError(phaseId, setup, request, error)
    } catch (error: InvalidFeatureTaskRuntimePhaseOutputSchemaError) {
      throw importedPayloadRecoveryError(phaseId, setup, request, error)
    }
  }

  private fun importedPayloadRecoveryError(
    phaseId: String,
    setup: GoalRunnerChildWorkflowSetup,
    request: GoalChildPlanningHydrationRequest,
    error: Throwable,
  ): IncompatibleGoalPlanningPreparationRecoveryError {
    val detail = error.message.orEmpty()
    val reason =
      if (classifyGoalPlanningRecovery(detail, error) == GoalPlanningRecoveryKind.HARD_RESET) {
        "stored goal planning '$phaseId' record for subtask ${request.descriptor.subtaskId} fails the " +
          "installed phase-output contract and requires a hard reset. Projection failure: $detail"
      } else {
        "stored goal planning '$phaseId' record for subtask ${request.descriptor.subtaskId} was already " +
          "imported by this child and the stored version now fails its projection contract. " +
          "This occurs when the shared preplan or subtask plan was regenerated after the child was hydrated, " +
          "making the previously-imported bytes stale. Projection failure: $detail"
      }
    return IncompatibleGoalPlanningPreparationRecoveryError(
      request.identity.parentGoalWorkflowId,
      setup.subtaskId,
      reason,
      error,
    )
  }

  private fun provenanceDivergence(
    expected: Map<*, *>,
    request: GoalChildPlanningHydrationRequest,
  ): String? {
    val mismatched = expectedProvenance(request).filter { (key, value) -> expected[key] != value }.keys
    if (mismatched.isEmpty()) return null
    return "stored import provenance differs from the hydration request at " +
      mismatched.joinToString(", ")
  }

  private fun preparedMatches(
    shared: SharedGoalPreplanCheckpoint?,
    plan: GoalSubtaskPlanCheckpoint?,
    request: GoalChildPlanningHydrationRequest,
  ): Boolean =
    listOf(
      shared != null,
      plan != null,
      shared?.provenance?.copy(parentSpecHash = request.provenance.parentSpecHash) == request.provenance,
      plan?.provenance?.copy(parentSpecHash = request.provenance.parentSpecHash) == request.provenance,
      plan?.manifestOrder == request.descriptor.manifestOrder,
    ).all { it }

  private fun planningPhasesSettled(
    artifacts: Map<String, Any?>,
    existing: WorkflowStateSnapshot,
  ): Boolean {
    val records =
      DurableWorkflowArtifactFamily.FEATURE_TASK_RUNTIME_PHASE_RECORDS.value(artifacts) as? Map<*, *> ?: return false
    val expected =
      DurableWorkflowArtifactFamily.FEATURE_TASK_RUNTIME_GOAL_PLANNING_IMPORT.value(artifacts) as? Map<*, *>
        ?: return false
    val expectedStepStatuses =
      PLANNING_PHASE_IDS.mapNotNull { phaseId ->
        settledStepStatus(records[phaseId] as? Map<*, *>, phaseId)?.let { phaseId to it }
      }.toMap()
    val preplanOutput = (records["preplan"] as? Map<*, *>)?.get("output_artifact") as? String ?: return false
    return expectedStepStatuses.size == PLANNING_PHASE_IDS.size &&
      sha256HexUtf8(preplanOutput) == expected["preplan_payload_sha256"] &&
      stepsSettled(existing, expectedStepStatuses)
  }

  private fun settledStepStatus(
    record: Map<*, *>?,
    phaseId: String,
  ): WorkflowStepStatus? {
    if (record == null || record[SharedPayloadKeys.PHASE_ID] != phaseId) return null
    return when (record[SharedPayloadKeys.STATUS].workflowStepStatus()) {
      WorkflowStepStatus.COMPLETED ->
        WorkflowStepStatus.COMPLETED
          .takeIf { (record["output_artifact"] as? String)?.isNotBlank() == true }
      WorkflowStepStatus.RUNNING -> WorkflowStepStatus.RUNNING
      WorkflowStepStatus.BLOCKED -> WorkflowStepStatus.BLOCKED
      else -> null
    }
  }

  private fun ledgerMatches(artifacts: Map<String, Any?>): Boolean {
    val ledger =
      (DurableWorkflowArtifactFamily.FEATURE_TASK_RUNTIME_PHASE_LEDGER.value(artifacts) as? List<*>)
        ?.mapNotNull { it as? Map<*, *> }
        ?: return false
    if (ledger.size < PLANNING_PHASE_IDS.size) return false
    return ledger.take(PLANNING_PHASE_IDS.size).withIndex().all { (index, entry) ->
      listOf(
        (entry["action"] as? String)?.let(FeatureTaskRuntimePhaseLedgerAction::fromWire) ==
          FeatureTaskRuntimePhaseLedgerAction.COMPLETE,
        (entry["sequence_number"] as? Number)?.toInt() == index,
        entry[SharedPayloadKeys.PHASE_ID] == PLANNING_PHASE_IDS[index],
        (entry["attempt_count"] as? Number)?.toInt() == 1,
        entry["resolved_agent_id"] == null,
      ).all { it }
    }
  }

  private fun stepsSettled(
    existing: WorkflowStateSnapshot,
    expected: Map<String, WorkflowStepStatus>,
  ): Boolean {
    val planningSteps = existing.steps.filter { it.stepId in PLANNING_PHASE_IDS }
    return planningSteps.size == PLANNING_PHASE_IDS.size &&
      planningSteps.all { it.status.workflowStepStatus() == expected[it.stepId] }
  }
}

internal fun expectedProvenance(request: GoalChildPlanningHydrationRequest): Map<String, Any?> =
  mapOf(
    "source_kind" to "imported_goal_planning",
    "parent_goal_workflow_id" to request.identity.parentGoalWorkflowId,
    "normalized_issue_key" to request.identity.normalizedIssueKey,
    "repository_identity" to request.identity.repositoryIdentity,
    SharedPayloadKeys.SUBTASK_ID to request.descriptor.subtaskId,
    "manifest_order" to request.descriptor.manifestOrder,
    "governed_sub_spec_path" to request.descriptor.governedSubSpecPath,
    "decomposition_manifest_hash" to request.provenance.decompositionManifestHash,
    "planning_contract_id" to request.provenance.planningContractId,
    "planning_contract_version" to request.provenance.planningContractVersion,
    "phase_output_contract_id" to request.provenance.phaseOutputContractId,
    "phase_output_contract_version" to request.provenance.phaseOutputContractVersion,
  )

private fun completedStep(phaseId: String): Map<String, Any?> =
  linkedMapOf(
    SharedPayloadKeys.STEP_ID to phaseId,
    SharedPayloadKeys.STATUS to "completed",
    "attempt_count" to 1,
  )

private fun importedRecord(
  phaseId: String,
  payload: String,
  repairEvidence: FeatureTaskRuntimePhaseOutputRepairEvidence?,
  importedAt: String,
): FeatureTaskRuntimePhaseRecord =
  FeatureTaskRuntimePhaseRecord(
    phaseId = phaseId,
    status = "completed",
    attemptCount = 1,
    startedAt = importedAt,
    finishedAt = importedAt,
    durationMillis = 0,
    resolvedAgentId = "goal-planning-import",
    executionOrigin = FeatureTaskRuntimePhaseExecutionOrigin.GOAL_PLANNING_HYDRATED,
    outputArtifact = payload,
    repairEvidence = repairEvidence,
  )

private val PLANNING_PHASE_IDS = listOf("preplan", "plan")
