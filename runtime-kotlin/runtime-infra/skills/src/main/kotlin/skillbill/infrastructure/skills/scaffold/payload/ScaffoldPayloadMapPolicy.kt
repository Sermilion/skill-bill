package skillbill.infrastructure.skills.scaffold.payload
import skillbill.error.shellcontent.InvalidScaffoldPayloadError
import skillbill.error.shellcontent.ScaffoldPayloadVersionMismatchError
import skillbill.error.shellcontent.UnknownPreShellFamilyError
import skillbill.error.shellcontent.UnknownSkillKindError
import skillbill.scaffold.model.SkillKind
import skillbill.scaffold.policy.ACTIVE_CREATION_SKILL_KINDS
import skillbill.scaffold.policy.RETIRED_CODE_REVIEW_AREA_KIND_ALIASES
import skillbill.scaffold.policy.RETIRED_PLATFORM_OVERRIDE_KIND_ALIASES
import skillbill.scaffold.policy.SCAFFOLD_PAYLOAD_VERSION
import skillbill.scaffold.policy.rejectRetiredPartialScaffoldKind

internal fun validatePayloadVersion(payload: Map<String, Any?>) {
  val version =
    payload["scaffold_payload_version"] as? String
      ?: throw InvalidScaffoldPayloadError(
        "Scaffold payload is missing required field 'scaffold_payload_version'.",
      )
  if (version != SCAFFOLD_PAYLOAD_VERSION) {
    throw ScaffoldPayloadVersionMismatchError(
      "Scaffold payload declares 'scaffold_payload_version' '$version' " +
        "but the scaffolder expects '$SCAFFOLD_PAYLOAD_VERSION'.",
    )
  }
}

internal fun detectKind(payload: Map<String, Any?>): String {
  val kind =
    payload["kind"] as? String
      ?: throw InvalidScaffoldPayloadError(
        "Scaffold payload field 'kind' must be a non-empty string.",
      )
  rejectRetiredFeatureImplementFamily(payload["family"])
  if (kind.trim().lowercase() in RETIRED_PLATFORM_OVERRIDE_KIND_ALIASES) {
    rejectRetiredPartialScaffoldKind(kind)
  }
  if (kind.trim().lowercase() in RETIRED_CODE_REVIEW_AREA_KIND_ALIASES) {
    rejectRetiredPartialScaffoldKind(kind)
  }
  val skillKind = SkillKind.fromWire(kind)
  if (skillKind.wireValue !in ACTIVE_CREATION_SKILL_KINDS) {
    throw UnknownSkillKindError(
      "Scaffold payload declares unsupported kind '$kind'. " +
        "Supported kinds: $ACTIVE_CREATION_SKILL_KINDS.",
    )
  }
  return skillKind.wireValue
}

private fun rejectRetiredFeatureImplementFamily(family: Any?) {
  val retiredFeatureImplement = "feature-" + "implement"
  if (family == retiredFeatureImplement) {
    throw UnknownPreShellFamilyError(
      "Scaffold payload declares pre-shell family '$retiredFeatureImplement'. Use 'feature-task' instead.",
    )
  }
}

internal fun requireStringMap(
  payload: Map<String, Any?>,
  key: String,
): String =
  (payload[key] as? String)?.takeIf { it.isNotBlank() }
    ?: throw InvalidScaffoldPayloadError(
      "Scaffold payload field '$key' must be a non-empty string.",
    )

internal fun requireStringOrDefaultMap(
  payload: Map<String, Any?>,
  key: String,
  default: String,
): String = (payload[key] as? String)?.takeIf { it.isNotBlank() } ?: default

internal fun rejectBaselineLayersForNonPlatformPack(
  payload: Map<String, Any?>,
  kind: String,
) {
  if (payload.containsKey("baseline_layers")) {
    throw InvalidScaffoldPayloadError(
      "Scaffold payload field 'baseline_layers' is only supported for kind 'platform-pack'; got '$kind'.",
    )
  }
}

internal fun requireStringListPayload(
  value: Any?,
  fieldName: String,
): List<String> {
  if (value !is List<*>) {
    throw InvalidScaffoldPayloadError(
      "Scaffold payload field '$fieldName' must be a list of strings.",
    )
  }
  return value.map { liftNonBlankString(it, fieldName) }
}

private fun liftNonBlankString(
  value: Any?,
  fieldName: String,
): String {
  val string =
    value as? String
      ?: throw InvalidScaffoldPayloadError(
        "Scaffold payload field '$fieldName' must contain only non-empty strings.",
      )
  if (string.isBlank()) {
    throw InvalidScaffoldPayloadError(
      "Scaffold payload field '$fieldName' must contain only non-empty strings.",
    )
  }
  return string
}
