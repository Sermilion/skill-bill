package skillbill.mcp.review

import kotlinx.serialization.json.JsonObject
import skillbill.contracts.JsonCodec
import skillbill.contracts.review.GovernedReviewEvidenceContracts
import skillbill.mcp.shared.McpProtocolFramer
import java.nio.file.Path

internal object GovernedReviewEvidenceBridge {
  fun enabled(environment: Map<String, String>): Boolean =
    !environment[GovernedReviewEvidenceContracts.SOCKET_ENV].isNullOrBlank()

  fun run(environment: Map<String, String>) {
    val socketPath = environment[GovernedReviewEvidenceContracts.SOCKET_ENV].orEmpty()
    val token = environment[GovernedReviewEvidenceContracts.TOKEN_ENV].orEmpty()
    GovernedReviewEvidenceConnection.connect(Path.of(socketPath), token).use { connection ->
      generateSequence(::readlnOrNull).forEach { line ->
        handleLine(line) { frame -> connection.forward(frame) }?.let(::println)
      }
    }
  }

  fun handleLine(
    line: String,
    forward: (String) -> String?,
  ): String? {
    val message = JsonCodec.parseObjectOrNull(line)
    val id = message?.get("id")?.let(JsonCodec::jsonElementToValue)
    val method = message?.get("method")?.let(JsonCodec::jsonElementToValue)?.toString().orEmpty()
    return when {
      message == null -> McpProtocolFramer.errorResponse(null, McpProtocolFramer.PARSE_ERROR, "Parse error")
      id == null -> null
      method == "initialize" ->
        McpProtocolFramer.successResponse(
          id,
          McpProtocolFramer.initialize(GovernedReviewEvidenceContracts.SERVER_NAME),
        )
      method == "ping" -> McpProtocolFramer.successResponse(id, emptyMap())
      method == "tools/list" ->
        forward(line)
          ?: McpProtocolFramer.errorResponse(
            id,
            McpProtocolFramer.INTERNAL_ERROR,
            "Governed review evidence endpoint closed.",
          )
      method == "tools/call" -> forwardToolCall(id, message.toolName(), line, forward)
      else ->
        McpProtocolFramer.errorResponse(
          id,
          McpProtocolFramer.METHOD_NOT_FOUND,
          "Method not found: $method",
        )
    }
  }

  private fun forwardToolCall(
    id: Any?,
    name: String,
    line: String,
    forward: (String) -> String?,
  ): String =
    if (name in GovernedReviewEvidenceContracts.OPERATIONS) {
      forward(line) ?: McpProtocolFramer.errorResponse(
        id,
        McpProtocolFramer.INTERNAL_ERROR,
        "Governed review evidence endpoint closed.",
      )
    } else {
      McpProtocolFramer.errorResponse(
        id,
        McpProtocolFramer.METHOD_NOT_FOUND,
        "Unknown governed operation: $name",
      )
    }

  private fun JsonObject.toolName(): String =
    JsonCodec.anyToStringAnyMap(this[McpProtocolFramer.PARAMS_KEY]?.let(JsonCodec::jsonElementToValue))
      .orEmpty()["name"]?.toString().orEmpty()
}
