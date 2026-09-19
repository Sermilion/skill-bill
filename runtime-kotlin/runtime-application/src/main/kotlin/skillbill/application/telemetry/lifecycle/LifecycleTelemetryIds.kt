package skillbill.application.telemetry.lifecycle
import java.security.SecureRandom
import java.time.Clock
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private const val SESSION_SUFFIX_LENGTH = 4

private val sessionIdTimestampFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
private val suffixChars = "abcdefghijklmnopqrstuvwxyz0123456789".toCharArray()
private val random = SecureRandom()

fun generateLifecycleSessionId(prefix: String, clock: Clock): String {
  val timestamp = clock.instant().atOffset(ZoneOffset.UTC).format(sessionIdTimestampFormatter)
  val suffix = CharArray(SESSION_SUFFIX_LENGTH) { suffixChars[random.nextInt(suffixChars.size)] }.concatToString()
  return "$prefix-$timestamp-$suffix"
}
