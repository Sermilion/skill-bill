package skillbill.mcp.core

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import skillbill.contracts.JsonCodec
import skillbill.contracts.SharedPayloadKeys
import skillbill.contracts.mcp.McpToolPayloadKeys
import skillbill.error.ShellContentContractException
import skillbill.mcp.shared.McpComponent
import skillbill.mcp.shared.McpProtocolFramer
import skillbill.mcp.shared.McpRuntimeLifecycle
import skillbill.mcp.shared.componentForLegacyContext

internal object McpStdioServer {
  internal fun handleLine(line: String): String? {
    val message = JsonCodec.parseObjectOrNull(line)
    val id = message?.get(McpProtocolFramer.ID_KEY)
    val method = message?.get(McpProtocolFramer.METHOD_KEY)
      ?.let(JsonCodec::jsonElementToValue)?.toString().orEmpty()
    return when {
      message == null -> McpProtocolFramer.errorResponse(null, McpProtocolFramer.PARSE_ERROR, "Parse error")
      id == null -> null
      method == "initialize" -> McpProtocolFramer.successResponse(
        id,
        McpProtocolFramer.initialize("skill-bill"),
      )
      method == "ping" -> McpProtocolFramer.successResponse(id, emptyMap())
      method == "tools/list" -> McpProtocolFramer.successResponse(
        id,
        McpProtocolFramer.toolsList(McpToolRegistry.tools.map(McpToolSpec::toPayload)),
      )
      method == "tools/call" -> callToolResponse(id, message.arguments(), null)
      else -> McpProtocolFramer.errorResponse(
        id,
        McpProtocolFramer.METHOD_NOT_FOUND,
        "Method not found: $method",
      )
    }
  }

  internal fun handleLine(line: String, context: Any): String? = handleLine(line, componentForLegacyContext(context))

  internal fun run(component: McpComponent) {
    generateSequence(::readlnOrNull).forEach { line ->
      handleLine(line, component)?.let(::println)
    }
  }

  internal fun handleLine(line: String, component: McpComponent): String? {
    val message = JsonCodec.parseObjectOrNull(line)
    val id = message?.get(McpProtocolFramer.ID_KEY)
    val method = message?.get(McpProtocolFramer.METHOD_KEY)
      ?.let(JsonCodec::jsonElementToValue)?.toString().orEmpty()
    return when {
      message == null -> McpProtocolFramer.errorResponse(null, McpProtocolFramer.PARSE_ERROR, "Parse error")
      id == null -> null
      method == "initialize" -> McpProtocolFramer.successResponse(
        id,
        McpProtocolFramer.initialize("skill-bill"),
      )
      method == "ping" -> McpProtocolFramer.successResponse(id, emptyMap())
      method == "tools/list" -> McpProtocolFramer.successResponse(
        id,
        McpProtocolFramer.toolsList(McpToolRegistry.tools.map(McpToolSpec::toPayload)),
      )
      method == "tools/call" -> callToolResponse(id, message.arguments(), component)
      else -> McpProtocolFramer.errorResponse(
        id,
        McpProtocolFramer.METHOD_NOT_FOUND,
        "Method not found: $method",
      )
    }
  }

  private fun callToolResponse(id: JsonElement, params: Map<String, Any?>, component: McpComponent?): String =
    McpProtocolFramer.successResponse(id, callToolResult(params, component))

  private fun callToolResult(params: Map<String, Any?>, component: McpComponent?): Map<String, Any?> {
    val toolName = params["name"]?.toString().orEmpty()
    val arguments = JsonCodec.anyToStringAnyMap(params["arguments"]).orEmpty()
    val normalizedArguments = runCatching {
      McpToolDispatcher.validateMcpToolArguments(toolName, arguments)
    }.getOrElse { error -> return mcpToolErrorResult(toolName, error) }
    if (component == null) {
      return runCatching {
        McpToolDispatcher.handlerFor(toolName)
        mcpToolErrorResult(toolName, IllegalStateException("A component is required for tool calls."))
      }.getOrElse { error -> mcpToolErrorResult(toolName, error) }
    }
    return dispatchMcpToolCall(toolName, normalizedArguments, component)
  }

  private fun JsonObject.arguments(): Map<String, Any?> =
    JsonCodec.anyToStringAnyMap(this[McpProtocolFramer.PARAMS_KEY]?.let(JsonCodec::jsonElementToValue)).orEmpty()
}

private fun dispatchMcpToolCall(
  toolName: String,
  arguments: Map<String, Any?>,
  component: McpComponent,
): Map<String, Any?> {
  val outcome = runCatching {
    val payload = McpToolDispatcher.callValidated(toolName, arguments, component)
    mcpToolResult(payload, isError = false)
  }
  return outcome.fold(
    onSuccess = { it },
    onFailure = { error ->
      when (error) {
        is ShellContentContractException, is IllegalArgumentException, is IllegalStateException ->
          mcpToolErrorResult(toolName, error)
        is Exception -> {
          McpRuntimeLifecycle.captureException(workflowPhase = toolName, error = error, component = component)
          mcpToolErrorResult(toolName, error)
        }
        else -> throw error
      }
    },
  )
}

private fun mcpToolErrorResult(toolName: String, error: Throwable): Map<String, Any?> = mcpToolResult(
  mapOf(
    SharedPayloadKeys.STATUS to "error",
    McpToolPayloadKeys.TOOL to toolName,
    McpToolPayloadKeys.ERROR to error.message.orEmpty(),
  ),
  isError = true,
)

private fun mcpToolResult(payload: Map<String, Any?>, isError: Boolean): Map<String, Any?> = linkedMapOf(
  McpToolPayloadKeys.CONTENT to listOf(
    mapOf(
      McpToolPayloadKeys.TYPE to "text",
      McpToolPayloadKeys.TEXT to JsonCodec.mapToJsonString(payload),
    ),
  ),
  McpToolPayloadKeys.IS_ERROR to isError,
)
