package skillbill.infrastructure.contracts

import me.tatarka.inject.annotations.Inject
import skillbill.infrastructure.contracts.workflow.featuretask.handoff.FeatureTaskRuntimeBuildReceiptSchemaValidator
import skillbill.infrastructure.contracts.workflow.featuretask.handoff.FeatureTaskRuntimeHandoffEnvelopeSchemaValidator
import skillbill.infrastructure.contracts.workflow.featuretask.handoff.FeatureTaskRuntimePersistenceSchemaValidator
import skillbill.infrastructure.contracts.workflow.featuretask.handoff.FeatureTaskRuntimePhaseHandoffSchemaValidator
import skillbill.infrastructure.contracts.workflow.featuretask.handoff.FeatureTaskRuntimeProjectionMeasurementSchemaValidator
import skillbill.infrastructure.contracts.workflow.featuretask.handoff.FeatureTaskRuntimeSharedEvidenceProjectionSchemaValidator
import skillbill.infrastructure.contracts.workflow.featuretask.phase.task.runtime.implementation.FeatureTaskRuntimeImplementationAttemptSchemaValidator
import skillbill.infrastructure.contracts.workflow.featuretask.phase.task.runtime.planning.FeatureTaskRuntimePlanningProjectionSchemaValidator
import skillbill.infrastructure.contracts.workflow.featuretask.phase.task.runtime.quarantine.FeatureTaskRuntimeQuarantineSchemaValidator
import skillbill.infrastructure.contracts.workflow.goal.observability.GoalObservabilityEventSchemaValidator
import skillbill.infrastructure.contracts.workflow.goal.planning.GoalPlanningPreparationSchemaValidator
import skillbill.infrastructure.contracts.workflow.goal.progress.GoalProgressEventSchemaValidator
import skillbill.ports.taskruntime.FeatureTaskRuntimeWireArtifactValidator
import skillbill.workflow.taskruntime.model.core.FeatureTaskRuntimeWireArtifactKind
import skillbill.workflow.taskruntime.model.core.FeatureTaskRuntimeWorkflowArtifactMap

@Inject
class FeatureTaskRuntimeWireArtifactValidator : FeatureTaskRuntimeWireArtifactValidator {
  override fun validate(
    kind: FeatureTaskRuntimeWireArtifactKind,
    payload: FeatureTaskRuntimeWorkflowArtifactMap,
    sourceLabel: String,
  ) {
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
