package skillbill.infrastructure.sqlite.telemetry.redaction
import skillbill.infrastructure.sqlite.core.ops.bindAll
import skillbill.infrastructure.sqlite.telemetry.lifecycle.telemetry.emit.connection
import skillbill.infrastructure.sqlite.telemetry.lifecycle.telemetry.feature.connection
import skillbill.infrastructure.sqlite.telemetry.lifecycle.telemetry.goal.connection
import skillbill.infrastructure.sqlite.telemetry.lifecycle.telemetry.measurement.connection
import skillbill.infrastructure.sqlite.telemetry.lifecycle.telemetry.quality.connection
import skillbill.infrastructure.sqlite.telemetry.lifecycle.telemetry.store.connection
import skillbill.infrastructure.sqlite.telemetry.outbox.connection
import java.security.SecureRandom
import java.sql.Connection

private const val REDACTION_SALT_KEY = "telemetry_redaction_salt"
private const val SALT_BYTE_LENGTH = 32

internal fun telemetryRedactionSalt(connection: Connection): String {
  readRedactionSalt(connection)?.let { return it }
  val generated = hexEncode(ByteArray(SALT_BYTE_LENGTH).also(SecureRandom()::nextBytes))
  connection.prepareStatement(
    "INSERT OR IGNORE INTO telemetry_local_secrets (secret_key, secret_value) VALUES (?, ?)",
  ).use { statement ->
    statement.bindAll(REDACTION_SALT_KEY, generated)
    statement.executeUpdate()
  }
  return readRedactionSalt(connection) ?: generated
}

private fun readRedactionSalt(connection: Connection): String? = connection.prepareStatement(
  "SELECT secret_value FROM telemetry_local_secrets WHERE secret_key = ?",
).use { statement ->
  statement.bindAll(REDACTION_SALT_KEY)
  statement.executeQuery().use { resultSet ->
    if (resultSet.next()) resultSet.getString("secret_value")?.takeIf(String::isNotBlank) else null
  }
}
