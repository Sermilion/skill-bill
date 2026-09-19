package skillbill.di.review
import me.tatarka.inject.annotations.Provides
import skillbill.infrastructure.launcher.AgentRunReviewIsolationResolver
import skillbill.infrastructure.launcher.FileSystemReviewLaunchAgentStaging
import skillbill.infrastructure.workflow.review.specialists.review.ClasspathReviewSpecialistContractProvider
import skillbill.infrastructure.workflow.review.specialists.system.FileSystemReviewAttribution
import skillbill.infrastructure.workflow.review.specialists.system.FileSystemReviewNativeAgentPreflight
import skillbill.infrastructure.workflow.review.specialists.system.FileSystemReviewRubricResolver
import skillbill.model.OptionalCallbacks
import skillbill.ports.review.launch.ReviewLaunchAgentStagingPort
import skillbill.ports.review.launch.ReviewLaunchIsolationResolver
import skillbill.ports.review.launch.ReviewNativeAgentPreflightPort
import skillbill.ports.review.preparation.ReviewAttributionPort
import skillbill.ports.review.preparation.ReviewRubricResolver
import skillbill.ports.review.repository.ReviewSpecialistContractProvider

internal interface RuntimeReviewLaunchProvides {
  @Provides @JvmSynthetic
  fun reviewAttributionPort(adapter: FileSystemReviewAttribution): ReviewAttributionPort = adapter

  @Provides @JvmSynthetic
  fun reviewRubricResolver(adapter: FileSystemReviewRubricResolver): ReviewRubricResolver = adapter

  @Provides @JvmSynthetic
  fun reviewSpecialistContractProvider(
    adapter: ClasspathReviewSpecialistContractProvider,
  ): ReviewSpecialistContractProvider = adapter

  @Provides @JvmSynthetic
  fun reviewNativeAgentPreflightPort(
    callbacks: OptionalCallbacks,
    adapter: FileSystemReviewNativeAgentPreflight,
  ): ReviewNativeAgentPreflightPort = callbacks.reviewNativeAgentPreflight ?: adapter

  @Provides @JvmSynthetic
  fun reviewLaunchAgentStagingPort(adapter: FileSystemReviewLaunchAgentStaging): ReviewLaunchAgentStagingPort = adapter

  @Provides @JvmSynthetic
  fun reviewLaunchIsolationResolver(adapter: AgentRunReviewIsolationResolver): ReviewLaunchIsolationResolver = adapter
}
