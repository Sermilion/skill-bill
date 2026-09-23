package skillbill.di.review
import me.tatarka.inject.annotations.Provides
import skillbill.infrastructure.contracts.review.ReviewContextSchemaValidator
import skillbill.infrastructure.launcher.review.UnixSocketGovernedReviewEvidenceEndpointBinder
import skillbill.infrastructure.workflow.featuretask.FileSystemFeatureTaskRuntimeSharedEvidenceStore
import skillbill.infrastructure.workflow.filesystem.FileSystemDiffResolver
import skillbill.infrastructure.workflow.git.standard.GitRepositoryOriginScopeKey
import skillbill.infrastructure.workflow.review.broker.FileSystemReviewEvidenceBroker
import skillbill.infrastructure.workflow.review.specialists.system.FileSystemReviewInputSource
import skillbill.infrastructure.workflow.review.specialists.system.FileSystemReviewSnapshotGateway
import skillbill.ports.diff.DiffResolverPort
import skillbill.ports.repository.RepositoryOriginScopeKeyPort
import skillbill.ports.review.evidence.GovernedReviewEvidenceEndpointBinder
import skillbill.ports.review.evidence.ReviewEvidenceBrokerFactory
import skillbill.ports.review.evidence.ReviewSnapshotGateway
import skillbill.ports.review.preparation.ReviewInputSource
import skillbill.ports.taskruntime.FeatureTaskRuntimeSharedEvidenceLocatorReadPort
import skillbill.ports.taskruntime.FeatureTaskRuntimeSharedEvidenceResolverPort
import skillbill.review.context.ReviewContextEnvelopeValidator
import skillbill.review.model.ParallelReviewParseResult
import skillbill.review.parallel.ParallelReviewFindingParser

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
  fun repositoryOriginScopeKeyPort(adapter: GitRepositoryOriginScopeKey): RepositoryOriginScopeKeyPort = adapter

  @Provides @JvmSynthetic
  fun parallelReviewParseRegister(): (String) -> ParallelReviewParseResult = ParallelReviewFindingParser::parse
}
