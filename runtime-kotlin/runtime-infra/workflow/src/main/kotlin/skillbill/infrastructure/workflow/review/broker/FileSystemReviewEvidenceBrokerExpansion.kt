package skillbill.infrastructure.workflow.review.broker
import skillbill.infrastructure.contracts.sha256Hex
import skillbill.infrastructure.workflow.decomposition.result
import skillbill.infrastructure.workflow.decomposition.target
import skillbill.infrastructure.workflow.featuretask.outcome
import skillbill.infrastructure.workflow.filesystem.path
import skillbill.infrastructure.workflow.filesystem.result
import skillbill.infrastructure.workflow.filesystem.target
import skillbill.infrastructure.workflow.git.checkpoint.result
import skillbill.infrastructure.workflow.git.checkpoint.target
import skillbill.infrastructure.workflow.git.goal.reason
import skillbill.infrastructure.workflow.git.goal.result
import skillbill.infrastructure.workflow.git.local.result
import skillbill.infrastructure.workflow.git.scoped.result
import skillbill.infrastructure.workflow.git.scoped.target
import skillbill.infrastructure.workflow.git.standard.outcome
import skillbill.infrastructure.workflow.git.standard.path
import skillbill.infrastructure.workflow.git.standard.result
import skillbill.infrastructure.workflow.git.suppression.path
import skillbill.infrastructure.workflow.git.workflow.path
import skillbill.infrastructure.workflow.git.workflow.result
import skillbill.infrastructure.workflow.git.workflow.results
import skillbill.infrastructure.workflow.review.specialists.review.result
import skillbill.infrastructure.workflow.review.specialists.system.path
import skillbill.infrastructure.workflow.review.specialists.system.target
import skillbill.infrastructure.workflow.runtime.target
import skillbill.ports.review.model.ReviewEvidenceBatchResult
import skillbill.ports.review.model.ReviewEvidenceResult
import skillbill.ports.review.model.ReviewRefusedOperationRecord
import skillbill.review.context.model.hunk.ReviewBudgetOutcome
import skillbill.review.context.model.packet.ReviewExpansionRecord
import java.nio.charset.StandardCharsets

private const val EXPANSION_ID_HEX_LENGTH = 24

internal fun stableReviewExpansionId(assignmentDigest: String, path: String, reason: String): String {
  val input = "$assignmentDigest\u0000$path\u0000$reason".toByteArray(StandardCharsets.UTF_8)
  val digest = sha256Hex(input)
  return "exp-${digest.take(EXPANSION_ID_HEX_LENGTH)}"
}

internal fun reviewEvidenceBatchResult(
  results: List<ReviewEvidenceResult>,
  outcome: ReviewBudgetOutcome?,
  cumulativeBytes: Long,
  expansionLedger: List<ReviewExpansionRecord>,
  refusalLedger: MutableList<ReviewRefusedOperationRecord>,
): ReviewEvidenceBatchResult {
  results.forEach { result ->
    result.forbidden?.let { refusalLedger += ReviewRefusedOperationRecord(it.category, it.target) }
    result.budgetExceeded?.let { refusalLedger += ReviewRefusedOperationRecord(it.type, it.budgetKind.wireValue) }
  }
  return ReviewEvidenceBatchResult(results, cumulativeBytes, expansionLedger, outcome)
}
