package skillbill.engine.work

object IdeStatusBranchScope {
  fun branchReferencesIssueKey(
    branch: String,
    issueKey: String,
  ): Boolean {
    val haystack = branch.lowercase()
    val needle = issueKey.trim().lowercase()
    if (needle.isEmpty()) return false
    var index = haystack.indexOf(needle)
    while (index >= 0) {
      val before = haystack.getOrNull(index - 1)
      val after = haystack.getOrNull(index + needle.length)
      if (before?.isLetterOrDigit() != true && after?.isLetterOrDigit() != true) return true
      index = haystack.indexOf(needle, index + 1)
    }
    return false
  }
}
