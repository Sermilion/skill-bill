package skillbill.application.review.parallel.core.code.review.stage
import skillbill.application.review.packet.index
import skillbill.application.review.parallel.core.code.review.bundled.review
import skillbill.application.review.parallel.core.code.review.claim.CONFIRMED
import skillbill.application.review.parallel.core.code.review.end.reviewRunId
import skillbill.application.review.parallel.core.code.review.end.run
import skillbill.application.review.parallel.core.code.review.evidence.index
import skillbill.application.review.parallel.core.code.review.inline.run
import skillbill.application.review.parallel.core.code.review.pass.index
import skillbill.application.review.parallel.core.code.review.runner.manifests
import skillbill.application.review.parallel.core.code.review.runner.reviewRunId
import skillbill.application.review.parallel.core.code.review.runner.run
import skillbill.application.review.parallel.core.code.review.runner.stageResume
import skillbill.application.review.parallel.core.code.review.spec.CONFIRMED
import skillbill.application.review.parallel.core.review.diff
import skillbill.application.review.parallel.core.review.manifests
import skillbill.application.review.parallel.core.review.run
import skillbill.application.review.parallel.planning.diff
import skillbill.application.review.parallel.planning.manifests
import skillbill.application.review.parallel.planning.reviewRunId
import skillbill.application.review.parallel.verification.path
import skillbill.application.review.parallel.verification.reviewRunId
import skillbill.application.review.preparation.commits
import skillbill.application.review.preparation.reached
import skillbill.application.review.review.HARNESS_HEAD_REVISION
import skillbill.application.review.review.RecordedCommit
import skillbill.application.review.review.RecordedWorkerResponse
import skillbill.application.review.review.ReviewHarnessConfig
import skillbill.application.review.review.ReviewRecorder
import skillbill.application.review.review.commits
import skillbill.application.review.review.diff
import skillbill.application.review.review.diffForPaths
import skillbill.application.review.review.durableFindingVerdicts
import skillbill.application.review.review.durableStageBoundaries
import skillbill.application.review.review.harnessRequest
import skillbill.application.review.review.manifests
import skillbill.application.review.review.parentLaunches
import skillbill.application.review.review.response
import skillbill.application.review.review.reviewHarness
import skillbill.application.review.review.sparseReviewPack
import skillbill.application.review.service.review
import skillbill.application.review.spec.diff
import skillbill.application.review.spec.issueKey
import skillbill.application.review.spec.path
import skillbill.application.review.spec.recordedAt
import skillbill.application.review.spec.run
import skillbill.application.review.verification.CONFIRMED
import skillbill.application.review.verification.path
import skillbill.application.review.verification.recordedAt
import skillbill.application.review.verification.run
import skillbill.ports.goalrunner.runner.model.GoalRunnerSubtaskLaunchRequest
import skillbill.review.context.model.launch.CodeReviewExecutionMode
import skillbill.review.model.ReviewClaimVerdict
import skillbill.review.model.ReviewFindingCitation
import skillbill.review.model.ReviewFindingVerdict
import skillbill.review.model.ReviewStage
import skillbill.review.model.ReviewStageBoundary
import skillbill.review.model.ReviewStageReached
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ParallelCodeReviewStageResumeTest {
  private val pack = sparseReviewPack(
    slug = "kotlin",
    requiredArea = "architecture",
    pathAreas = mapOf("testing" to listOf("src/test/")),
  )

  @Test
  fun `a completed review pass records verification and resumes into adjudication without relaunching lanes`() {
    val recorder = ReviewRecorder()
    val config = delegatedConfig()
    reviewHarness(config, recorder).run(delegatedRequest())
    assertTrue(
      recorder.durableStageBoundaries.any {
        it.stage == ReviewStage.REVIEW && it.reached == ReviewStageReached.REACHED
      },
    )
    assertTrue(
      recorder.durableStageBoundaries.any {
        it.stage == ReviewStage.VERIFICATION && it.reached == ReviewStageReached.REACHED
      },
    )
    val afterFirst = recorder.specialistLaunches.size
    assertTrue(afterFirst > 0)

    val resumed = reviewHarness(config, recorder).run(delegatedRequest())
    assertEquals(
      afterFirst,
      recorder.specialistLaunches.size,
      "A resume after a recorded review boundary must not re-launch specialist lanes.",
    )
    val resume = assertNotNull(resumed.stageResume)
    assertTrue(resume.holdsDurableResult(ReviewStage.REVIEW))
    assertTrue(resume.holdsDurableResult(ReviewStage.VERIFICATION))
    assertEquals(ReviewStage.ADJUDICATION, resume.reentryStage)
  }

  @Test
  fun `a run holding verification results resumes into adjudication with verdicts retained`() {
    val recorder = ReviewRecorder()
    val config = delegatedConfig()
    reviewHarness(config, recorder).run(delegatedRequest())
    val verdict = ReviewFindingVerdict(
      stage = ReviewStage.VERIFICATION,
      findingRef = "F-001",
      claimVerdict = ReviewClaimVerdict.CONFIRMED,
      citations = listOf(ReviewFindingCitation("src/Main.kt", 1)),
      recordedAt = "2026-08-14T08:00:00Z",
    )
    recorder.durableStageBoundaries += ReviewStageBoundary(
      ReviewStage.VERIFICATION,
      ReviewStageReached.REACHED,
      "2026-08-14T08:01:00Z",
    )
    recorder.durableFindingVerdicts += verdict

    val resumed = reviewHarness(config, recorder).run(delegatedRequest())
    val resume = assertNotNull(resumed.stageResume)
    assertEquals(ReviewStage.ADJUDICATION, resume.reentryStage)
    assertEquals(listOf(verdict), recorder.durableFindingVerdicts.toList())
  }

  @Test
  fun `no resolvable spec records a closed none reason and skips stage 2`() {
    val recorder = ReviewRecorder()
    reviewHarness(delegatedConfig(), recorder).run(delegatedRequest())
    assertEquals("not_applicable_scope", recorder.durableSpecProjection?.absenceReason)
    assertTrue(
      recorder.durableStageBoundaries.any {
        it.stage == ReviewStage.ADJUDICATION && it.reached == ReviewStageReached.NOT_REACHED
      },
    )
  }

  private fun delegatedRequest() = harnessRequest(
    reviewRunId = RUN_ID,
    codeReviewMode = CodeReviewExecutionMode.DELEGATED,
  )

  private fun delegatedConfig(): ReviewHarnessConfig {
    val paths = listOf("src/Main.kt", "src/test/AppTest.kt")
    val shas = paths.indices.map { index ->
      if (index == paths.lastIndex) HARNESS_HEAD_REVISION else "c$index"
    }
    return ReviewHarnessConfig(
      manifests = listOf(pack),
      diff = diffForPaths(*paths.toTypedArray()),
      response = { RecordedWorkerResponse() },
      commits = paths.mapIndexed { index, path ->
        RecordedCommit(shas[index], "commit touching $path", diffForPaths(path))
      },
    )
  }

  private companion object {
    const val RUN_ID = "review-run-stage-resume"
  }
}

private val ReviewRecorder.specialistLaunches: List<GoalRunnerSubtaskLaunchRequest>
  get() = parentLaunches.filter { it.skillRunRequest.issueKey == "code-review" }
