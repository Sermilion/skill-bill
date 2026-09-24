package skillbill.contracts.issuekey

const val MAX_ISSUE_KEY_LENGTH: Int = 128

fun isWellFormedIssueKey(issueKey: String): Boolean {
  val trimmed = issueKey.trim()
  return trimmed.isNotEmpty() &&
    trimmed.length <= MAX_ISSUE_KEY_LENGTH &&
    trimmed.none(Character::isISOControl)
}

fun normalizeIssueKey(issueKey: String?): String? =
  issueKey?.trim()?.also {
    require(it.isNotEmpty()) { "issue key cannot be blank." }
    require(it.length <= MAX_ISSUE_KEY_LENGTH) { "issue key must be at most $MAX_ISSUE_KEY_LENGTH characters." }
    require(it.none(Character::isISOControl)) { "issue key cannot contain control characters." }
  }

fun normalizeRequiredIssueKey(issueKey: String): String = requireNotNull(normalizeIssueKey(issueKey))
