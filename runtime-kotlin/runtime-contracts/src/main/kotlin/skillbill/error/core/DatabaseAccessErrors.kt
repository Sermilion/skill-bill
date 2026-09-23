package skillbill.error.core

private const val MAX_CONDITION_CHARS = 200

enum class DatabaseAccessOperation(val wireValue: String) {
  OPEN("open"),
  READ("read"),
  WRITE("write"),
}

class DatabaseAccessError(
  val dbPath: String,
  val operation: DatabaseAccessOperation,
  condition: String,
) : RuntimeException(
    "Database ${operation.wireValue} failed for '$dbPath': ${boundedCondition(condition)}",
  ) {
  val condition: String = boundedCondition(condition)
}

class DatabaseBusyError(cause: Throwable) : RuntimeException(cause.message, cause)

private val STACK_FRAME_LINE = Regex("^\\s*(at\\s+\\S|Caused by:|\\.{3}\\s+\\d+\\s+more)")
private val QUALIFIED_SQLITE_TYPE = Regex("\\borg\\.sqlite\\.[A-Za-z0-9_.$]+")

private fun boundedCondition(raw: String): String {
  val singleLine =
    raw.lineSequence()
      .filterNot { STACK_FRAME_LINE.containsMatchIn(it) }
      .joinToString(" ") { it.trim() }
      .replace(QUALIFIED_SQLITE_TYPE, "sqlite")
      .replace(Regex("\\s+"), " ")
      .trim()
      .ifBlank { "unknown sqlite condition" }
  return if (singleLine.length <= MAX_CONDITION_CHARS) singleLine else singleLine.take(MAX_CONDITION_CHARS) + "…"
}
