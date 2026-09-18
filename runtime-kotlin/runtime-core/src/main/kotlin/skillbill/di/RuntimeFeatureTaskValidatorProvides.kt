package skillbill.di

import me.tatarka.inject.annotations.Provides
import skillbill.infrastructure.fs.FeatureTaskRuntimePhaseOutputSchemaValidator
import skillbill.infrastructure.fs.ProducerOutputEvidenceSchemaValidator
import skillbill.infrastructure.fs.RejectedOutputDiagnosticSchemaValidator
import skillbill.ports.diagnostics.ProducerOutputEvidenceValidator
import skillbill.ports.diagnostics.RejectedOutputDiagnosticMetadataValidator
import skillbill.workflow.taskruntime.FeatureTaskRuntimePhaseOutputValidator
import skillbill.infrastructure.fs.FeatureTaskRuntimeWireArtifactValidator as FeatureTaskRuntimeWireArtifactSchemaValidator
import skillbill.workflow.taskruntime.FeatureTaskRuntimeWireArtifactValidator as FeatureTaskRuntimeWireArtifactValidatorPort

internal interface RuntimeFeatureTaskValidatorProvides {
  @Provides @JvmSynthetic
  fun featureTaskRuntimePhaseOutputValidator(
    validator: FeatureTaskRuntimePhaseOutputSchemaValidator,
  ): FeatureTaskRuntimePhaseOutputValidator = validator

  @Provides @JvmSynthetic
  fun featureTaskRuntimeWireArtifactValidator(
    validator: FeatureTaskRuntimeWireArtifactSchemaValidator,
  ): FeatureTaskRuntimeWireArtifactValidatorPort = validator

  @Provides @JvmSynthetic
  fun rejectedOutputDiagnosticMetadataValidator(): RejectedOutputDiagnosticMetadataValidator =
    RejectedOutputDiagnosticSchemaValidator()

  @Provides @JvmSynthetic
  fun producerOutputEvidenceValidator(): ProducerOutputEvidenceValidator = ProducerOutputEvidenceSchemaValidator()
}
