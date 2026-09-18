package skillbill.infrastructure.sqlite.telemetry

import java.security.MessageDigest

const val REDACTED_ISSUE_KEY_PREFIX = "iss_"

private const val REDACTED_TOKEN_HEX_LENGTH = 16
private const val HEX_MASK = 0xff
private const val HEX_RADIX = 16

fun redactIssueKey(issueKey: String, level: String, salt: String): String {
  if (issueKey.isBlank() || level == "full") {
    return issueKey
  }
  val digest = MessageDigest.getInstance("SHA-256").digest("$salt|$issueKey".toByteArray(Charsets.UTF_8))
  return REDACTED_ISSUE_KEY_PREFIX + hexEncode(digest).take(REDACTED_TOKEN_HEX_LENGTH)
}

fun redactIssueKeyReferences(value: String, issueKey: String, level: String, salt: String): String {
  if (issueKey.isBlank() || !value.contains(issueKey)) {
    return value
  }
  return value.replace(issueKey, redactIssueKey(issueKey, level, salt))
}

internal fun hexEncode(bytes: ByteArray): String = buildString(bytes.size * 2) {
  bytes.forEach { byte -> append((byte.toInt() and HEX_MASK).toString(HEX_RADIX).padStart(2, '0')) }
}
