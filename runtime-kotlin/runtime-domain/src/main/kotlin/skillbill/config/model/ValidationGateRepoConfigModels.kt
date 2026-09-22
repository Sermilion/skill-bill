package skillbill.config.model

const val VALIDATION_GATE_KEY: String = "validation_gate"
const val GRADLE_WRAPPER_KEY: String = "gradle_wrapper"

data class ValidationGateRepoConfig(
  val gradleWrapper: String? = null,
) {
  companion object {
    fun defaults(): ValidationGateRepoConfig = ValidationGateRepoConfig()
  }
}

sealed interface ValidationGateRepoConfigParse {
  data class Valid(val config: ValidationGateRepoConfig) : ValidationGateRepoConfigParse

  data class Invalid(
    val keyPath: String,
    val value: String,
    val reason: String,
  ) : ValidationGateRepoConfigParse
}

fun parseValidationGateRepoConfig(raw: Any?): ValidationGateRepoConfigParse =
  try {
    ValidationGateRepoConfigParse.Valid(parseValidationGateMapping(raw))
  } catch (failure: InvalidValidationGateRepoConfig) {
    failure.invalid
  }

fun parseGradleWrapperPath(raw: String?): String? {
  val trimmed = raw?.trim().orEmpty()
  if (trimmed.isEmpty()) return null
  val withForwardSlashes = trimmed.replace('\\', '/')
  val absolute =
    withForwardSlashes.startsWith("/") || withForwardSlashes.matches(Regex("^[A-Za-z]:/.*"))
  val normalized = withForwardSlashes.removePrefix("./")
  val segments = normalized.split('/').filter { segment -> segment.isNotEmpty() }
  val invalid =
    absolute ||
      normalized.isEmpty() ||
      segments.isEmpty() ||
      segments.any { segment -> segment == "." || segment == ".." }
  return if (invalid) null else segments.joinToString("/")
}

fun applyValidationGateGradleWrapper(
  argv: List<String>,
  gradleWrapper: String?,
): List<String> {
  val wrapper = gradleWrapper?.takeIf { path -> path.isNotBlank() } ?: return argv
  val head = argv.firstOrNull() ?: return argv
  if (head != "./gradlew" && head != "gradlew") return argv
  val projectDir = wrapper.substringBeforeLast('/', missingDelimiterValue = "")
  val rewrittenHead =
    if (projectDir.isNotEmpty()) {
      listOf(wrapper, "-p", projectDir)
    } else {
      listOf(wrapper)
    }
  return rewrittenHead + argv.drop(1)
}

private fun parseValidationGateMapping(raw: Any?): ValidationGateRepoConfig {
  val root = raw as? Map<*, *> ?: invalidValidationGate(VALIDATION_GATE_KEY, raw, "must be a mapping.")
  val fields = root.entries.associate { (key, value) -> key.toString() to value }
  fields.entries.firstOrNull { (key, _) -> key !in VALIDATION_GATE_FIELDS }?.let { (key, value) ->
    invalidValidationGate("$VALIDATION_GATE_KEY.$key", value, "is not a supported validation_gate field.")
  }
  if (!fields.containsKey(GRADLE_WRAPPER_KEY)) {
    return ValidationGateRepoConfig.defaults()
  }
  val rawWrapper = fields[GRADLE_WRAPPER_KEY]
  if (rawWrapper !is String) {
    invalidValidationGate(
      "$VALIDATION_GATE_KEY.$GRADLE_WRAPPER_KEY",
      rawWrapper,
      "must be a non-blank repo-relative path string.",
    )
  }
  val parsed =
    parseGradleWrapperPath(rawWrapper)
      ?: invalidValidationGate(
        "$VALIDATION_GATE_KEY.$GRADLE_WRAPPER_KEY",
        rawWrapper,
        "must be a non-blank repo-relative path without '..' segments.",
      )
  return ValidationGateRepoConfig(gradleWrapper = parsed)
}

private fun invalidValidationGate(
  keyPath: String,
  value: Any?,
  reason: String,
): Nothing =
  throw InvalidValidationGateRepoConfig(
    ValidationGateRepoConfigParse.Invalid(
      keyPath = keyPath,
      value = value?.toString() ?: "null",
      reason = reason,
    ),
  )

private class InvalidValidationGateRepoConfig(
  val invalid: ValidationGateRepoConfigParse.Invalid,
) : RuntimeException()

private val VALIDATION_GATE_FIELDS: Set<String> = setOf(GRADLE_WRAPPER_KEY)
