package skillbill.di.featuretask
import me.tatarka.inject.annotations.Provides
import skillbill.infrastructure.contracts.FeatureTaskRuntimePhaseOutputSchemaValidator
import skillbill.infrastructure.contracts.ProducerOutputEvidenceSchemaValidator
import skillbill.infrastructure.contracts.RejectedOutputDiagnosticSchemaValidator
import skillbill.ports.diagnostics.ProducerOutputEvidenceValidator
import skillbill.ports.diagnostics.RejectedOutputDiagnosticMetadataValidator
import skillbill.workflow.taskruntime.model.core.FeatureTaskRuntimeWireArtifactValidator
import skillbill.workflow.taskruntime.phase.task.FeatureTaskRuntimePhaseOutputValidator
import skillbill.infrastructure.contracts.FeatureTaskRuntimeWireArtifactValidator as FeatureTaskRuntimeWireArtifactSchemaValidator
internal interface RuntimeFeatureTaskValidatorProvides {
  @Provides @JvmSynthetic
  fun featureTaskRuntimePhaseOutputValidator(): FeatureTaskRuntimePhaseOutputValidator =
    FeatureTaskRuntimePhaseOutputSchemaValidator()

  @Provides @JvmSynthetic
  fun featureTaskRuntimeWireArtifactValidator(): FeatureTaskRuntimeWireArtifactValidator =
    FeatureTaskRuntimeWireArtifactSchemaValidator()

  @Provides @JvmSynthetic
  fun rejectedOutputDiagnosticMetadataValidator(): RejectedOutputDiagnosticMetadataValidator =
    RejectedOutputDiagnosticSchemaValidator()

  @Provides @JvmSynthetic
  fun producerOutputEvidenceValidator(): ProducerOutputEvidenceValidator = ProducerOutputEvidenceSchemaValidator()
}
