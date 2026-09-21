package skillbill.experiment.model

import skillbill.config.model.ExperimentAvailabilityPolicy
import skillbill.contracts.experiment.ExperimentConfigPayloadKeys
import skillbill.experiment.EXPERIMENT_DISABLE_TOKEN
import skillbill.experiment.parseExperimentNameList
import skillbill.telemetry.model.TelemetryConfigDocument
import skillbill.telemetry.model.TelemetryOpenDocument

sealed interface ExperimentConfigParse {
  data class Valid(val policy: ExperimentAvailabilityPolicy) : ExperimentConfigParse

  data class Invalid(
    val key: String,
    val value: String,
    val reason: String,
  ) : ExperimentConfigParse
}

fun parseExperimentAvailabilityValue(value: Any?): ExperimentConfigParse = when {
  value == null ->
    ExperimentConfigParse.Invalid(
      ExperimentConfigPayloadKeys.EXPERIMENTS,
      "null",
      "must be a string array, not null.",
    )
  value is String ->
    ExperimentConfigParse.Invalid(
      ExperimentConfigPayloadKeys.EXPERIMENTS,
      value,
      "must be a string array, not a scalar.",
    )
  value !is List<*> ->
    ExperimentConfigParse.Invalid(
      ExperimentConfigPayloadKeys.EXPERIMENTS,
      value.toString(),
      "must be a string array.",
    )
  value.isEmpty() ->
    ExperimentConfigParse.Valid(ExperimentAvailabilityPolicy.Disabled)
  else -> parseExperimentStringEntries(value)
}

private fun parseExperimentStringEntries(entries: List<*>): ExperimentConfigParse {
  val invalidEntry = entries.firstOrNull { it !is String }
  val stringEntries = entries.filterIsInstance<String>()
  return when {
    invalidEntry != null ->
      ExperimentConfigParse.Invalid(
        ExperimentConfigPayloadKeys.EXPERIMENTS,
        invalidEntry.toString(),
        "entries must be strings.",
      )
    stringEntries.any { entry -> entry.equals(EXPERIMENT_DISABLE_TOKEN, ignoreCase = true) } ->
      ExperimentConfigParse.Invalid(
        ExperimentConfigPayloadKeys.EXPERIMENTS,
        stringEntries.toString(),
        "disable experiments with an empty array; none is not a config entry.",
      )
    else -> parseExperimentNames(stringEntries)
  }
}

private fun parseExperimentNames(names: List<String>): ExperimentConfigParse {
  val parsedNames = parseExperimentNameList(names)
  return if (parsedNames == null) {
    ExperimentConfigParse.Invalid(
      ExperimentConfigPayloadKeys.EXPERIMENTS,
      names.toString(),
      "contains empty, invalid, or duplicate experiment names.",
    )
  } else {
    ExperimentConfigParse.Valid(ExperimentAvailabilityPolicy.ExplicitNames(parsedNames))
  }
}

fun TelemetryConfigDocument.withListedExperimentNames(names: List<String>): TelemetryConfigDocument {
  val updatedPayload = payload.toMutableMap()
  updatedPayload[ExperimentConfigPayloadKeys.EXPERIMENTS] = names
  return TelemetryConfigDocument(TelemetryOpenDocument.from(updatedPayload))
}
