package skillbill.infrastructure.fs.contracts

import skillbill.contracts.JsonCodec
import skillbill.contracts.workflow.FEATURE_TASK_RUNTIME_HANDOFF_ENVELOPE_CONTRACT_VERSION
import skillbill.contracts.workflow.FeatureTaskRuntimeHandoffEnvelopeSchemaPaths
import skillbill.error.FeatureTaskRuntimeHandoffProjectionFailureKind
import skillbill.error.InvalidFeatureTaskRuntimeBuildReceiptSchemaError
import skillbill.error.InvalidFeatureTaskRuntimeHandoffProjectionContext
import skillbill.error.InvalidFeatureTaskRuntimeHandoffProjectionError
import skillbill.error.InvalidFeatureTaskRuntimeImplementationAttemptSchemaError
import skillbill.error.InvalidFeatureTaskRuntimePersistenceSchemaError
import skillbill.error.InvalidFeatureTaskRuntimePhaseHandoffSchemaError
import skillbill.error.InvalidFeatureTaskRuntimePlanningProjectionSchemaError
import skillbill.error.InvalidFeatureTaskRuntimeProjectionMeasurementSchemaError
import skillbill.error.InvalidFeatureTaskRuntimeQuarantineSchemaError
import skillbill.error.InvalidFeatureTaskRuntimeSharedEvidenceProjectionSchemaError
import skillbill.error.InvalidGoalObservabilityEventSchemaError
import skillbill.error.InvalidGoalPlanningPreparationSchemaError
import skillbill.error.InvalidGoalProgressEventSchemaError
import skillbill.error.ShellContentContractException
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeWireArtifactKind

internal fun requireValidatorWireMap(
  payload: Any,
  sourceLabel: String,
  nonObjectError: (sourceLabel: String, reason: String) -> ShellContentContractException,
): Map<String, Any?> = JsonCodec.anyToStringAnyMap(payload)
  ?: throw nonObjectError(sourceLabel, "<root> must be an object.")

internal fun requireFeatureTaskRuntimeArtifactMap(
  kind: FeatureTaskRuntimeWireArtifactKind,
  payload: Any,
  sourceLabel: String,
): Map<String, Any?> = requireValidatorWireMap(payload, sourceLabel) { label, reason ->
  featureTaskRuntimeWireArtifactNonObjectError(kind, label, reason)
}

internal fun featureTaskRuntimeWireArtifactNonObjectError(
  kind: FeatureTaskRuntimeWireArtifactKind,
  sourceLabel: String,
  reason: String,
): ShellContentContractException = when (kind) {
  FeatureTaskRuntimeWireArtifactKind.QUARANTINE_RECORD ->
    InvalidFeatureTaskRuntimeQuarantineSchemaError(sourceLabel = sourceLabel, reason = reason)
  FeatureTaskRuntimeWireArtifactKind.PLANNING_PROJECTION ->
    InvalidFeatureTaskRuntimePlanningProjectionSchemaError(sourceLabel = sourceLabel, reason = reason)
  FeatureTaskRuntimeWireArtifactKind.IMPLEMENTATION_ATTEMPT ->
    InvalidFeatureTaskRuntimeImplementationAttemptSchemaError(sourceLabel = sourceLabel, reason = reason)
  FeatureTaskRuntimeWireArtifactKind.BUILD_RECEIPT ->
    InvalidFeatureTaskRuntimeBuildReceiptSchemaError(
      sourceLabel = sourceLabel,
      reason = reason,
      payloadFreeReason = reason,
    )
  FeatureTaskRuntimeWireArtifactKind.HANDOFF_DECLARATION ->
    InvalidFeatureTaskRuntimePhaseHandoffSchemaError(sourceLabel = sourceLabel, reason = reason)
  FeatureTaskRuntimeWireArtifactKind.HANDOFF_PERSISTENCE_RECORD ->
    InvalidFeatureTaskRuntimePersistenceSchemaError(sourceLabel = sourceLabel, reason = reason)
  FeatureTaskRuntimeWireArtifactKind.HANDOFF_MEASUREMENT ->
    InvalidFeatureTaskRuntimeProjectionMeasurementSchemaError(sourceLabel = sourceLabel, reason = reason)
  FeatureTaskRuntimeWireArtifactKind.HANDOFF_SHARED_EVIDENCE_PROJECTION ->
    InvalidFeatureTaskRuntimeSharedEvidenceProjectionSchemaError(sourceLabel = sourceLabel, reason = reason)
  FeatureTaskRuntimeWireArtifactKind.HANDOFF_ENVELOPE ->
    InvalidFeatureTaskRuntimeHandoffProjectionError(
      context = InvalidFeatureTaskRuntimeHandoffProjectionContext(
        workflowId = null,
        consumerPhaseId = sourceLabel,
        projectionName = "<root>",
        projectionContractId = FeatureTaskRuntimeHandoffEnvelopeSchemaPaths.EXPECTED_SCHEMA_ID,
        projectionContractVersion = FEATURE_TASK_RUNTIME_HANDOFF_ENVELOPE_CONTRACT_VERSION,
        failureKind = FeatureTaskRuntimeHandoffProjectionFailureKind.SCHEMA_INVALID,
        reason = reason,
      ),
    )
  FeatureTaskRuntimeWireArtifactKind.GOAL_PROGRESS_EVENT ->
    InvalidGoalProgressEventSchemaError(
      sourceLabel = sourceLabel,
      fieldPath = "<root>",
      reason = reason,
    )
  FeatureTaskRuntimeWireArtifactKind.GOAL_OBSERVABILITY_EVENT ->
    InvalidGoalObservabilityEventSchemaError(
      sourceLabel = sourceLabel,
      fieldPath = "<root>",
      reason = reason,
    )
  FeatureTaskRuntimeWireArtifactKind.GOAL_PLANNING_PREPARATION_ENVELOPE ->
    InvalidGoalPlanningPreparationSchemaError(
      sourceLabel = sourceLabel,
      fieldPath = "<root>",
      reason = reason,
    )
}
