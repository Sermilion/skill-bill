package skillbill.application.review.parallel.core.code.review.runner

import me.tatarka.inject.annotations.Inject
import skillbill.application.review.spec.SpecIntentProjectionResolver
import skillbill.ports.config.RepoLocalConfigPort
import skillbill.ports.db.DatabaseSessionFactory
import skillbill.ports.diagnostics.RuntimeDiagnostics
import skillbill.ports.diff.DiffResolverPort
import skillbill.ports.goalrunner.runner.GoalRunnerSubtaskLauncher
import skillbill.ports.repository.RepositoryEnclosingRootPort
import skillbill.ports.review.evidence.GovernedReviewEvidenceEndpointBinder
import skillbill.ports.review.evidence.ReviewEvidenceBrokerFactory
import skillbill.ports.review.launch.ReviewLaunchAgentStagingPort
import skillbill.ports.review.launch.ReviewNativeAgentPreflightPort
import skillbill.ports.review.preparation.ReviewRubricResolver
import skillbill.ports.review.repository.ReviewSpecialistContractProvider
import skillbill.ports.scaffold.install.InstalledPlatformPackCatalogPort
import skillbill.ports.taskruntime.FeatureTaskRuntimeSharedEvidenceLocatorReadPort
import skillbill.ports.taskruntime.FeatureTaskRuntimeSharedEvidenceResolverPort
import skillbill.review.context.ReviewContextEnvelopeValidator
import skillbill.review.model.ParallelReviewParseResult
import java.time.Clock

@Inject
class ParallelCodeReviewRunnerBoundaries(
  val diffResolver: DiffResolverPort,
  val repoLocalConfig: RepoLocalConfigPort,
  val reviewContextEnvelopeValidator: ReviewContextEnvelopeValidator,
  val reviewRubricResolver: ReviewRubricResolver,
  val reviewSpecialistContractProvider: ReviewSpecialistContractProvider,
  val database: DatabaseSessionFactory,
  val installedPackCatalog: InstalledPlatformPackCatalogPort,
  val sharedEvidenceResolver: FeatureTaskRuntimeSharedEvidenceResolverPort,
  val sharedEvidenceLocatorReader: FeatureTaskRuntimeSharedEvidenceLocatorReadPort,
  val specIntentProjectionResolver: SpecIntentProjectionResolver,
  val parentReviewLauncher: GoalRunnerSubtaskLauncher,
  val nativeAgentPreflight: ReviewNativeAgentPreflightPort,
  val registerParse: (String) -> ParallelReviewParseResult,
  val diagnostics: RuntimeDiagnostics,
  val clock: Clock,
  val repositoryEnclosingRootPort: RepositoryEnclosingRootPort,
  val reviewEvidenceBrokerFactory: ReviewEvidenceBrokerFactory,
  val governedEvidenceEndpointBinder: GovernedReviewEvidenceEndpointBinder,
  val reviewLaunchAgentStaging: ReviewLaunchAgentStagingPort,
)
