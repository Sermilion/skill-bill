package skillbill.infrastructure.launcher.codegraph

import com.fasterxml.jackson.databind.JsonNode
import org.junit.jupiter.api.io.TempDir
import skillbill.contracts.codegraph.CodeGraphDegradationReason
import skillbill.ports.codegraph.model.CodeGraphSessionPreparation
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import skillbill.contracts.codegraph.CodeGraphMcpKeys as K

class CodeGraphQueryBoundaryTest {
  @TempDir lateinit var root: Path

  @Test
  fun `scoped endpoint lists only declared tool and forces the repository on queries`() {
    val fixture = CodeGraphBoundaryFixture(root).apply { prepared() }
    val lease = assertIs<CodeGraphSessionPreparation.Ready>(fixture.session.prepare(fixture.request())).lease
    try {
      val listed = rpc(lease.launchConfiguration.endpoint, CodeGraphMcpProtocol.request(5, "tools/list"))
      assertEquals(listOf("codegraph_explore"), listed.path(K.RESULT).path(K.TOOLS).map { it.path(K.NAME).asText() })
      val response = rpc(lease.launchConfiguration.endpoint, query("codegraph_explore"))
      assertEquals("fresh source", response.path(K.RESULT).path(K.CONTENT).first().path(K.TEXT).asText())
      val sent = CodeGraphMcpProtocol.mapper.readTree(fixture.process.requests.last())
      assertEquals(root.toString(), sent.path(K.PARAMS).path(K.ARGUMENTS).path(K.PROJECT_PATH).asText())
      assertEquals(1, fixture.starts)
      val denied = rpc(lease.launchConfiguration.endpoint, query("undeclared_tool"))
      assertTrue(denied.path(K.RESULT).path(K.IS_ERROR).asBoolean())
      assertContains(fixture.persisted(), "unavailable_capability")
    } finally {
      lease.close()
    }
  }

  @Test
  fun `pending synchronization and query failures return visible fallback without stale source`() {
    listOf("pending", "error", "transport", "crash").forEach { outcome ->
      val directory = Files.createDirectory(root.resolve(outcome))
      val file = Files.writeString(directory.resolve("current.kt"), "ordinary current source")
      val fixture = CodeGraphBoundaryFixture(directory).apply {
        prepared()
        process.queryText = if (outcome == "pending") {
          "### Pending sync: current.kt\nstale secret source"
        } else {
          "secret error"
        }
        process.queryError = outcome == "error"
        if (outcome == "transport") process.queryFailure = IllegalStateException("secret failure")
      }
      val lease = assertIs<CodeGraphSessionPreparation.Ready>(fixture.session.prepare(fixture.request())).lease
      try {
        if (outcome == "crash") fixture.process.alive = false
        val response = rpc(lease.launchConfiguration.endpoint, query("codegraph_explore"))
        assertTrue(response.path(K.RESULT).path(K.IS_ERROR).asBoolean())
        assertContains(response.toString(), "ordinary file tools")
        assertFalse(response.toString().contains("secret"))
        val expected = if (outcome == "pending") {
          CodeGraphDegradationReason.PENDING_SYNCHRONIZATION
        } else {
          CodeGraphDegradationReason.QUERY_FAILURE
        }
        assertEquals(expected, lease.activeObservation.degradation?.reason)
        assertEquals("ordinary current source", Files.readString(file))
      } finally {
        lease.close()
      }
      assertContains(fixture.persisted(), if (outcome == "pending") "pending_synchronization" else "query_failure")
      assertFalse(fixture.persisted().contains("secret"))
    }
  }

  private fun query(tool: String): JsonNode {
    val params = CodeGraphMcpProtocol.mapper.createObjectNode().put(K.NAME, tool)
    params.putObject(K.ARGUMENTS).put(K.PROJECT_PATH, "/outside/repository")
    return CodeGraphMcpProtocol.request(8, "tools/call", params)
  }

  private fun rpc(endpoint: String, body: JsonNode): JsonNode {
    val request = HttpRequest.newBuilder(URI(endpoint)).header("Content-Type", "application/json")
      .POST(HttpRequest.BodyPublishers.ofString(body.toString())).build()
    val response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString())
    assertEquals(200, response.statusCode())
    return CodeGraphMcpProtocol.mapper.readTree(response.body())
  }
}
