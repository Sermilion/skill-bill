package skillbill.experiment.model

private val EXPERIMENT_NAME_PATTERN = Regex("^[a-z][a-z0-9]*(-[a-z0-9]+)*$")

internal const val EXPERIMENT_DISABLE_TOKEN: String = "none"

fun validateExperimentName(name: String): String? {
  val trimmed = name.trim()
  if (trimmed.isEmpty()) return null
  if (trimmed == EXPERIMENT_DISABLE_TOKEN) return null
  if (!EXPERIMENT_NAME_PATTERN.matches(trimmed)) return null
  return trimmed
}

internal fun parseExperimentNameList(rawNames: Iterable<String>): List<String>? {
  val normalized = rawNames.mapNotNull { entry -> validateExperimentName(entry) }
  if (normalized.size != rawNames.count()) return null
  if (normalized.toSet().size != normalized.size) return null
  return normalized.sorted()
}
