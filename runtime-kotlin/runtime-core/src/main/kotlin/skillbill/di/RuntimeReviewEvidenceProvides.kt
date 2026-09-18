package skillbill.di

import me.tatarka.inject.annotations.Provides
import skillbill.infrastructure.contracts.review.ReviewContextSchemaValidator
import skillbill.infrastructure.launcher.review.UnixSocketGovernedReviewEvidenceEndpointBinder
import skillbill.infrastructure.workflow.FileSystemDiffResolver
import skillbill.infrastructure.workflow.FileSystemFeatureTaskRuntimeSharedEvidenceStore
import skillbill.infrastructure.workflow.FileSystemReviewEvidenceBroker
import skillbill.infrastructure.workflow.FileSystemReviewInputSource
import skillbill.infrastructure.workflow.FileSystemReviewSnapshotGateway
import skillbill.ports.diff.DiffResolverPort
import skillbill.ports.review.GovernedReviewEvidenceEndpointBinder
import skillbill.ports.review.ReviewEvidenceBrokerFactory
import skillbill.ports.review.ReviewInputSource
import skillbill.ports.review.ReviewSnapshotGateway
import skillbill.ports.taskruntime.FeatureTaskRuntimeSharedEvidenceLocatorReadPort
import skillbill.ports.taskruntime.FeatureTaskRuntimeSharedEvidenceResolverPort
import skillbill.review.ParallelReviewFindingParser
import skillbill.review.context.ReviewContextEnvelopeValidator
import skillbill.review.model.ParallelReviewParseResult

internal interface RuntimeReviewEvidenceProvides {
  @Provides @JvmSynthetic
  fun reviewContextEnvelopeValidator(validator: ReviewContextSchemaValidator): ReviewContextEnvelopeValidator =
    validator

  @Provides @JvmSynthetic
  fun reviewEvidenceBrokerFactory(): ReviewEvidenceBrokerFactory =
    ReviewEvidenceBrokerFactory { binding -> FileSystemReviewEvidenceBroker(binding) }

  @Provides @JvmSynthetic
  fun governedReviewEvidenceEndpointBinder(
    adapter: UnixSocketGovernedReviewEvidenceEndpointBinder,
  ): GovernedReviewEvidenceEndpointBinder = adapter

  @Provides @JvmSynthetic
  fun sharedEvidenceResolverPort(
    adapter: FileSystemFeatureTaskRuntimeSharedEvidenceStore,
  ): FeatureTaskRuntimeSharedEvidenceResolverPort = adapter

  @Provides @JvmSynthetic
  fun sharedEvidenceLocatorReadPort(
    adapter: FileSystemFeatureTaskRuntimeSharedEvidenceStore,
  ): FeatureTaskRuntimeSharedEvidenceLocatorReadPort = adapter

  @Provides @JvmSynthetic
  fun reviewSnapshotGateway(gateway: FileSystemReviewSnapshotGateway): ReviewSnapshotGateway = gateway

  @Provides @JvmSynthetic
  fun reviewInputSource(source: FileSystemReviewInputSource): ReviewInputSource = source

  @Provides @JvmSynthetic
  fun diffResolverPort(adapter: FileSystemDiffResolver): DiffResolverPort = adapter

  @Provides @JvmSynthetic
  fun parallelReviewParseRegister(): (String) -> ParallelReviewParseResult = ParallelReviewFindingParser::parse
}
