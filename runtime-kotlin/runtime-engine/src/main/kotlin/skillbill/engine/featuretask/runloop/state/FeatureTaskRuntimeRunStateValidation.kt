package skillbill.engine.featuretask.runloop.state

import skillbill.contracts.JsonCodec
import skillbill.contracts.SharedPayloadKeys
import skillbill.contracts.workflow.identity.evidence.ValidationEvidencePayloadKeys
import skillbill.engine.goalrunner.status.completed
import skillbill.error.shellcontent.InvalidFeatureTaskRuntimePhaseOutputSchemaError
import skillbill.workflow.model.WorkflowStepStatus
import skillbill.workflow.model.validation.FeatureTaskRuntimeVerdict
import skillbill.workflow.model.workflowStepStatus
import skillbill.workflow.taskruntime.artifact.decodeValidationEvidenceFromArtifact
import skillbill.workflow.taskruntime.artifact.envelopeWireMap
import skillbill.workflow.taskruntime.model.handoff.task.FeatureTaskRuntimePhaseOutput
import skillbill.workflow.taskruntime.model.phase.FeatureTaskRuntimePhaseRecord
import skillbill.workflow.taskruntime.model.phase.FeatureTaskRuntimeTransitionDeclaration
import skillbill.workflow.taskruntime.model.validation.FeatureTaskRuntimeValidationEvidence
import skillbill.workflow.taskruntime.phase.task.FeatureTaskRuntimePhaseWorkflowDefinition

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
    val invalidated =
      transitions.forwardPhaseIds
        .dropWhile { it != FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_VALIDATE }
        .filter(completedState::contains) + FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_VALIDATE
    completedState.removeAll(invalidated.toSet())
    gateInvalidatedState.addAll(invalidated)
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
  val durableVerdictFor: (String) -> FeatureTaskRuntimeVerdict,
)

internal fun validationEvidenceFromEnvelope(
  envelope: Map<String, Any?>,
  sourceLabel: String,
): FeatureTaskRuntimeValidationEvidence? {
  val produced = JsonCodec.anyToStringAnyMap(envelope[SharedPayloadKeys.PRODUCED_OUTPUTS])
  val result =
    JsonCodec.anyToStringAnyMap(
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
  val output =
    try {
      validation.validatedRecordToOutput(record)
    } catch (_: InvalidFeatureTaskRuntimePhaseOutputSchemaError) {
      null
    }
  val envelope = output?.normalizedOutput?.envelopeWireMap()
  val valid =
    envelope != null &&
      (envelope[SharedPayloadKeys.STATUS] as? String).workflowStepStatus() == WorkflowStepStatus.COMPLETED
  if (!valid) {
    state.invalidateValidationPhase()
    state.invalidateUnsatisfiedGateSuccessors(validation.durableVerdictFor)
  }
}
