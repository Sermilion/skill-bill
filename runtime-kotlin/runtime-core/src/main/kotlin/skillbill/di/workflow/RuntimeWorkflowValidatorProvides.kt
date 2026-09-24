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
  @Provides
  fun decompositionManifestValidator(): DecompositionManifestValidator = DecompositionManifestSchemaValidator()

  @Provides
  fun workflowSnapshotValidator(): WorkflowSnapshotValidator = WorkflowStateSchemaValidator()

  @Provides
  fun goalPlanningPreparationEnvelopeValidator(): GoalPlanningPreparationEnvelopeValidator =
    FeatureTaskRuntimeWireArtifactValidator()

  @Provides
  fun goalObservabilityEventValidator(): GoalObservabilityEventValidator = FeatureTaskRuntimeWireArtifactValidator()

  @Provides
  fun goalProgressEventValidator(): GoalProgressEventValidator = FeatureTaskRuntimeWireArtifactValidator()

  @Provides
  fun ideStatusValidator(): IdeStatusValidator = IdeStatusSchemaValidator()
}
