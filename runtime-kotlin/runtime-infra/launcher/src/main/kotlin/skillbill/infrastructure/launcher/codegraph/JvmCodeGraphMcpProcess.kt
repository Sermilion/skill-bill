package skillbill.infrastructure.launcher.codegraph

import com.fasterxml.jackson.databind.node.ObjectNode
import skillbill.ports.codegraph.CodeGraphMcpProcess
import skillbill.ports.codegraph.CodeGraphMcpProcessPort
import java.io.IOException
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import skillbill.contracts.codegraph.CodeGraphMcpKeys as K

private const val METHOD_NOT_FOUND = -32601
private const val RPC_TIMEOUT_SECONDS = 30L
private const val MAX_RESPONSE_CHARS = 2_000_000

internal class JvmCodeGraphMcpProcess : CodeGraphMcpProcessPort {
  override fun start(command: List<String>, repository: Path, environment: Map<String, String>): CodeGraphMcpProcess {
    val process = ProcessBuilder(command).directory(repository.toFile())
      .redirectError(ProcessBuilder.Redirect.DISCARD)
      .apply { environment().putAll(environment) }.start()
    return Handle(process)
  }

  private class Handle(private val process: Process) : CodeGraphMcpProcess {
    private val reader = process.inputStream.bufferedReader()
    private val writer = process.outputStream.bufferedWriter()
    private val executor = Executors.newSingleThreadExecutor { task ->
      Thread(task, "skill-bill-codegraph-rpc").apply { isDaemon = true }
    }
    private val descendants = ConcurrentHashMap.newKeySet<ProcessHandle>()
    override val alive: Boolean get() {
      process.descendants().use { descendants.addAll(it.toList()) }
      return process.isAlive
    }

    @Synchronized
    override fun exchange(request: String): String {
      val id = CodeGraphMcpProtocol.mapper.readTree(request).path(K.ID)
      val future = executor.submit<String> {
        notify(request)
        var matched: String? = null
        while (matched == null) {
          val line = readBoundedLine()
          val response = CodeGraphMcpProtocol.mapper.readTree(line)
          if (response.has(K.METHOD)) {
            if (response.has(K.ID)) {
              val unsupported = CodeGraphMcpProtocol.mapper.createObjectNode()
                .put(K.CODE, METHOD_NOT_FOUND).put(K.MESSAGE, "Client requests are unavailable.")
              val reply = CodeGraphMcpProtocol.mapper.createObjectNode().put(K.JSONRPC, "2.0")
                .set<ObjectNode>(K.ID, response.path(K.ID))
                .set<ObjectNode>(K.ERROR, unsupported)
              notify(reply.toString())
            }
          } else if (response.path(K.ID) == id) {
            matched = line
          }
        }
        matched
      }
      return try {
        future.get(RPC_TIMEOUT_SECONDS, TimeUnit.SECONDS)
      } catch (interrupted: InterruptedException) {
        cancelExchange(future, interrupted)
        Thread.currentThread().interrupt()
        throw interrupted
      } catch (timeout: TimeoutException) {
        cancelExchange(future, timeout)
        throw timeout
      } catch (failed: ExecutionException) {
        cancelExchange(future, failed)
        throw failed
      }
    }

    private fun cancelExchange(future: Future<String>, failure: Exception) {
      future.cancel(true)
      runCatching { close() }.exceptionOrNull()?.let(failure::addSuppressed)
    }

    override fun notify(notification: String) {
      writer.write(notification)
      writer.newLine()
      writer.flush()
    }

    private fun readBoundedLine(): String {
      val result = StringBuilder()
      while (result.length <= MAX_RESPONSE_CHARS) {
        val next = reader.read()
        if (next < 0) throw IOException("CodeGraph MCP stream closed.")
        if (next == '\n'.code) return result.toString()
        result.append(next.toChar())
      }
      throw IOException("CodeGraph MCP response exceeds limit.")
    }

    override fun close() {
      val interrupted = Thread.interrupted()
      try {
        val descendantCleanup = runCatching {
          process.descendants().use { descendants.addAll(it.toList()) }
          descendants.forEach { it.destroyForcibly() }
        }
        process.destroyForcibly()
        if (!process.waitFor(2, TimeUnit.SECONDS)) throw IOException("CodeGraph MCP termination failed.")
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2)
        descendants.forEach { child ->
          if (child.isAlive) child.onExit().get((deadline - System.nanoTime()).coerceAtLeast(1), TimeUnit.NANOSECONDS)
        }
        descendantCleanup.getOrThrow()
      } finally {
        executor.shutdownNow()
        if (interrupted) Thread.currentThread().interrupt()
      }
    }
  }
}
