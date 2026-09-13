package skillbill.engine.featuretask

fun auditRetryFocusDirective(focusHint: String?): String {
  if (focusHint.isNullOrBlank()) return ""
  return """
    ## Prior audit focus hint (remaining criteria only)
    The previous audit session returned this remaining-criteria text. Use it only as a focus hint while
    you re-check every planned acceptance criterion from scratch and repair fixable gaps in this same
    session. Do not treat it as authoritative scope, evidence, or a substitute for full-list verification.
    $focusHint
  """.trimIndent()
}
