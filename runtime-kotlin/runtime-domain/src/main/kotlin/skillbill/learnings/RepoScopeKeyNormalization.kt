package skillbill.learnings

private const val SCHEME_SEPARATOR = "://"
private const val GIT_SUFFIX = ".git"

fun normalizeRepoScopeKey(originUrl: String): String? {
  val trimmed = originUrl.trim()
  if (trimmed.isEmpty()) return null
  val path = originUrlPath(trimmed) ?: return null
  val segments =
    path.trimEnd('/')
      .removeSuffix(GIT_SUFFIX)
      .split('/')
      .filter(String::isNotEmpty)
  if (segments.size < 2) return null
  if (segments.any { segment -> segment.any(Char::isWhitespace) }) return null
  return segments.joinToString("/")
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
