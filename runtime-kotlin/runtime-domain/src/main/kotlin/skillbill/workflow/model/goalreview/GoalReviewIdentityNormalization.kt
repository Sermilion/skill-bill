package skillbill.workflow.model.goalreview

private val IDENTITY_WHITESPACE = Regex("\\s+")

internal fun normalizeIdentityPart(part: String): String = part.trim().lowercase().replace(IDENTITY_WHITESPACE, " ")
