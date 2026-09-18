package skillbill.infrastructure.http

internal object HttpHeaderNames {
  const val AUTHORIZATION: String = "Authorization"
  const val CONTENT_TYPE: String = "Content-Type"
  const val USER_AGENT: String = "User-Agent"
}

internal const val TELEMETRY_USER_AGENT: String = "skill-bill-telemetry/1.0"
internal const val INSTALLER_USER_AGENT: String = "skill-bill-update"
internal const val JSON_CONTENT_TYPE: String = "application/json"
