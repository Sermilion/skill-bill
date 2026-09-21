package skillbill.infrastructure.launcher.codegraph

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import skillbill.contracts.codegraph.CodeGraphDegradationReason
import skillbill.ports.codegraph.CodeGraphMcpProcess
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.nio.file.Path
import java.util.UUID
import java.util.concurrent.Executors
import kotlin.coroutines.cancellation.CancellationException
import skillbill.contracts.codegraph.CodeGraphMcpKeys as K

private const val MAX_REQUEST_BYTES = 262_144

internal class CodeGraphMcpEndpoint(
  private val process: CodeGraphMcpProcess,
  private val repository: Path,
  private val capability: String,
  private val degrade: (CodeGraphDegradationReason) -> Unit,
) : AutoCloseable {
  private val mapper = CodeGraphMcpProtocol.mapper
  private val executor = Executors.newSingleThreadExecutor { task ->
    Thread(task, "skill-bill-codegraph-http").apply { isDaemon = true }
  }
  private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
  private val path = "/${UUID.randomUUID()}/mcp"
  val url: String = "http://127.0.0.1:${server.address.port}$path"
  private var sequence = 2
  private lateinit var tools: JsonNode

  fun start() {
    val initialized = mapper.readTree(
      process.exchange(
        CodeGraphMcpProtocol.request(0, "initialize", CodeGraphMcpProtocol.initialize()).toString(),
      ),
    )
    check(!initialized.has(K.ERROR) && initialized.path(K.RESULT).has(K.PROTOCOL_VERSION))
    process.notify(CodeGraphMcpProtocol.request(0, "notifications/initialized").also { it.remove(K.ID) }.toString())
    val listed = mapper.readTree(process.exchange(CodeGraphMcpProtocol.request(1, "tools/list").toString()))
    val declared = listed.path(K.RESULT).path(K.TOOLS).filter { it.path(K.NAME).asText() == capability }
    if (declared.size != 1) throw CodeGraphCapabilityUnavailable()
    tools = mapper.createObjectNode().also { body -> body.putArray(K.TOOLS).add(declared.single()) }
    server.executor = executor
    server.createContext(path, ::handle)
    server.start()
  }

  private fun handle(exchange: HttpExchange) {
    exchange.use {
      if (exchange.requestURI.path != path || exchange.requestHeaders.getFirst("Origin") != null) {
        exchange.sendResponseHeaders(HttpURLConnection.HTTP_FORBIDDEN, -1)
        return
      }
      if (exchange.requestMethod != "POST") {
        exchange.sendResponseHeaders(HttpURLConnection.HTTP_BAD_METHOD, -1)
        return
      }
      try {
        val bytes = exchange.requestBody.readNBytes(MAX_REQUEST_BYTES + 1)
        if (bytes.size > MAX_REQUEST_BYTES) {
          exchange.sendResponseHeaders(HttpURLConnection.HTTP_ENTITY_TOO_LARGE, -1)
          return
        }
        val request = mapper.readTree(bytes)
        if (!request.has(K.ID)) {
          exchange.sendResponseHeaders(HttpURLConnection.HTTP_ACCEPTED, -1)
          return
        }
        val response = dispatch(request)
        val body = response.toString().toByteArray()
        exchange.responseHeaders.set("Content-Type", "application/json")
        exchange.sendResponseHeaders(HttpURLConnection.HTTP_OK, body.size.toLong())
        exchange.responseBody.write(body)
      } catch (cancellation: CancellationException) {
        throw cancellation
      } catch (_: Exception) {
        degrade(CodeGraphDegradationReason.QUERY_FAILURE)
      }
    }
  }

  internal fun dispatch(request: JsonNode): JsonNode {
    val id = request.path(K.ID)
    return when (request.path(K.METHOD).asText()) {
      "initialize" -> CodeGraphMcpProtocol.result(
        id,
        mapper.createObjectNode().also {
          it.put(K.PROTOCOL_VERSION, CodeGraphMcpProtocol.PROTOCOL)
          it.put(
            K.INSTRUCTIONS,
            "Use codegraph_explore for repository navigation. On an error result, use ordinary file tools. " +
              "Fallback is not CodeGraph evidence.",
          )
          it.putObject(K.CAPABILITIES).putObject(K.TOOLS)
          it.putObject(K.SERVER_INFO).put(K.NAME, "skill-bill-codegraph").put(K.VERSION, "1")
        },
      )
      "tools/list" -> CodeGraphMcpProtocol.result(id, tools)
      "ping" -> CodeGraphMcpProtocol.result(id, mapper.createObjectNode())
      "tools/call" -> query(request)
      else -> CodeGraphMcpProtocol.fallback(id, "Unsupported CodeGraph operation. Use ordinary file tools.")
    }
  }

  private fun query(request: JsonNode): JsonNode {
    val id = request.path(K.ID)
    if (request.path(K.PARAMS).path(K.NAME).asText() != capability) {
      degrade(CodeGraphDegradationReason.UNAVAILABLE_CAPABILITY)
      return CodeGraphMcpProtocol.fallback(id, "CodeGraph capability unavailable. Use ordinary file tools.")
    }
    return try {
      val params = request.path(K.PARAMS).deepCopy<ObjectNode>()
      val arguments = params.path(K.ARGUMENTS).takeIf { it.isObject }?.deepCopy<ObjectNode>()
        ?: mapper.createObjectNode()
      arguments.put(K.PROJECT_PATH, repository.toString())
      params.set<ObjectNode>(K.ARGUMENTS, arguments)
      val reply = mapper.readTree(
        process.exchange(CodeGraphMcpProtocol.request(sequence++, "tools/call", params).toString()),
      )
      val body = reply.path(K.RESULT)
      val text = body.path(K.CONTENT).joinToString("\n") { it.path(K.TEXT).asText() }
      val reason = when {
        CodeGraphReadinessParser.pending(text) -> CodeGraphDegradationReason.PENDING_SYNCHRONIZATION
        reply.has(K.ERROR) || body.path(K.IS_ERROR).asBoolean() || !body.has(K.CONTENT) ->
          CodeGraphDegradationReason.QUERY_FAILURE
        else -> null
      }
      if (reason == null) {
        CodeGraphMcpProtocol.result(id, body)
      } else {
        degrade(reason)
        CodeGraphMcpProtocol.fallback(
          id,
          "CodeGraph ${reason.wireValue}. Read affected files with ordinary file tools.",
        )
      }
    } catch (cancellation: CancellationException) {
      throw cancellation
    } catch (_: Exception) {
      degrade(CodeGraphDegradationReason.QUERY_FAILURE)
      CodeGraphMcpProtocol.fallback(id, "CodeGraph query failed. Use ordinary file tools.")
    }
  }

  override fun close() {
    server.stop(0)
    executor.shutdownNow()
  }
}

internal class CodeGraphCapabilityUnavailable : IllegalStateException("Declared CodeGraph capability unavailable.")
