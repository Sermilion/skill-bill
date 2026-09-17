package skillbill.di

import me.tatarka.inject.annotations.Provides
import skillbill.infrastructure.fs.FeatureTaskRuntimePhaseOutputValidatorAdapter
import skillbill.infrastructure.fs.FeatureTaskRuntimeWireArtifactValidatorAdapter
import skillbill.infrastructure.fs.ProducerOutputEvidenceValidatorAdapter
import skillbill.infrastructure.fs.RejectedOutputDiagnosticMetadataValidatorAdapter
import skillbill.ports.diagnostics.ProducerOutputEvidenceValidator
import skillbill.ports.diagnostics.RejectedOutputDiagnosticMetadataValidator
import skillbill.workflow.taskruntime.FeatureTaskRuntimePhaseOutputValidator
import skillbill.workflow.taskruntime.FeatureTaskRuntimeWireArtifactValidator

internal interface RuntimeFeatureTaskValidatorProvides {
  @Provides @JvmSynthetic
  fun featureTaskRuntimePhaseOutputValidator(
    adapter: FeatureTaskRuntimePhaseOutputValidatorAdapter,
  ): FeatureTaskRuntimePhaseOutputValidator = adapter

  @Provides @JvmSynthetic
  fun featureTaskRuntimeWireArtifactValidator(
    adapter: FeatureTaskRuntimeWireArtifactValidatorAdapter,
  ): FeatureTaskRuntimeWireArtifactValidator = adapter

  @Provides @JvmSynthetic
  fun rejectedOutputDiagnosticMetadataValidator(): RejectedOutputDiagnosticMetadataValidator =
    RejectedOutputDiagnosticMetadataValidatorAdapter()

  @Provides @JvmSynthetic
  fun producerOutputEvidenceValidator(): ProducerOutputEvidenceValidator = ProducerOutputEvidenceValidatorAdapter()
}
