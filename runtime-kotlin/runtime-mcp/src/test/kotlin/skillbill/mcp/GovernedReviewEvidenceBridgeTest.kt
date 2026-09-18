package skillbill.mcp

import skillbill.contracts.JsonCodec
import skillbill.error.GovernedReviewEvidenceTransportError
import skillbill.mcp.review.GovernedReviewEvidenceBridge
import skillbill.mcp.review.GovernedReviewEvidenceConnection
import skillbill.ports.review.model.GovernedReviewEvidenceCodec
import java.net.StandardProtocolFamily
import java.net.UnixDomainSocketAddress
import java.nio.channels.Channels
import java.nio.channels.ServerSocketChannel
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class GovernedReviewEvidenceBridgeTest {
  @Test
  fun `the bridge advertises exactly the two governed operations`() {
    val reply = requireNotNull(
      GovernedReviewEvidenceBridge.handleLine(
        JsonCodec.mapToJsonString(linkedMapOf("jsonrpc" to "2.0", "id" to 1, "method" to "tools/list")),
      ) { error("tools/list must not be forwarded") },
    )

    val result = JsonCodec.anyToStringAnyMap(
      JsonCodec.parseObjectOrNull(reply)?.get("result")?.let(JsonCodec::jsonElementToValue),
    ).orEmpty()
    val tools = requireNotNull(JsonCodec.anyToStringAnyMapList(result["tools"]))
    assertEquals(listOf("read_evidence", "request_expansion"), tools.map { it["name"] })
  }

  @Test
  fun `a call to any other tool is refused instead of forwarded`() {
    var forwarded = false
    val reply = requireNotNull(
      GovernedReviewEvidenceBridge.handleLine(
        JsonCodec.mapToJsonString(
          linkedMapOf(
            "jsonrpc" to "2.0",
            "id" to 2,
            "method" to "tools/call",
            "params" to mapOf("name" to "Read", "arguments" to mapOf("path" to "/etc/passwd")),
          ),
        ),
      ) {
        forwarded = true
        "{}"
      },
    )

    assertTrue(!forwarded)
    assertTrue(reply.contains("\"error\""))
  }

  @Test
  fun `a forwarded response over the frame limit is rejected by a real unix socket`() {
    val socketPath = Files.createTempFile("skillbill-evidence", ".sock")
    Files.delete(socketPath)
    val server = ServerSocketChannel.open(StandardProtocolFamily.UNIX)
    server.bind(UnixDomainSocketAddress.of(socketPath))
    val thread = Thread {
      server.use {
        it.accept().use { channel ->
          val reader = Channels.newInputStream(channel).bufferedReader()
          val writer = Channels.newOutputStream(channel).bufferedWriter()
          reader.readLine()
          writer.appendLine("x".repeat(GovernedReviewEvidenceCodec.RESPONSE_FRAME_BYTES + 1))
          writer.flush()
        }
      }
    }
    thread.isDaemon = true
    thread.start()
    assertFailsWith<GovernedReviewEvidenceTransportError> {
      GovernedReviewEvidenceConnection.connect(socketPath, "token").use { connection ->
        connection.forward("{}")
      }
    }
    thread.join(5_000)
  }

  @Test
  fun `a handshake refusal is reported as a transport error`() {
    val socketPath = Files.createTempFile("skillbill-evidence-refusal", ".sock")
    Files.delete(socketPath)
    val server = ServerSocketChannel.open(StandardProtocolFamily.UNIX)
    server.bind(UnixDomainSocketAddress.of(socketPath))
    val thread = Thread {
      server.use {
        it.accept().use { channel ->
          Channels.newInputStream(channel).bufferedReader().readLine()
        }
      }
    }
    thread.start()
    assertFailsWith<GovernedReviewEvidenceTransportError> {
      GovernedReviewEvidenceConnection.connect(socketPath, "token")
    }
    thread.join()
  }
}
