package skillbill.infrastructure.contracts

import me.tatarka.inject.annotations.Inject
import skillbill.infrastructure.contracts.workflow.FeatureTaskRuntimeBuildReceiptSchemaValidator
import skillbill.infrastructure.contracts.workflow.FeatureTaskRuntimeHandoffEnvelopeSchemaValidator
import skillbill.infrastructure.contracts.workflow.FeatureTaskRuntimeImplementationAttemptSchemaValidator
import skillbill.infrastructure.contracts.workflow.FeatureTaskRuntimePersistenceSchemaValidator
import skillbill.infrastructure.contracts.workflow.FeatureTaskRuntimePhaseHandoffSchemaValidator
import skillbill.infrastructure.contracts.workflow.FeatureTaskRuntimePlanningProjectionSchemaValidator
import skillbill.infrastructure.contracts.workflow.FeatureTaskRuntimeProjectionMeasurementSchemaValidator
import skillbill.infrastructure.contracts.workflow.FeatureTaskRuntimeQuarantineSchemaValidator
import skillbill.infrastructure.contracts.workflow.FeatureTaskRuntimeSharedEvidenceProjectionSchemaValidator
import skillbill.infrastructure.contracts.workflow.GoalObservabilityEventSchemaValidator
import skillbill.infrastructure.contracts.workflow.GoalPlanningPreparationSchemaValidator
import skillbill.infrastructure.contracts.workflow.GoalProgressEventSchemaValidator
import skillbill.workflow.taskruntime.FeatureTaskRuntimeWireArtifactKind
import skillbill.workflow.taskruntime.FeatureTaskRuntimeWireArtifactValidator

@Inject
class FeatureTaskRuntimeWireArtifactValidator : FeatureTaskRuntimeWireArtifactValidator {
  override fun validate(kind: FeatureTaskRuntimeWireArtifactKind, payload: Any, sourceLabel: String) {
    val wireMap = requireFeatureTaskRuntimeArtifactMap(kind, payload, sourceLabel)
    when (kind) {
      FeatureTaskRuntimeWireArtifactKind.QUARANTINE_RECORD ->
        FeatureTaskRuntimeQuarantineSchemaValidator.validate(wireMap, sourceLabel)
      FeatureTaskRuntimeWireArtifactKind.PLANNING_PROJECTION ->
        FeatureTaskRuntimePlanningProjectionSchemaValidator.validate(wireMap, sourceLabel)
      FeatureTaskRuntimeWireArtifactKind.IMPLEMENTATION_ATTEMPT ->
        FeatureTaskRuntimeImplementationAttemptSchemaValidator.validate(wireMap, sourceLabel)
      FeatureTaskRuntimeWireArtifactKind.BUILD_RECEIPT ->
        FeatureTaskRuntimeBuildReceiptSchemaValidator.validate(wireMap, sourceLabel)
      FeatureTaskRuntimeWireArtifactKind.HANDOFF_DECLARATION ->
        FeatureTaskRuntimePhaseHandoffSchemaValidator.validate(wireMap, sourceLabel)
      FeatureTaskRuntimeWireArtifactKind.HANDOFF_PERSISTENCE_RECORD ->
        FeatureTaskRuntimePersistenceSchemaValidator.validate(wireMap, sourceLabel)
      FeatureTaskRuntimeWireArtifactKind.HANDOFF_MEASUREMENT ->
        FeatureTaskRuntimeProjectionMeasurementSchemaValidator.validate(wireMap, sourceLabel)
      FeatureTaskRuntimeWireArtifactKind.HANDOFF_SHARED_EVIDENCE_PROJECTION ->
        FeatureTaskRuntimeSharedEvidenceProjectionSchemaValidator.validate(wireMap, sourceLabel)
      FeatureTaskRuntimeWireArtifactKind.HANDOFF_ENVELOPE ->
        FeatureTaskRuntimeHandoffEnvelopeSchemaValidator.validate(wireMap, sourceLabel)
      FeatureTaskRuntimeWireArtifactKind.GOAL_PROGRESS_EVENT ->
        GoalProgressEventSchemaValidator.validate(wireMap, sourceLabel)
      FeatureTaskRuntimeWireArtifactKind.GOAL_OBSERVABILITY_EVENT ->
        GoalObservabilityEventSchemaValidator.validate(wireMap, sourceLabel)
      FeatureTaskRuntimeWireArtifactKind.GOAL_PLANNING_PREPARATION_ENVELOPE ->
        GoalPlanningPreparationSchemaValidator.validate(wireMap, sourceLabel)
    }
  }
}
