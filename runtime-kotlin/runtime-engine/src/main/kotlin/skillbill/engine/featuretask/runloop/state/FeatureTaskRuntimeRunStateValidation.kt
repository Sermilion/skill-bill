package skillbill.engine.featuretask.runloop.state




import skillbill.engine.featuretask.phase.core.FeatureTaskRuntimePhaseGates
import skillbill.engine.featuretask.phase.record.FeatureTaskRuntimePhaseRecorder
import skillbill.engine.featuretask.runloop.settlement.FeatureTaskRuntimeRunLoopValidationGate
import skillbill.engine.featuretask.runloop.core.PhaseRun
import skillbill.contracts.JsonCodec
import skillbill.contracts.SharedPayloadKeys
import skillbill.contracts.workflow.ValidationEvidencePayloadKeys
import skillbill.engine.featuretask.validation.durableValidationChangedPaths
import skillbill.error.InvalidFeatureTaskRuntimeValidationEvidenceSchemaError
import skillbill.workflow.taskruntime.FeatureTaskRuntimePhaseWorkflowDefinition
import skillbill.workflow.taskruntime.decodeValidationEvidenceFromArtifact
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimePhaseOutput
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimePhaseRecord
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeTransitionDeclaration
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeValidationEvidence
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeVerdict

internal class ValidationSettlementState(
  completed: Set<String>,
  val initialRecords: Map<String, FeatureTaskRuntimePhaseRecord>,
  val transitions: FeatureTaskRuntimeTransitionDeclaration,
  gateInvalidatedPhases: Set<String>,
) {
  private val completedState = completed.toMutableSet()
  private val gateInvalidatedState = gateInvalidatedPhases.toMutableSet()

  val completed: Set<String>
    get() = completedState.toSet()

  val gateInvalidatedPhases: Set<String>
    get() = gateInvalidatedState.toSet()

  internal fun invalidateValidationPhase() {
    completedState.remove(FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_VALIDATE)
    gateInvalidatedState += FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_VALIDATE
  }

  internal fun invalidateUnsatisfiedGateSuccessors(durableVerdictFor: (String) -> FeatureTaskRuntimeVerdict) {
    FeatureTaskRuntimeRunStateReconstruction.invalidateUnsatisfiedGateSuccessors(
      transitions,
      completedState,
      gateInvalidatedState,
      durableVerdictFor,
    )
  }
}

internal data class ValidationSettlementValidation(
  val validatedRecordToOutput: (FeatureTaskRuntimePhaseRecord) -> FeatureTaskRuntimePhaseOutput?,
  val validationEvidenceCommandResolver: (FeatureTaskRuntimeValidationEvidence?) -> String?,
  val durableVerdictFor: (String) -> FeatureTaskRuntimeVerdict,
)

internal fun requireValidationEvidenceForValidateSettlement(
  recorder: FeatureTaskRuntimePhaseRecorder,
  phaseGates: FeatureTaskRuntimePhaseGates,
  run: PhaseRun,
  envelope: Map<
    String,

    Any?,
    >,
) {
  if (run.phaseId != FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_VALIDATE) return
  val evidence = validationEvidenceFromEnvelope(envelope, run.phaseId)
    ?: throw InvalidFeatureTaskRuntimeValidationEvidenceSchemaError(
      run.phaseId,
      "runtime-owned validation evidence is missing.",
    )
  evidence.requireSuccessfulCommand(
    FeatureTaskRuntimeRunLoopValidationGate.requiredValidationCommand(
      phaseGates = phaseGates,
      run = run,
      evidence = evidence,
      changedPaths = durableValidationChangedPaths(recorder, run.request.workflowId),
    ),
    run.phaseId,
  )
}

internal fun validationEvidenceFromEnvelope(
  envelope: Map<String, Any?>,
  sourceLabel: String,
): FeatureTaskRuntimeValidationEvidence? {
  val produced = JsonCodec.anyToStringAnyMap(envelope[SharedPayloadKeys.PRODUCED_OUTPUTS])
  val result = JsonCodec.anyToStringAnyMap(
    produced?.get(ValidationEvidencePayloadKeys.VALIDATION_RESULT),
  )
  return JsonCodec.anyToStringAnyMap(
    result?.get(ValidationEvidencePayloadKeys.VALIDATION_EVIDENCE),
  )?.let { raw -> decodeValidationEvidenceFromArtifact(raw, sourceLabel) }
}

internal fun invalidateIncompleteValidationSettlement(
  state: ValidationSettlementState,
  validation: ValidationSettlementValidation,
) {
  if (FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_VALIDATE !in state.completed) return
  val record = state.initialRecords[FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_VALIDATE] ?: return
  val valid = runCatching {
    val output = validation.validatedRecordToOutput(record) ?: return@runCatching false
    val produced = JsonCodec.anyToStringAnyMap(
      output.normalizedOutput?.envelopePayload(),
    )?.let { envelope ->
      JsonCodec.anyToStringAnyMap(envelope[SharedPayloadKeys.PRODUCED_OUTPUTS])
    }
    val result = JsonCodec.anyToStringAnyMap(
      produced?.get(ValidationEvidencePayloadKeys.VALIDATION_RESULT),
    )
    val evidence = JsonCodec.anyToStringAnyMap(
      result?.get(ValidationEvidencePayloadKeys.VALIDATION_EVIDENCE),
    )?.let { raw ->
      decodeValidationEvidenceFromArtifact(
        raw,
        FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_VALIDATE,
      )
    }
    val decodedEvidence = evidence ?: return@runCatching false
    val requiredCommand = validation.validationEvidenceCommandResolver(decodedEvidence)
      ?: return@runCatching false
    decodedEvidence.requireSuccessfulCommand(
      requiredCommand,
      FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_VALIDATE,
    )
    true
  }.getOrDefault(false)
  if (!valid) {
    state.invalidateValidationPhase()
    state.invalidateUnsatisfiedGateSuccessors(validation.durableVerdictFor)
  }
}
