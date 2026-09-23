package skillbill.application.review.learnings

import me.tatarka.inject.annotations.Inject
import skillbill.application.learning.learningEntrySessionJson
import skillbill.contracts.learning.summarizeAppliedLearnings
import skillbill.learnings.learningEntry
import skillbill.learnings.model.LearningEntry
import skillbill.ports.db.DatabaseSessionFactory
import skillbill.ports.diagnostics.RuntimeDiagnostics
import skillbill.ports.repository.RepositoryOriginScopeKeyPort
import skillbill.review.context.model.hunk.ReviewLearningsReference
import java.nio.file.Path

internal data class ReviewLearningsResolution(
  val references: List<ReviewLearningsReference>,
  val appliedSummary: String,
)

@Inject
class ReviewLearningsResolver(
  private val database: DatabaseSessionFactory,
  private val originScopeKeyPort: RepositoryOriginScopeKeyPort,
  private val diagnostics: RuntimeDiagnostics,
) {
  internal fun resolve(
    repoRoot: Path,
    routedSkill: String,
    reviewSessionId: String,
  ): ReviewLearningsResolution {
    val repoScopeKey =
      originScopeKeyPort.repoScopeKeyOrNull(repoRoot, diagnostics) { reason ->
        "Review learnings resolved without a repo scope for '$repoRoot': $reason. " +
          "Global and skill learnings still apply."
      }
    val entries =
      database.transaction { unitOfWork ->
        val resolution = unitOfWork.learnings.resolve(repoScopeKey, routedSkill)
        val resolved = resolution.records.map(::learningEntry)
        unitOfWork.learnings.saveSessionLearnings(
          reviewSessionId,
          learningEntrySessionJson(resolution.skillName, resolved),
        )
        resolved
      }
    return ReviewLearningsResolution(
      references = entries.map(::reviewLearningsReference),
      appliedSummary = summarizeAppliedLearnings(entries.map(LearningEntry::reference)),
    )
  }
}

private fun reviewLearningsReference(entry: LearningEntry): ReviewLearningsReference =
  ReviewLearningsReference(
    learningId = entry.reference,
    source = entry.scopeLabel,
    scope = entry.scope.wireName,
    title = entry.title,
    ruleText = entry.ruleText,
    digest = ReviewLearningsReference.digestOf(entry.ruleText),
  )
