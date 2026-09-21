package skillbill.codegraph

const val CODEGRAPH_LOCAL_DIRECTORY: String = ".codegraph"
const val CODEGRAPH_SESSION_RUNTIME_DIRECTORY: String = ".skill-bill/runtime/codegraph-sessions"

fun isCodeGraphGeneratedPath(path: String): Boolean {
  val parts = mutableListOf<String>()
  path.replace('\\', '/').split('/').forEach { part ->
    when (part) {
      "", "." -> Unit
      ".." -> if (parts.isNotEmpty()) parts.removeAt(parts.lastIndex)
      else -> parts += part
    }
  }
  val normalized = parts.joinToString("/")
  return normalized == CODEGRAPH_LOCAL_DIRECTORY ||
    normalized.startsWith("$CODEGRAPH_LOCAL_DIRECTORY/") ||
    normalized == CODEGRAPH_SESSION_RUNTIME_DIRECTORY ||
    normalized.startsWith("$CODEGRAPH_SESSION_RUNTIME_DIRECTORY/")
}
