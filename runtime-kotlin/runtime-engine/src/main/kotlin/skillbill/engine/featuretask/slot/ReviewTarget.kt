package skillbill.engine.featuretask.slot

/**
 * What one review call inspects. Each target composes the opening lines of the review prompt; last commit reads its
 * revisions from the prepared review input.
 */
sealed interface ReviewTarget {
  /** The opening prompt lines for this target, given the prepared input's base and head revisions. */
  fun openingLines(
    baseRevision: String,
    headRevision: String,
  ): List<String>

  data object LastCommit : ReviewTarget {
    override fun openingLines(
      baseRevision: String,
      headRevision: String,
    ): List<String> =
      listOf(
        "Review the last commit `$headRevision` against its first parent `$baseRevision`.",
        "Inspect with `git diff $baseRevision $headRevision` in this repository workspace.",
      )
  }

  data object Uncommitted : ReviewTarget {
    override fun openingLines(
      baseRevision: String,
      headRevision: String,
    ): List<String> =
      listOf(
        "Review the uncommitted changes in this repository workspace against `HEAD`, including untracked files.",
        "Inspect with `git diff HEAD` plus `git status --porcelain` for untracked files.",
      )
  }

  data class Commit(val sha: String) : ReviewTarget {
    override fun openingLines(
      baseRevision: String,
      headRevision: String,
    ): List<String> =
      listOf(
        "Review commit `$sha` against its first parent `$sha^`.",
        "Inspect with `git diff $sha^ $sha` in this repository workspace.",
      )
  }
}
