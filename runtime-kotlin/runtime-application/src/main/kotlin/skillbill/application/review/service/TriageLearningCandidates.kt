package skillbill.application.review.service

import skillbill.application.review.model.LearningCandidate
import skillbill.learnings.LearningsRuntime
import skillbill.learnings.model.LearningScope
import skillbill.review.model.NumberedFinding
import skillbill.review.model.TriageDecision

private const val SUGGESTED_TITLE_MAX_LENGTH = 120

internal fun learningCandidates(
  reviewRunId: String,
  recorded: List<TriageDecision>,
  numberedFindings: List<NumberedFinding>,
  resolveRepoScopeKey: () -> String?,
): List<LearningCandidate> {
  val notedRejections =
    recorded.filter { it.outcomeType in LearningsRuntime.rejectedFindingOutcomeTypes && it.note.isNotBlank() }
  if (notedRejections.isEmpty()) {
    return emptyList()
  }
  val descriptionsByFindingId = numberedFindings.associate { it.findingId to it.description }
  val repoScopeKey = resolveRepoScopeKey()
  return notedRejections
    .map { decision ->
      LearningCandidate(
        reviewRunId = reviewRunId,
        findingId = decision.findingId,
        suggestedTitle =
          suggestedTitle(descriptionsByFindingId[decision.findingId].orEmpty(), decision.findingId),
        suggestedRuleText = decision.note.trim(),
        suggestedScope = if (repoScopeKey == null) LearningScope.GLOBAL else LearningScope.REPO,
        suggestedScopeKey = repoScopeKey,
      )
    }
}

internal fun suggestedTitle(
  description: String,
  findingId: String,
): String =
  description.lineSequence()
    .firstOrNull()
    .orEmpty()
    .trim()
    .take(SUGGESTED_TITLE_MAX_LENGTH)
    .ifBlank { findingId }
