package skillbill.application.review.parallel.core.code.review.runner
import me.tatarka.inject.annotations.Inject
import skillbill.application.idestatus.AgentActivityStampWriter
import skillbill.application.review.parallel.core.code.review.runner.model.ParallelCodeReviewRunnerBoundaries
import skillbill.application.review.parallel.planning.ParallelCodeReviewRunnerPlanning
import skillbill.application.review.parallel.planning.ParallelCodeReviewRunnerRubricPlanning
import skillbill.application.review.parallel.verification.ParallelCodeReviewRunnerFailureAdmission
import skillbill.application.review.parallel.verification.ParallelCodeReviewRunnerLanePlanRecording
import skillbill.application.review.parallel.verification.ParallelCodeReviewRunnerVerificationStages
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
  private val rubricPlanning =
    ParallelCodeReviewRunnerRubricPlanning(
      boundaries.reviewRubricResolver,
      boundaries.installedPackCatalog,
    )
  internal val planning =
    ParallelCodeReviewRunnerPlanning(
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
  internal val laneLaunch =
    ParallelCodeReviewRunnerLaneLaunch(
      parentReviewLauncher = boundaries.parentReviewLauncher,
      reviewEvidenceBrokerFactory = boundaries.reviewEvidenceBrokerFactory,
      governedEvidenceEndpointBinder = boundaries.governedEvidenceEndpointBinder,
      reviewLaunchAgentStaging = boundaries.reviewLaunchAgentStaging,
      sharedEvidenceLocatorReader = boundaries.sharedEvidenceLocatorReader,
      failureAdmission = failureAdmission,
      activityStampWriter = activityStampWriter,
    )
  internal val resultAssembly =
    ParallelCodeReviewRunnerResultAssembly(
      boundaries.parentReviewLauncher,
      boundaries.reviewContextEnvelopeValidator,
      runtimeOwnedPersistence,
      boundaries.clock,
    )
  internal val verificationStages =
    ParallelCodeReviewRunnerVerificationStages(
      boundaries.parentReviewLauncher,
      boundaries.reviewContextEnvelopeValidator,
      runtimeOwnedPersistence,
      boundaries.clock,
    )
}
