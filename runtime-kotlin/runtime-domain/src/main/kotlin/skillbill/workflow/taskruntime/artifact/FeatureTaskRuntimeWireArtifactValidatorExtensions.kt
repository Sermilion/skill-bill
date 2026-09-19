package skillbill.workflow.taskruntime.artifact
import skillbill.workflow.taskruntime.feature.artifact
import skillbill.workflow.taskruntime.feature.envelope
import skillbill.workflow.taskruntime.handoff.envelope
import skillbill.workflow.taskruntime.handoff.validate
import skillbill.workflow.taskruntime.model.core.FeatureTaskRuntimeWireArtifactValidator
import skillbill.workflow.taskruntime.model.core.validate
import skillbill.workflow.taskruntime.phase.envelope
import skillbill.workflow.taskruntime.phase.task.BUILD_RECEIPT

fun FeatureTaskRuntimeWireArtifactValidator.validateQuarantineRecord(quarantineRecord: Any, sourceLabel: String) {
  validate(FeatureTaskRuntimeWireArtifactKind.QUARANTINE_RECORD, quarantineRecord, sourceLabel)
}

fun FeatureTaskRuntimeWireArtifactValidator.validatePlanningProjection(producedOutputs: Any, sourceLabel: String) {
  validate(FeatureTaskRuntimeWireArtifactKind.PLANNING_PROJECTION, producedOutputs, sourceLabel)
}

fun FeatureTaskRuntimeWireArtifactValidator.validateImplementationAttemptRecord(
  attemptRecord: Any,
  sourceLabel: String,
) {
  validate(FeatureTaskRuntimeWireArtifactKind.IMPLEMENTATION_ATTEMPT, attemptRecord, sourceLabel)
}

fun FeatureTaskRuntimeWireArtifactValidator.validateBuildReceipt(buildReceipt: Any, sourceLabel: String) {
  validate(FeatureTaskRuntimeWireArtifactKind.BUILD_RECEIPT, buildReceipt, sourceLabel)
}

fun FeatureTaskRuntimeWireArtifactValidator.validateDeclaration(payload: Any, sourceLabel: String) {
  validate(FeatureTaskRuntimeWireArtifactKind.HANDOFF_DECLARATION, payload, sourceLabel)
}

fun FeatureTaskRuntimeWireArtifactValidator.validatePersistenceRecord(payload: Any, sourceLabel: String) {
  validate(FeatureTaskRuntimeWireArtifactKind.HANDOFF_PERSISTENCE_RECORD, payload, sourceLabel)
}

fun FeatureTaskRuntimeWireArtifactValidator.validateMeasurement(payload: Any, sourceLabel: String) {
  validate(FeatureTaskRuntimeWireArtifactKind.HANDOFF_MEASUREMENT, payload, sourceLabel)
}

fun FeatureTaskRuntimeWireArtifactValidator.validateSharedEvidenceProjection(payload: Any, sourceLabel: String) {
  validate(FeatureTaskRuntimeWireArtifactKind.HANDOFF_SHARED_EVIDENCE_PROJECTION, payload, sourceLabel)
}

fun FeatureTaskRuntimeWireArtifactValidator.validateEnvelope(envelope: Any, workflowId: String? = null) {
  validate(
    FeatureTaskRuntimeWireArtifactKind.HANDOFF_ENVELOPE,
    envelope,
    workflowId ?: "handoff-envelope",
  )
}

fun FeatureTaskRuntimeWireArtifactValidator.validateGoalProgressEvent(event: Any, sourceLabel: String) {
  validate(FeatureTaskRuntimeWireArtifactKind.GOAL_PROGRESS_EVENT, event, sourceLabel)
}

fun FeatureTaskRuntimeWireArtifactValidator.validateGoalObservabilityEvent(event: Any, sourceLabel: String) {
  validate(FeatureTaskRuntimeWireArtifactKind.GOAL_OBSERVABILITY_EVENT, event, sourceLabel)
}

fun FeatureTaskRuntimeWireArtifactValidator.validateGoalPlanningPreparationEnvelope(
  envelope: Any,
  sourceLabel: String,
) {
  validate(FeatureTaskRuntimeWireArtifactKind.GOAL_PLANNING_PREPARATION_ENVELOPE, envelope, sourceLabel)
}
