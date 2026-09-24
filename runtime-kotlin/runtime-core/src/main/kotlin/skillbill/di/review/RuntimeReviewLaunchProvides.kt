package skillbill.di.review
import me.tatarka.inject.annotations.Provides
import skillbill.infrastructure.launcher.AgentRunReviewIsolationResolver
import skillbill.infrastructure.launcher.FileSystemReviewLaunchAgentStaging
import skillbill.infrastructure.workflow.review.specialists.review.ClasspathReviewSpecialistContractProvider
import skillbill.infrastructure.workflow.review.specialists.system.FileSystemReviewAttribution
import skillbill.infrastructure.workflow.review.specialists.system.FileSystemReviewNativeAgentPreflight
import skillbill.infrastructure.workflow.review.specialists.system.FileSystemReviewRubricResolver
import skillbill.ports.review.launch.ReviewLaunchAgentStagingPort
import skillbill.ports.review.launch.ReviewLaunchIsolationResolver
import skillbill.ports.review.launch.ReviewNativeAgentPreflightPort
import skillbill.ports.review.preparation.ReviewAttributionPort
import skillbill.ports.review.preparation.ReviewRubricResolver
import skillbill.ports.review.repository.ReviewSpecialistContractProvider

internal interface RuntimeReviewLaunchProvides {
  @Provides
  fun reviewAttributionPort(adapter: FileSystemReviewAttribution): ReviewAttributionPort = adapter

  @Provides
  fun reviewRubricResolver(adapter: FileSystemReviewRubricResolver): ReviewRubricResolver = adapter

  @Provides
  fun reviewSpecialistContractProvider(
    adapter: ClasspathReviewSpecialistContractProvider,
  ): ReviewSpecialistContractProvider = adapter

  @Provides
  fun reviewNativeAgentPreflightPort(
    adapter: FileSystemReviewNativeAgentPreflight,
  ): ReviewNativeAgentPreflightPort = adapter

  @Provides
  fun reviewLaunchAgentStagingPort(adapter: FileSystemReviewLaunchAgentStaging): ReviewLaunchAgentStagingPort = adapter

  @Provides
  fun reviewLaunchIsolationResolver(adapter: AgentRunReviewIsolationResolver): ReviewLaunchIsolationResolver = adapter
}
