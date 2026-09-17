package skillbill.engine.featuretask

fun auditRetryFocusDirective(focusHint: String?): String {
  if (focusHint.isNullOrBlank()) return ""
  return """
    ## Prior audit focus hint (remaining criteria only)
    The previous audit session returned this remaining-criteria text. Use it only as a focus hint while
    you re-check every planned acceptance criterion from scratch and repair fixable gaps in this same
    session, including files outside scoped_owned_paths. scoped_owned_paths is the last checkpoint's
    evidence, not a write allowlist.
    $focusHint
  """.trimIndent()
}
