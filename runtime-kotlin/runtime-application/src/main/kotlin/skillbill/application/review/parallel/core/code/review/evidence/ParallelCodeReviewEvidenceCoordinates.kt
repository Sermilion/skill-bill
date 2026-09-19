package skillbill.application.review.parallel.core.code.review.evidence
import skillbill.application.review.model.ParallelCodeReviewRequest
import skillbill.application.review.parallel.core.code.review.bundled.review
import skillbill.application.review.parallel.core.code.review.bundled.scope
import skillbill.application.review.parallel.core.code.review.end.repoRoot
import skillbill.application.review.parallel.core.code.review.regression.repoRoot
import skillbill.application.review.parallel.core.code.review.runner.evidence
import skillbill.application.review.parallel.core.code.review.runner.request
import skillbill.application.review.parallel.core.code.review.spec.request
import skillbill.application.review.parallel.core.review.scope
import skillbill.application.review.parallel.planning.ParallelCodeReviewRunnerPlanning
import skillbill.application.review.parallel.planning.diffResolver
import skillbill.application.review.parallel.planning.evidence
import skillbill.application.review.parallel.planning.hasSuppliedDiff
import skillbill.application.review.parallel.planning.head
import skillbill.application.review.parallel.planning.repoRoot
import skillbill.application.review.parallel.planning.request
import skillbill.application.review.parallel.planning.scope
import skillbill.application.review.preparation.request
import skillbill.application.review.preparation.scope
import skillbill.application.review.review.evidence
import skillbill.application.review.review.repoRoot
import skillbill.application.review.service.review
import skillbill.application.review.spec.evidence
import skillbill.application.review.spec.repoRoot
import skillbill.application.reviewevidence.model.DiffResolutionException
import skillbill.application.reviewevidence.model.ParallelReviewScope
import skillbill.ports.review.model.ReviewCheckpointFileIdentity
import skillbill.ports.review.model.ReviewEvidenceCoordinates

internal fun ParallelCodeReviewRunnerPlanning.evidenceCoordinates(
  request: ParallelCodeReviewRequest,
  head: String,
): ReviewEvidenceCoordinates {
  if (hasSuppliedDiff(request) || request.scope in setOf(ParallelReviewScope.BRANCH, ParallelReviewScope.PR)) {
    return ReviewEvidenceCoordinates.Committed(head)
  }
  val index = diffResolver.runProcess(listOf("git", "ls-files", "--stage", "-z"), request.repoRoot)
    ?: throw DiffResolutionException("Cannot capture the reviewed index.")
  val entries = index.split('\u0000').filter(String::isNotEmpty).associate { row ->
    val metadata = row.substringBefore('\t').split(' ')
    if (metadata.size != INDEX_ENTRY_METADATA_FIELDS || metadata[2] != "0" || '\t' !in row) {
      throw DiffResolutionException("Review checkpoint contains unresolved index entries.")
    }
    row.substringAfter('\t') to ReviewCheckpointFileIdentity.Regular(metadata[1])
  }
  if (request.scope == ParallelReviewScope.STAGED) {
    return ReviewEvidenceCoordinates.Checkpoint(ReviewEvidenceCoordinates.Checkpoint.Kind.INDEX, entries)
  }
  val files = diffResolver.reviewWorktreeFileIdentities(request.repoRoot, entries.keys.toList())
  return ReviewEvidenceCoordinates.Checkpoint(ReviewEvidenceCoordinates.Checkpoint.Kind.WORKTREE, files)
}

private const val INDEX_ENTRY_METADATA_FIELDS = 3
