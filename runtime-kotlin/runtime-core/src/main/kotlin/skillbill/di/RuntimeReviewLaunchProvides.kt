package skillbill.di

import me.tatarka.inject.annotations.Provides
import skillbill.infrastructure.launcher.AgentRunReviewIsolationResolver
import skillbill.infrastructure.launcher.FileSystemReviewLaunchAgentStaging
import skillbill.infrastructure.workflow.ClasspathReviewSpecialistContractProvider
import skillbill.infrastructure.workflow.FileSystemReviewAttribution
import skillbill.infrastructure.workflow.FileSystemReviewNativeAgentPreflight
import skillbill.infrastructure.workflow.FileSystemReviewRubricResolver
import skillbill.model.OptionalCallbacks
import skillbill.ports.review.ReviewAttributionPort
import skillbill.ports.review.ReviewLaunchAgentStagingPort
import skillbill.ports.review.ReviewLaunchIsolationResolver
import skillbill.ports.review.ReviewNativeAgentPreflightPort
import skillbill.ports.review.ReviewRubricResolver
import skillbill.ports.review.ReviewSpecialistContractProvider

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
