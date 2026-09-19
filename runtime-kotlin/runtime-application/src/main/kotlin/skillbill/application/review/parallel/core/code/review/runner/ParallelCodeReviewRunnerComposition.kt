package skillbill.application.review.parallel.core.code.review.runner
import me.tatarka.inject.annotations.Inject
import skillbill.application.idestatus.AgentActivityStampWriter
import skillbill.application.review.parallel.core.code.review.bundled.review
import skillbill.application.review.parallel.core.code.review.end.runner
import skillbill.application.review.parallel.core.code.review.inline.governedEvidenceEndpointBinder
import skillbill.application.review.parallel.core.code.review.inline.parentReviewLauncher
import skillbill.application.review.parallel.core.code.review.regression.runner
import skillbill.application.review.parallel.planning.ParallelCodeReviewRunnerPlanning
import skillbill.application.review.parallel.planning.ParallelCodeReviewRunnerRubricPlanning
import skillbill.application.review.parallel.planning.diffResolver
import skillbill.application.review.parallel.planning.installedPackCatalog
import skillbill.application.review.parallel.planning.lanePlanRecording
import skillbill.application.review.parallel.planning.repoLocalConfig
import skillbill.application.review.parallel.planning.repositoryEnclosingRootPort
import skillbill.application.review.parallel.planning.reviewContextEnvelopeValidator
import skillbill.application.review.parallel.planning.reviewRubricResolver
import skillbill.application.review.parallel.planning.reviewSpecialistContractProvider
import skillbill.application.review.parallel.planning.sharedEvidenceLocatorReader
import skillbill.application.review.parallel.planning.sharedEvidenceResolver
import skillbill.application.review.parallel.planning.specIntentProjectionResolver
import skillbill.application.review.parallel.verification.ParallelCodeReviewRunnerFailureAdmission
import skillbill.application.review.parallel.verification.ParallelCodeReviewRunnerLanePlanRecording
import skillbill.application.review.parallel.verification.ParallelCodeReviewRunnerVerificationStages
import skillbill.application.review.parallel.verification.boundaries
import skillbill.application.review.parallel.verification.clock
import skillbill.application.review.parallel.verification.parentReviewLauncher
import skillbill.application.review.parallel.verification.registerParse
import skillbill.application.review.parallel.verification.reviewContextEnvelopeValidator
import skillbill.application.review.review.boundaries
import skillbill.application.review.review.database
import skillbill.application.review.review.diagnostics
import skillbill.application.review.review.sharedEvidenceLocatorReader
import skillbill.application.review.service.database
import skillbill.application.review.service.diagnostics
import skillbill.application.review.service.review
import skillbill.application.review.spec.clock
import skillbill.application.review.spec.diagnostics
import skillbill.application.review.spec.runner
import skillbill.application.review.verification.clock
import skillbill.application.review.verification.diagnostics
import skillbill.application.review.verification.runner
import skillbill.application.runtimepersistence.RuntimeOwnedPersistenceBoundary
import skillbill.ports.review.launch.ReviewNativeAgentPreflightPort

@Inject
class ParallelCodeReviewRunnerComposition(
  boundaries: ParallelCodeReviewRunnerBoundaries,
  activityStampWriter: AgentActivityStampWriter,
) {
  internal val runtimeOwnedPersistence = RuntimeOwnedPersistenceBoundary(boundaries.database, boundaries.diagnostics)
  private val failureAdmission = ParallelCodeReviewRunnerFailureAdmission(boundaries.registerParse)
  internal val nativeAgentPreflight: ReviewNativeAgentPreflightPort = boundaries.nativeAgentPreflight
  private val rubricPlanning = ParallelCodeReviewRunnerRubricPlanning(
    boundaries.reviewRubricResolver,
    boundaries.installedPackCatalog,
  )
  internal val planning = ParallelCodeReviewRunnerPlanning(
    diffResolver = boundaries.diffResolver,
    repoLocalConfig = boundaries.repoLocalConfig,
    reviewContextEnvelopeValidator = boundaries.reviewContextEnvelopeValidator,
    reviewSpecialistContractProvider = boundaries.reviewSpecialistContractProvider,
    installedPackCatalog = boundaries.installedPackCatalog,
    sharedEvidenceResolver = boundaries.sharedEvidenceResolver,
    sharedEvidenceLocatorReader = boundaries.sharedEvidenceLocatorReader,
    specIntentProjectionResolver = boundaries.specIntentProjectionResolver,
    rubricPlanning = rubricPlanning,
    lanePlanRecording = ParallelCodeReviewRunnerLanePlanRecording(runtimeOwnedPersistence, boundaries.clock),
    repositoryEnclosingRootPort = boundaries.repositoryEnclosingRootPort,
  )
  internal val laneLaunch = ParallelCodeReviewRunnerLaneLaunch(
    parentReviewLauncher = boundaries.parentReviewLauncher,
    reviewEvidenceBrokerFactory = boundaries.reviewEvidenceBrokerFactory,
    governedEvidenceEndpointBinder = boundaries.governedEvidenceEndpointBinder,
    reviewLaunchAgentStaging = boundaries.reviewLaunchAgentStaging,
    sharedEvidenceLocatorReader = boundaries.sharedEvidenceLocatorReader,
    failureAdmission = failureAdmission,
    activityStampWriter = activityStampWriter,
  )
  internal val resultAssembly = ParallelCodeReviewRunnerResultAssembly(
    boundaries.parentReviewLauncher,
    boundaries.reviewContextEnvelopeValidator,
    runtimeOwnedPersistence,
    boundaries.clock,
  )
  internal val verificationStages = ParallelCodeReviewRunnerVerificationStages(
    boundaries.parentReviewLauncher,
    boundaries.reviewContextEnvelopeValidator,
    runtimeOwnedPersistence,
    boundaries.clock,
  )
}
