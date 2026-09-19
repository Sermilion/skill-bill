package skillbill.engine.featuretask.lifecycle.core


import skillbill.engine.featuretask.review.core.FeatureTaskRuntimeReviewDriver
import skillbill.application.review.model.ParallelCodeReviewRequest
import skillbill.application.review.model.ParallelCodeReviewResult
import skillbill.application.review.model.ParallelReviewLaneStatus
import skillbill.review.context.model.ReviewLaneReviewDisposition
import skillbill.review.model.ParallelReviewMergeResult

object ApprovingReviewDriverStub : FeatureTaskRuntimeReviewDriver {
  override fun run(request: ParallelCodeReviewRequest): ParallelCodeReviewResult = ParallelCodeReviewResult(
    mergeResult = ParallelReviewMergeResult(
      findings = emptyList(),
      formattedOutput = "verdict: approved",
    ),
    lane1 = ParallelReviewLaneStatus(
      agentId = request.agent1Id,
      success = true,
      reviewDisposition = ReviewLaneReviewDisposition.COMPLETE,
    ),
  )
}
