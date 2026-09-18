package skillbill.mcp.core

import kotlinx.serialization.json.JsonObject
import skillbill.contracts.JsonCodec
import skillbill.contracts.SharedPayloadKeys
import skillbill.contracts.mcp.McpToolPayloadKeys
import skillbill.error.ShellContentContractException
import skillbill.error.InvalidMcpToolArgumentError
import skillbill.mcp.shared.McpComponent
import skillbill.mcp.shared.McpRuntimeLifecycle
import skillbill.mcp.shared.componentForLegacyContext
import skillbill.mcp.shared.validateDeclaredArguments

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

  internal fun handleLine(line: String, context: Any): String? =
    handleLine(line, componentForLegacyContext(context))

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

  private fun callToolResponse(
    id: kotlinx.serialization.json.JsonElement,
    params: Map<String, Any?>,
    component: McpComponent?,
  ): String =
    McpProtocolFramer.successResponse(id, callToolResult(params, component))

  private fun callToolResult(params: Map<String, Any?>, component: McpComponent?): Map<String, Any?> {
    val toolName = params["name"]?.toString().orEmpty()
    val arguments = JsonCodec.anyToStringAnyMap(params["arguments"]).orEmpty()
    try {
      val schema = McpToolRegistry.toolNamed(toolName)?.inputSchema
      if (schema != null) {
        validateDeclaredArguments(toolName, arguments, schema)
      }
      validateStrictArguments(params)
    } catch (error: Exception) {
      return mcpToolErrorResult(toolName, error)
    }
    if (component == null) {
      return try {
        McpToolDispatcher.handlerFor(toolName)
        mcpToolErrorResult(toolName, IllegalStateException("A component is required for tool calls."))
      } catch (error: Exception) {
        mcpToolErrorResult(toolName, error)
      }
    }
    return dispatchMcpToolCall(toolName, arguments, component)
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
    val payload = McpToolDispatcher.call(toolName, arguments, component)
    mcpToolResult(payload, isError = false)
  }
  if (outcome.isSuccess) return outcome.getOrThrow()
  val error = outcome.exceptionOrNull()!!
  return when (error) {
    is ShellContentContractException, is IllegalArgumentException, is IllegalStateException ->
      mcpToolErrorResult(toolName, error)
    is Exception -> {
      McpRuntimeLifecycle.captureException(workflowPhase = toolName, error = error, component = component)
      mcpToolErrorResult(toolName, error)
    }
    else -> throw error
  }
}

private fun mcpToolErrorResult(toolName: String, error: Exception): Map<String, Any?> = mcpToolResult(
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

private fun validateStrictArguments(params: Map<String, Any?>) {
  val toolName = params["name"]?.toString().orEmpty()
  val arguments = JsonCodec.anyToStringAnyMap(params["arguments"]).orEmpty()
  val schema = McpToolRegistry.toolNamed(toolName)?.inputSchema
  val unknownArguments = schema?.let { unknownProperties(arguments, it, path = "") }.orEmpty()
  unknownArguments.firstOrNull()?.let {
    throw InvalidMcpToolArgumentError(
      toolName = toolName,
      argumentKey = it,
      detail = "is not declared",
    )
  }
}

private fun unknownProperties(value: Any?, schema: Map<String, Any?>, path: String): List<String> {
  val objectValue = JsonCodec.anyToStringAnyMap(value)
  val arrayValue = value as? List<*>
  return when {
    objectValue != null -> unknownObjectProperties(objectValue, schema, path)
    arrayValue != null -> unknownArrayProperties(arrayValue, schema, path)
    else -> emptyList()
  }
}

private fun unknownObjectProperties(value: Map<String, Any?>, schema: Map<String, Any?>, path: String): List<String> {
  val properties = JsonCodec.anyToStringAnyMap(schema["properties"]).orEmpty()
  val localUnknown = if (schema["additionalProperties"] == false) {
    value.keys.filterNot(properties::containsKey).sorted().map { propertyName ->
      if (path.isBlank()) propertyName else "$path.$propertyName"
    }
  } else {
    emptyList()
  }
  val nestedUnknown = value.flatMap { (propertyName, propertyValue) ->
    JsonCodec.anyToStringAnyMap(properties[propertyName])?.let { propertySchema ->
      unknownProperties(propertyValue, propertySchema, nestedPath(path, propertyName))
    }.orEmpty()
  }
  return localUnknown + nestedUnknown
}

private fun unknownArrayProperties(value: List<*>, schema: Map<String, Any?>, path: String): List<String> {
  val itemSchema = JsonCodec.anyToStringAnyMap(schema["items"]) ?: return emptyList()
  return value.flatMapIndexed { index, item ->
    unknownProperties(item, itemSchema, "$path[$index]")
  }
}

private fun nestedPath(parent: String, child: String): String = if (parent.isBlank()) child else "$parent.$child"
