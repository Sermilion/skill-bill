package skillbill.engine.featuretask.slot.codereview

import skillbill.application.review.model.ParallelCodeReviewResult
import skillbill.application.review.model.ParallelReviewLaneStatus
import skillbill.engine.featuretask.slot.PhaseLaunchFailureKind
import skillbill.engine.featuretask.slot.PhaseStepOutput
import skillbill.ports.agentrun.model.AgentRunTermination
import skillbill.review.model.ParallelReviewLaneResult
import skillbill.review.model.ParallelReviewMergeResult
import skillbill.review.model.ReviewLaneReviewDisposition
import skillbill.review.parallel.ParallelReviewFindingParser
import skillbill.review.parallel.ParallelReviewMerger

internal object InlineReviewResultDecoder {
  fun decode(
    agentId: String,
    output: PhaseStepOutput,
  ): ParallelCodeReviewResult {
    val unsupported = output.launchFailure?.takeIf { it.kind == PhaseLaunchFailureKind.UNSUPPORTED_AGENT }
    return unsupported?.let { failedResult(agentId, it.cause) } ?: launchedResult(agentId, output)
  }

  fun failedLaneReason(result: ParallelCodeReviewResult): String? {
    val parent = result.lane1
    if (parent.agentId.isBlank() || parent.success) return null
    val detail = parent.failureReason?.takeIf(String::isNotBlank) ?: "lane failed"
    return "Feature-task-runtime phase 'review' $detail"
  }

  private fun launchedResult(
    agentId: String,
    output: PhaseStepOutput,
  ): ParallelCodeReviewResult {
    launchFailureReason(output)?.let { reason -> return failedResult(agentId, reason) }
    val stdout = output.stdout.text
    val parsed = ParallelReviewFindingParser.parse(stdout)
    val merged =
      ParallelReviewMerger.merge(
        ParallelReviewLaneResult(agentId = agentId, findings = parsed.findings),
        ParallelReviewLaneResult(agentId = agentId, findings = emptyList()),
      )
    return ParallelCodeReviewResult(
      mergeResult = merged.copy(formattedOutput = stdout.ifBlank { "Review completed." }),
      lane1 =
        ParallelReviewLaneStatus(
          agentId = agentId,
          success = true,
          droppedCandidateDiagnostic = droppedCandidateDiagnostic(parsed.rejections.size, parsed.candidateCount),
          reviewDisposition =
            if (stdout.isBlank()) {
              ReviewLaneReviewDisposition.INCOMPLETE
            } else {
              ReviewLaneReviewDisposition.COMPLETE
            },
        ),
    )
  }

  private fun failedResult(
    agentId: String,
    reason: String,
  ) = ParallelCodeReviewResult(
    mergeResult = ParallelReviewMergeResult(findings = emptyList(), formattedOutput = ""),
    lane1 = ParallelReviewLaneStatus(agentId = agentId, success = false, failureReason = reason),
  )

  private fun launchFailureReason(output: PhaseStepOutput): String? =
    when (val termination = output.termination) {
      null -> "agent process failed to spawn"
      AgentRunTermination.TimedOut -> "agent timed out"
      AgentRunTermination.SpawnFailed -> "agent process failed to spawn"
      AgentRunTermination.Interrupted -> "agent was interrupted"
      is AgentRunTermination.Exited ->
        when {
          termination.code != 0 -> "agent exited with status ${termination.code}"
          output.stdout.truncated -> "agent output exceeded the retention cap before completion"
          else -> null
        }
    }

  private fun droppedCandidateDiagnostic(
    rejected: Int,
    candidateCount: Int,
  ): String? =
    if (rejected == 0) {
      null
    } else {
      "dropped $rejected of $candidateCount [F-XXX] candidate line(s)"
    }
}
