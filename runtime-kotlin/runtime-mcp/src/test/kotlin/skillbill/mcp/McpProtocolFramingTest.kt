package skillbill.mcp

import skillbill.contracts.JsonCodec
import skillbill.mcp.core.McpStdioServer
import skillbill.mcp.review.GovernedReviewEvidenceBridge
import kotlin.test.Test
import kotlin.test.assertEquals

class McpProtocolFramingTest {
  @Test
  fun `stdio and governed evidence modes share parse error and ping framing`() {
    val stdioParseError = decode(McpStdioServer.handleLine("{"))
    val stdioPing = decode(McpStdioServer.handleLine("""{"jsonrpc":"2.0","id":1,"method":"ping"}"""))
    val evidenceParseError = decode(
      GovernedReviewEvidenceBridge.handleLine("{") { error("parse errors must not be forwarded") },
    )
    val evidencePing = decode(
      GovernedReviewEvidenceBridge.handleLine("""{"jsonrpc":"2.0","id":1,"method":"ping"}""") {
        error("ping must not be forwarded")
      },
    )

    assertEquals(-32700, JsonCodec.anyToStringAnyMap(stdioParseError["error"])?.get("code"))
    assertEquals(-32700, JsonCodec.anyToStringAnyMap(evidenceParseError["error"])?.get("code"))
    assertEquals(emptyMap<String, Any?>(), JsonCodec.anyToStringAnyMap(stdioPing["result"]))
    assertEquals(emptyMap<String, Any?>(), JsonCodec.anyToStringAnyMap(evidencePing["result"]))
  }

  private fun decode(response: String?): Map<String, Any?> = JsonCodec.anyToStringAnyMap(
    JsonCodec.parseObjectOrNull(requireNotNull(response))?.let(JsonCodec::jsonElementToValue),
  ).orEmpty()
}
