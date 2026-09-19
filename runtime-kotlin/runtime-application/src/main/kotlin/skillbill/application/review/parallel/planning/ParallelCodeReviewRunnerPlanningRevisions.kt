package skillbill.application.review.parallel.planning
import skillbill.application.review.model.ParallelCodeReviewRequest
import skillbill.application.review.packet.revision
import skillbill.application.review.parallel.core.code.review.bundled.review
import skillbill.application.review.parallel.core.code.review.bundled.scope
import skillbill.application.review.parallel.core.code.review.end.repoRoot
import skillbill.application.review.parallel.core.code.review.regression.repoRoot
import skillbill.application.review.parallel.core.code.review.runner.PARALLEL_REVIEW_HEAD_REVISION
import skillbill.application.review.parallel.core.code.review.runner.planning
import skillbill.application.review.parallel.core.code.review.runner.request
import skillbill.application.review.parallel.core.code.review.spec.request
import skillbill.application.review.parallel.core.review.scope
import skillbill.application.review.preparation.request
import skillbill.application.review.preparation.scope
import skillbill.application.review.review.candidate
import skillbill.application.review.review.repoRoot
import skillbill.application.review.service.review
import skillbill.application.review.spec.repoRoot
import skillbill.application.reviewevidence.model.DiffResolutionException
import skillbill.application.reviewevidence.model.ParallelReviewScope
import java.nio.file.Path

internal fun ParallelCodeReviewRunnerPlanning.resolveReviewRevisions(
  request: ParallelCodeReviewRequest,
): Pair<String, String> {
  val (base, head) = if (spansCommitRange(request)) canonicalRange(request) else declaredRange(request)
  if (base.isBlank() || head.isBlank()) {
    throw DiffResolutionException("Review base and head revisions must resolve to non-blank immutable identities.")
  }
  return base to head
}

internal fun ParallelCodeReviewRunnerPlanning.spansCommitRange(request: ParallelCodeReviewRequest): Boolean =
  !hasSuppliedDiff(request) &&
    (request.scope == ParallelReviewScope.BRANCH || request.scope == ParallelReviewScope.PR)

internal fun ParallelCodeReviewRunnerPlanning.hasSuppliedDiff(request: ParallelCodeReviewRequest): Boolean =
  request.suppliedDiff != null || request.suppliedDiffPath != null

internal fun ParallelCodeReviewRunnerPlanning.canonicalRange(
  request: ParallelCodeReviewRequest,
): Pair<String, String> {
  val head = canonicalRevision(request.headRevision ?: PARALLEL_REVIEW_HEAD_REVISION, request.repoRoot)
  val base = request.baseRevision?.let { canonicalRevision(it, request.repoRoot) } ?: when (request.scope) {
    ParallelReviewScope.PR -> detectPrBase(request.repoRoot)
    ParallelReviewScope.STAGED,
    ParallelReviewScope.UNSTAGED,
    ParallelReviewScope.UNCOMMITTED,
    ParallelReviewScope.BRANCH,
    ParallelReviewScope.WORKTREE_FROM_BASE,
    -> detectBranchBase(request.repoRoot)
  }
  return base to head
}

internal fun ParallelCodeReviewRunnerPlanning.declaredRange(request: ParallelCodeReviewRequest): Pair<String, String> {
  val head = request.headRevision
    ?: if (hasSuppliedDiff(request)) {
      PARALLEL_REVIEW_HEAD_REVISION
    } else {
      canonicalRevision(PARALLEL_REVIEW_HEAD_REVISION, request.repoRoot)
    }
  return (request.baseRevision ?: head) to head
}

internal fun ParallelCodeReviewRunnerPlanning.canonicalRevision(revision: String, repoRoot: Path): String =
  diffResolver.runProcess(listOf("git", "rev-parse", "--verify", "$revision^{commit}"), repoRoot)
    ?.trim()
    ?.takeIf { it.isNotBlank() }
    ?: throw DiffResolutionException("Review revision '$revision' does not resolve to a commit here.")

internal fun ParallelCodeReviewRunnerPlanning.detectPrBase(repoRoot: Path): String {
  val baseRefOid = diffResolver
    .runProcess(listOf("gh", "pr", "view", "--json", "baseRefOid", "--jq", ".baseRefOid"), repoRoot)
    ?.trim()
    ?.takeIf { it.isNotBlank() }
  val merged = baseRefOid?.let {
    diffResolver.runProcess(listOf("git", "merge-base", "HEAD", it), repoRoot)?.trim()
  }
  return merged?.takeIf { it.isNotBlank() } ?: detectBranchBase(repoRoot)
}

internal fun ParallelCodeReviewRunnerPlanning.detectBranchBase(repoRoot: Path): String {
  val candidates = listOf("main", "master", "origin/main", "origin/master")
  for (candidate in candidates) {
    val result = diffResolver.runProcess(listOf("git", "merge-base", "HEAD", candidate), repoRoot)
    if (result != null) return result.trim()
  }
  throw DiffResolutionException(
    "Could not detect branch base. Tried: ${candidates.joinToString()}.",
  )
}
