package skillbill.infrastructure.sqlite.core

internal const val DEGRADED_VALUE_PREVIEW_CHAR_LIMIT = 120

internal fun String.degradedValuePreview(): String = take(DEGRADED_VALUE_PREVIEW_CHAR_LIMIT)
