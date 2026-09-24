package skillbill.workflow.time

import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

fun parsePersistedInstant(value: String): Instant = try {
  Instant.parse(value)
} catch (_: RuntimeException) {
  try {
    OffsetDateTime.parse(value).toInstant()
  } catch (_: RuntimeException) {
    try {
      LocalDateTime.parse(value.replace(' ', 'T'), DateTimeFormatter.ISO_LOCAL_DATE_TIME).toInstant(ZoneOffset.UTC)
    } catch (error: RuntimeException) {
      throw IllegalArgumentException("Timestamp is not a supported persisted instant.", error)
    }
  }
}
