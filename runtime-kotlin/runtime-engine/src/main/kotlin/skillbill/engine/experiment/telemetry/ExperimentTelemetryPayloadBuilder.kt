package skillbill.engine.experiment.telemetry

import skillbill.contracts.experiment.ExperimentTelemetryPayloadKeys
import java.security.MessageDigest

enum class ExperimentTelemetryConsent {
  OFF,
  ANONYMOUS,
  FULL,
}

object ExperimentTelemetryPayloadBuilder {
  fun build(
    consent: ExperimentTelemetryConsent,
    pairId: String,
    cohort: String,
    metrics: Map<String, Any?>,
  ): Map<String, Any?>? {
    if (consent == ExperimentTelemetryConsent.OFF) return null
    val payload =
      linkedMapOf<String, Any?>(
        ExperimentTelemetryPayloadKeys.PAIR_ID to
          if (consent == ExperimentTelemetryConsent.FULL) {
            pairId
          } else {
            hash(
              pairId,
            )
          },
        ExperimentTelemetryPayloadKeys.COHORT to cohort,
        ExperimentTelemetryPayloadKeys.METRICS to redact(metrics),
      )
    return payload
  }

  private fun redact(value: Any?): Any? =
    when (value) {
      is Map<*, *> ->
        value.entries
          .filter { entry -> entry.key?.toString()?.let(::allowedKey) == true }
          .associate { entry -> entry.key.toString() to redact(entry.value) }
      is Iterable<*> -> value.map(::redact)
      else -> value
    }

  private fun allowedKey(key: String): Boolean {
    val normalized = key.lowercase()
    return listOf("source", "query", "path", "secret", "credential", "token").none(normalized::contains)
  }

  private fun hash(value: String): String =
    MessageDigest.getInstance("SHA-256").digest(value.encodeToByteArray())
      .joinToString("") { byte -> "%02x".format(byte) }
}
