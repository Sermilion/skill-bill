package skillbill.learnings

private const val SCHEME_SEPARATOR = "://"
private const val GIT_SUFFIX = ".git"

fun normalizeRepoScopeKey(originUrl: String): String? {
  val path = originUrl.trim().takeIf(String::isNotEmpty)?.let(::originUrlPath) ?: return null
  val segments =
    path.trimEnd('/')
      .removeSuffix(GIT_SUFFIX)
      .split('/')
      .filter(String::isNotEmpty)
  val isRepoPath = segments.size >= 2 && segments.none { segment -> segment.any(Char::isWhitespace) }
  return if (isRepoPath) segments.joinToString("/") else null
}

private fun originUrlPath(originUrl: String): String? {
  val schemeIndex = originUrl.indexOf(SCHEME_SEPARATOR)
  if (schemeIndex > 0) {
    val authorityAndPath = originUrl.substring(schemeIndex + SCHEME_SEPARATOR.length)
    val pathStart = authorityAndPath.indexOf('/')
    return if (pathStart < 0) null else authorityAndPath.substring(pathStart + 1)
  }
  if (SCHEME_SEPARATOR in originUrl) return null
  val colonIndex = originUrl.indexOf(':')
  if (colonIndex <= 0) return null
  return originUrl.substring(colonIndex + 1)
}
