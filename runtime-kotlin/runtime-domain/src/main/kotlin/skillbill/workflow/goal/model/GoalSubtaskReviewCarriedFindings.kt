package skillbill.workflow.goal.model

import skillbill.workflow.taskruntime.model.normalizeIdentityPart

fun withoutRefutedFindings(
  findings: List<GoalSubtaskReviewCompactFinding>,
  refutedFindingIds: Set<String>,
): List<GoalSubtaskReviewCompactFinding> {
  if (refutedFindingIds.isEmpty()) return findings
  val refuted = refutedFindingIds.mapTo(linkedSetOf(), ::normalizeIdentityPart)
  return findings.filterNot { finding -> finding.findingId?.let(::normalizeIdentityPart) in refuted }
}
