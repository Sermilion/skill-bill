package skillbill.mcp

object ReadianMcpRuntime {
  private const val AUTHENTICATED_ENV = "SKILL_BILL_READIAN_AUTHENTICATED"

  fun authStatus(context: McpRuntimeContext): Map<String, Any?> {
    val authenticated = context.environment[AUTHENTICATED_ENV].isTruthy()
    return logSafePayload(
      linkedMapOf(
        "status" to if (authenticated) "ok" else "auth_required",
        "authenticated" to authenticated,
        "auth_required" to !authenticated,
        "boundary" to "readian_mcp",
        "credential_handling" to
          "Readian credentials, refresh tokens, session cookies, and auth headers stay below the MCP boundary.",
      ),
    )
  }

  fun call(toolName: String, arguments: Map<String, Any?>, context: McpRuntimeContext): Map<String, Any?> {
    if (!context.environment[AUTHENTICATED_ENV].isTruthy()) {
      return authRequired(toolName, arguments)
    }

    val data =
      when (toolName) {
        "readian_get_today_feed" -> linkedMapOf(
          "items" to emptyList<Map<String, Any?>>(),
          "note" to "Readian MCP boundary is authenticated; feed adapter returned no fixture data in this runtime.",
        )
        "readian_get_recommendations" -> linkedMapOf(
          "recommendations" to emptyList<Map<String, Any?>>(),
          "note" to
            "Readian MCP boundary is authenticated; recommendation adapter returned no fixture data in this runtime.",
        )
        "readian_get_article" -> linkedMapOf(
          "article_id" to arguments["article_id"],
          "article" to null,
          "note" to "Readian MCP boundary is authenticated; article adapter returned no fixture data in this runtime.",
        )
        "readian_save_candidate" -> linkedMapOf(
          "candidate_id" to arguments["candidate_id"],
          "saved" to false,
          "note" to
            "Readian MCP boundary accepted the request; write adapter is not configured in this runtime.",
        )
        "readian_mark_story_status" -> linkedMapOf(
          "story_id" to arguments["story_id"],
          "status_value" to arguments["status"],
          "updated" to false,
          "note" to
            "Readian MCP boundary accepted the request; status adapter is not configured in this runtime.",
        )
        else -> error("Unknown Readian MCP tool '$toolName'.")
      }
    return logSafePayload(okPayload(toolName, arguments, data))
  }

  internal fun logSafePayload(payload: Map<String, Any?>): Map<String, Any?> {
    @Suppress("UNCHECKED_CAST")
    return ReadianSecretRedactor.redact(payload) as Map<String, Any?>
  }

  private fun authRequired(toolName: String, arguments: Map<String, Any?>): Map<String, Any?> = logSafePayload(
    linkedMapOf(
      "status" to "auth_required",
      "auth_required" to true,
      "tool" to toolName,
      "error" to linkedMapOf(
        "code" to "auth_required",
        "message" to "Readian authentication is required inside the Readian MCP boundary before this tool can run.",
      ),
      "next_action" to "Authenticate Readian through the MCP server, then retry the workflow.",
      "log_safe_arguments" to arguments,
    ),
  )

  private fun okPayload(toolName: String, arguments: Map<String, Any?>, data: Map<String, Any?>): Map<String, Any?> =
    linkedMapOf(
      "status" to "ok",
      "auth_required" to false,
      "tool" to toolName,
      "data" to data,
      "log_safe_arguments" to arguments,
    )

  private fun String?.isTruthy(): Boolean = this?.lowercase() in setOf("1", "true", "yes", "authenticated")
}
