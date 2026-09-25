package skillbill.di.review

import me.tatarka.inject.annotations.Provides
import skillbill.infrastructure.contracts.review.ReviewContextSchemaValidator
import skillbill.infrastructure.launcher.review.UnixSocketGovernedReviewEvidenceEndpointBinder
import skillbill.infrastructure.workflow.featuretask.FileSystemFeatureTaskRuntimeSharedEvidenceStore
import skillbill.infrastructure.workflow.filesystem.FileSystemDiffResolver
import skillbill.infrastructure.workflow.git.standard.GitRepositoryOriginScopeKey
import skillbill.infrastructure.workflow.review.broker.FileSystemReviewEvidenceBroker
import skillbill.infrastructure.workflow.review.specialists.FileSystemReviewInputSource
import skillbill.infrastructure.workflow.review.specialists.FileSystemReviewSnapshotGateway
import skillbill.ports.diff.DiffResolverPort
import skillbill.ports.repository.RepositoryOriginScopeKeyPort
import skillbill.ports.review.ReviewContextEnvelopeValidator
import skillbill.ports.review.evidence.GovernedReviewEvidenceEndpointBinder
import skillbill.ports.review.evidence.ReviewEvidenceBrokerFactory
import skillbill.ports.review.evidence.ReviewSnapshotGateway
import skillbill.ports.review.preparation.ReviewInputSource
import skillbill.ports.taskruntime.FeatureTaskRuntimeSharedEvidenceLocatorReadPort
import skillbill.ports.taskruntime.FeatureTaskRuntimeSharedEvidenceResolverPort
import skillbill.review.model.ParallelReviewParseResult
import skillbill.review.parallel.ParallelReviewFindingParser

internal interface RuntimeReviewEvidenceProvides {
  @Provides
  fun reviewContextEnvelopeValidator(validator: ReviewContextSchemaValidator): ReviewContextEnvelopeValidator =
    validator

  @Provides
  fun reviewEvidenceBrokerFactory(): ReviewEvidenceBrokerFactory =
    ReviewEvidenceBrokerFactory { binding -> FileSystemReviewEvidenceBroker(binding) }

  @Provides
  fun governedReviewEvidenceEndpointBinder(
    adapter: UnixSocketGovernedReviewEvidenceEndpointBinder,
  ): GovernedReviewEvidenceEndpointBinder = adapter

  @Provides
  fun sharedEvidenceResolverPort(
    adapter: FileSystemFeatureTaskRuntimeSharedEvidenceStore,
  ): FeatureTaskRuntimeSharedEvidenceResolverPort = adapter

  @Provides
  fun sharedEvidenceLocatorReadPort(
    adapter: FileSystemFeatureTaskRuntimeSharedEvidenceStore,
  ): FeatureTaskRuntimeSharedEvidenceLocatorReadPort = adapter

  /** Review runners accept an absent reader; production always binds the filesystem store. */
  @Provides
  fun optionalSharedEvidenceLocatorReadPort(
    port: FeatureTaskRuntimeSharedEvidenceLocatorReadPort,
  ): FeatureTaskRuntimeSharedEvidenceLocatorReadPort? = port

  @Provides
  fun reviewSnapshotGateway(gateway: FileSystemReviewSnapshotGateway): ReviewSnapshotGateway = gateway

  @Provides
  fun reviewInputSource(source: FileSystemReviewInputSource): ReviewInputSource = source

  @Provides
  fun diffResolverPort(adapter: FileSystemDiffResolver): DiffResolverPort = adapter

  @Provides
  fun repositoryOriginScopeKeyPort(adapter: GitRepositoryOriginScopeKey): RepositoryOriginScopeKeyPort = adapter

  @Provides
  fun parallelReviewParseRegister(): (String) -> ParallelReviewParseResult = ParallelReviewFindingParser::parse
}
