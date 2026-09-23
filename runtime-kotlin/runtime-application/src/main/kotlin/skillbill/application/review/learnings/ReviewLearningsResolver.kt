package skillbill.application.review.learnings

import me.tatarka.inject.annotations.Inject
import skillbill.application.learning.learningEntrySessionJson
import skillbill.contracts.learning.summarizeAppliedLearnings
import skillbill.learnings.learningEntry
import skillbill.learnings.model.LearningEntry
import skillbill.learnings.scopedLearningLabel
import skillbill.ports.db.DatabaseSessionFactory
import skillbill.ports.diagnostics.RuntimeDiagnostics
import skillbill.ports.repository.OriginScopeKey
import skillbill.ports.repository.RepositoryOriginScopeKeyPort
import skillbill.review.context.model.hunk.ReviewLearningsReference
import java.nio.file.Path

data class ReviewLearningsResolution(
  val references: List<ReviewLearningsReference>,
  val appliedSummary: String,
)

@Inject
class ReviewLearningsResolver(
  private val database: DatabaseSessionFactory,
  private val originScopeKeyPort: RepositoryOriginScopeKeyPort,
  private val diagnostics: RuntimeDiagnostics,
) {
  fun resolve(
    repoRoot: Path,
    routedSkill: String,
    reviewSessionId: String,
  ): ReviewLearningsResolution {
    val repoScopeKey = repoScopeKey(repoRoot)
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

  private fun repoScopeKey(repoRoot: Path): String? =
    when (val origin = originScopeKeyPort.resolveOriginScopeKey(repoRoot)) {
      is OriginScopeKey.Resolved -> origin.key
      is OriginScopeKey.Unavailable -> {
        diagnostics.warning(
          "Review learnings resolved without a repo scope for '$repoRoot': ${origin.reason}. " +
            "Global and skill learnings still apply.",
        )
        null
      }
    }
}

private fun reviewLearningsReference(entry: LearningEntry): ReviewLearningsReference =
  ReviewLearningsReference(
    learningId = entry.reference,
    source = scopedLearningLabel(entry),
    scope = entry.scope.wireName,
    title = entry.title,
    ruleText = entry.ruleText,
    digest = ReviewLearningsReference.digestOf(entry.ruleText),
  )
