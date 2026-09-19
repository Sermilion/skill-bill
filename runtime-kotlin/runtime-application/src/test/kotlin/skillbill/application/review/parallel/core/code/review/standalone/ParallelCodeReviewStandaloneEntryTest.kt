package skillbill.application.review.parallel.core.code.review.standalone
import skillbill.application.review.parallel.core.code.review.bundled.review
import skillbill.application.review.parallel.core.code.review.claim.ReviewRecorder
import skillbill.application.review.parallel.core.code.review.claim.findings
import skillbill.application.review.parallel.core.code.review.end.lanes
import skillbill.application.review.parallel.core.code.review.end.reviewRunId
import skillbill.application.review.parallel.core.code.review.end.run
import skillbill.application.review.parallel.core.code.review.inline.findings
import skillbill.application.review.parallel.core.code.review.inline.run
import skillbill.application.review.parallel.core.code.review.integration.ReviewRecorder
import skillbill.application.review.parallel.core.code.review.regression.lanes
import skillbill.application.review.parallel.core.code.review.runner.findings
import skillbill.application.review.parallel.core.code.review.runner.lanes
import skillbill.application.review.parallel.core.code.review.runner.manifests
import skillbill.application.review.parallel.core.code.review.runner.request
import skillbill.application.review.parallel.core.code.review.runner.reviewRunId
import skillbill.application.review.parallel.core.code.review.runner.run
import skillbill.application.review.parallel.core.code.review.spec.ReviewRecorder
import skillbill.application.review.parallel.core.code.review.spec.request
import skillbill.application.review.parallel.core.code.review.stage.ReviewRecorder
import skillbill.application.review.parallel.core.review.diff
import skillbill.application.review.parallel.core.review.lanes
import skillbill.application.review.parallel.core.review.manifests
import skillbill.application.review.parallel.core.review.run
import skillbill.application.review.parallel.planning.diff
import skillbill.application.review.parallel.planning.lane
import skillbill.application.review.parallel.planning.lanes
import skillbill.application.review.parallel.planning.manifests
import skillbill.application.review.parallel.planning.request
import skillbill.application.review.parallel.planning.reviewRunId
import skillbill.application.review.parallel.verification.lane
import skillbill.application.review.parallel.verification.reviewRunId
import skillbill.application.review.preparation.request
import skillbill.application.review.review.RecordedWorkerResponse
import skillbill.application.review.review.ReviewHarnessConfig
import skillbill.application.review.review.ReviewRecorder
import skillbill.application.review.review.diff
import skillbill.application.review.review.diffForPaths
import skillbill.application.review.review.harnessRequest
import skillbill.application.review.review.lane
import skillbill.application.review.review.lanes
import skillbill.application.review.review.manifests
import skillbill.application.review.review.parentLaunches
import skillbill.application.review.review.response
import skillbill.application.review.review.reviewHarness
import skillbill.application.review.review.sparseReviewPack
import skillbill.application.review.review.stdout
import skillbill.application.review.service.review
import skillbill.application.review.spec.diff
import skillbill.application.review.spec.findings
import skillbill.application.review.spec.issueKey
import skillbill.application.review.spec.lane
import skillbill.application.review.spec.lanes
import skillbill.application.review.spec.none
import skillbill.application.review.spec.run
import skillbill.application.review.spec.stdout
import skillbill.application.review.stats.lane
import skillbill.application.review.verification.findings
import skillbill.application.review.verification.lanes
import skillbill.application.review.verification.run
import skillbill.application.review.verification.stdout
import skillbill.review.context.model.launch.CodeReviewExecutionMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ParallelCodeReviewStandaloneEntryTest {
  @Test
  fun `single parent lane keeps parent prose without a findings register`() {
    val pack = sparseReviewPack(
      slug = "kotlin",
      requiredArea = "architecture",
      pathAreas = mapOf("testing" to listOf("src/test/")),
    )
    val recorder = ReviewRecorder()
    val prose = "Null is unchecked in Main.\nverdict: changes_requested"
    val result = reviewHarness(
      ReviewHarnessConfig(
        manifests = listOf(pack),
        diff = diffForPaths("src/Main.kt"),
        response = { request ->
          when (request.skillRunRequest.issueKey) {
            "code-review" -> RecordedWorkerResponse(stdout = prose)
            else -> RecordedWorkerResponse()
          }
        },
      ),
      recorder,
    ).run(
      harnessRequest(
        reviewRunId = "standalone-single-lane",
        codeReviewMode = CodeReviewExecutionMode.INLINE,
      ),
    )

    assertEquals(
      1,
      recorder.parentLaunches.count { it.skillRunRequest.issueKey == "code-review" },
    )
    assertTrue(result.mergeResult.findings.isEmpty())
    assertEquals(prose, result.mergeResult.formattedOutput)
    result.accountingSummary?.lanes?.let { lanes ->
      assertTrue(lanes.none { it.lane == "parallel-agent-2" })
    }
  }
}
