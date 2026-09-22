package skillbill.experiment
import skillbill.error.shellcontent.ExperimentParameterMalformedError
import skillbill.experiment.model.ResolvedExperimentSelection

object ExperimentParameterParser {
  fun parse(rawParameter: String?): ResolvedExperimentSelection {
    if (rawParameter == null) {
      return ResolvedExperimentSelection(normalizedNames = emptyList(), explicitDisable = false)
    }
    val raw = rawParameter.trim()
    if (raw.isEmpty()) {
      throw ExperimentParameterMalformedError(rawParameter, "must not be empty.")
    }
    if (raw.equals(EXPERIMENT_DISABLE_TOKEN, ignoreCase = true)) {
      return ResolvedExperimentSelection(normalizedNames = emptyList(), explicitDisable = true)
    }
    val parts = raw.split(',').map { part -> part.trim() }
    val names = parseExperimentNameList(parts)
    val reason =
      when {
        parts.any { part -> part.equals(EXPERIMENT_DISABLE_TOKEN, ignoreCase = true) } ->
          "cannot mix none with experiment names."
        names == null -> "contains an empty, invalid, or duplicate name."
        else -> null
      }
    if (reason != null) {
      throw ExperimentParameterMalformedError(rawParameter, reason)
    }
    return ResolvedExperimentSelection(normalizedNames = names.orEmpty(), explicitDisable = false)
  }
}
