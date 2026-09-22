package skillbill.scaffold.policy.scaffold
import skillbill.error.shellcontent.InvalidScaffoldPayloadError

fun requireStringList(
  value: Any?,
  fieldName: String,
): List<String> {
  if (value !is List<*>) {
    failInvalidScaffoldPayload("Scaffold payload field '$fieldName' must be a list of strings.")
  }
  val mapped = value.map { it as? String ?: failNonBlankString(fieldName) }
  if (mapped.any(String::isBlank)) {
    failNonBlankString(fieldName)
  }
  return mapped
}

private fun failNonBlankString(fieldName: String): Nothing =
  failInvalidScaffoldPayload("Scaffold payload field '$fieldName' must contain only non-empty strings.")

private fun failInvalidScaffoldPayload(message: String): Nothing = throw InvalidScaffoldPayloadError(message)

fun requireStringInPayloadMap(
  map: Map<*, *>,
  fieldLabel: String,
  key: String,
): String {
  val value =
    map[key] as? String
      ?: throw InvalidScaffoldPayloadError("Scaffold payload field '$fieldLabel' must be a non-empty string.")
  if (value.isBlank()) {
    throw InvalidScaffoldPayloadError("Scaffold payload field '$fieldLabel' must be a non-empty string.")
  }
  return value
}

fun displayNameFromSlug(slug: String): String =
  slug.split('-').joinToString(" ") { part ->
    part.replaceFirstChar { ch ->
      if (ch.isLowerCase()) ch.titlecase() else ch.toString()
    }
  }

fun sharedContractNote(): String =
  "Author skill instructions only in sibling `content.md` files. " +
    "Generated `SKILL.md` wrappers and platform pointer files are render/install output."
