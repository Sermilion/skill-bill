package skillbill.di.workflow
import me.tatarka.inject.annotations.Provides
import skillbill.infrastructure.contracts.FeatureTaskRuntimeWireArtifactValidator
import skillbill.infrastructure.contracts.workflow.decomposition.DecompositionManifestSchemaValidator
import skillbill.infrastructure.contracts.workflow.goal.status.IdeStatusSchemaValidator
import skillbill.infrastructure.contracts.workflow.workflow.WorkflowStateSchemaValidator
import skillbill.ports.idestatus.IdeStatusValidator
import skillbill.workflow.decomposition.DecompositionManifestValidator
import skillbill.workflow.engine.WorkflowSnapshotValidator
import skillbill.workflow.goal.GoalObservabilityEventValidator
import skillbill.workflow.goal.GoalPlanningPreparationEnvelopeValidator
import skillbill.workflow.goal.GoalProgressEventValidator

internal interface RuntimeWorkflowValidatorProvides {
  @Provides @JvmSynthetic
  fun decompositionManifestValidator(validator: DecompositionManifestSchemaValidator): DecompositionManifestValidator =
    validator

  @Provides @JvmSynthetic
  fun workflowSnapshotValidator(validator: WorkflowStateSchemaValidator): WorkflowSnapshotValidator = validator

  @Provides @JvmSynthetic
  fun goalPlanningPreparationEnvelopeValidator(
    validator: FeatureTaskRuntimeWireArtifactValidator,
  ): GoalPlanningPreparationEnvelopeValidator = validator

  @Provides @JvmSynthetic
  fun goalObservabilityEventValidator(
    validator: FeatureTaskRuntimeWireArtifactValidator,
  ): GoalObservabilityEventValidator = validator

  @Provides @JvmSynthetic
  fun goalProgressEventValidator(validator: FeatureTaskRuntimeWireArtifactValidator): GoalProgressEventValidator =
    validator

  @Provides @JvmSynthetic
  fun ideStatusValidator(validator: IdeStatusSchemaValidator): IdeStatusValidator = validator
}
