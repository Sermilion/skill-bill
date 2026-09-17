package skillbill.application.reviewevidence

internal data class RawCommitDiff(
  val commitSha: String,
  val parentSha: String,
  val subject: String,
  val diff: String,
)

internal fun diffRecords(diff: String): List<String> {
  val normalized = diff.replace("\r\n", "\n")
  val gitRecords = normalized.split(Regex("(?m)(?=^diff --git )")).filter { it.startsWith("diff --git ") }
  return gitRecords.ifEmpty {
    normalized.split(Regex("(?m)(?=^\\+\\+\\+ )")).filter { it.startsWith("+++ ") }
  }
}
