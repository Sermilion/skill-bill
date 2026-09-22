
package skillbill.domain.skillremove

import skillbill.model.FileLocation

object SkillRemoveErrorSanitizer {
  fun sanitize(
    message: String,
    repoRootAbsolutePath: String,
  ): String {
    val repoRoot: FileLocation? = if (message.isBlank()) null else parseRepoRoot(repoRootAbsolutePath)
    if (repoRoot == null) return message
    val repoRootStr = repoRoot.value

    return message.splitToSequence(' ', '\t', '\n')
      .map { token ->
        if (token.isBlank()) return@map token

        if (!token.contains('/') && !token.contains('\\')) return@map token

        val (core, trailing) = stripTrailingPunctuation(token)
        val sanitized = sanitizeToken(core, repoRoot, repoRootStr) ?: return@map token
        sanitized + trailing
      }
      .joinToString(" ")
  }

  private fun parseRepoRoot(repoRootAbsolutePath: String): FileLocation? =
    FileLocation(repoRootAbsolutePath).takeIf(FileLocation::isAbsolute)?.normalized()

  private fun stripTrailingPunctuation(token: String): Pair<String, String> {
    var idx = token.length
    while (idx > 0 && token[idx - 1] in TRAILING_PUNCTUATION) idx--
    return token.substring(0, idx) to token.substring(idx)
  }

  private fun sanitizeToken(
    token: String,
    repoRoot: FileLocation,
    repoRootStr: String,
  ): String? {
    if (token.contains('\u0000')) return null
    val parsed = FileLocation(token)
    if (!parsed.isAbsolute) return null
    val normalized = parsed.normalized()
    return when {
      normalized.startsWith(repoRoot) -> repoRoot.relativize(normalized).value.ifBlank { "." }

      token.startsWith(repoRootStr) -> token.removePrefix(repoRootStr).trimStart('/', '\\').ifBlank { "." }
      else -> EXTERNAL_PATH_PLACEHOLDER
    }
  }

  private const val EXTERNAL_PATH_PLACEHOLDER: String = "<external path>"
  private val TRAILING_PUNCTUATION: Set<Char> = setOf('.', ',', ';', ':', ')', ']', '}', '\'', '"')
}
