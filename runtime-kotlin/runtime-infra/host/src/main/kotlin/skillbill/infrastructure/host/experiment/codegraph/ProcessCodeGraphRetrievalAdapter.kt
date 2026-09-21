package skillbill.infrastructure.host.experiment.codegraph

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.ObjectMapper
import skillbill.contracts.experiment.codegraph.CODEGRAPH_QUERY_RECEIPT_CONTRACT_VERSION
import skillbill.contracts.experiment.codegraph.CodeGraphDependencyPayloadKeys
import skillbill.contracts.experiment.codegraph.CodeGraphQueryOutputPayloadKeys
import skillbill.contracts.experiment.codegraph.CodeGraphQueryReceiptPayloadKeys
import skillbill.error.shellcontent.CodeGraphRetrievalRefusalError
import skillbill.infrastructure.contracts.experiment.codegraph.CodeGraphQueryOutputSchemaValidator
import skillbill.infrastructure.contracts.experiment.codegraph.CodeGraphQueryReceiptSchemaValidator
import skillbill.ports.experiment.codegraph.CodeGraphRetrievalPort
import skillbill.ports.experiment.codegraph.CodeGraphUsageLedgerPort
import skillbill.ports.experiment.codegraph.model.CodeGraphCandidateHit
import skillbill.ports.experiment.codegraph.model.CodeGraphQueryRequest
import skillbill.ports.experiment.codegraph.model.CodeGraphQueryResult
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

private const val DEFAULT_QUERY_TIMEOUT_SECONDS: Long = 30L
private const val DEFAULT_OUTPUT_BYTE_CAP: Int = 256_000
private const val MAX_QUERY_LIMIT: Int = 256
private const val MAX_UNRESOLVED_NOTES: Int = 32
private const val PROCESS_POLL_MILLIS: Long = 50L
private const val OUTPUT_SETTLE_MILLIS: Long = 100L
private const val NANOS_PER_MILLISECOND: Long = 1_000_000L

class ProcessCodeGraphRetrievalAdapter(
  private val binaryPath: Path,
  private val usageLedger: CodeGraphUsageLedgerPort,
  private val queryTimeout: Duration = Duration.ofSeconds(DEFAULT_QUERY_TIMEOUT_SECONDS),
  private val outputByteCap: Int = DEFAULT_OUTPUT_BYTE_CAP,
  private val mapper: ObjectMapper = ObjectMapper(),
) : CodeGraphRetrievalPort {
  override fun query(request: CodeGraphQueryRequest): CodeGraphQueryResult {
    var lastStaleEvidence: StaleCodeGraphEvidenceException? = null
    repeat(2) { attempt ->
      try {
        return queryOnce(request)
      } catch (error: StaleCodeGraphEvidenceException) {
        lastStaleEvidence = error
        usageLedger.markDegraded(
          request.pairId ?: "anonymous",
          if (attempt == 0) "source changed while CodeGraph query was running; retrying" else error.message.orEmpty(),
        )
      }
    }
    throw CodeGraphRetrievalRefusalError(
      requireNotNull(lastStaleEvidence).message.orEmpty(),
    )
  }

  private fun queryOnce(request: CodeGraphQueryRequest): CodeGraphQueryResult {
    requireWorktree(request)
    val pairId = request.pairId ?: "anonymous"
    val sourceIdentity = ensureIndex(request, pairId)
    val dependency = CodeGraphDependencyLoader.load(request.worktreeRoot)
    val cli = dependency[CodeGraphDependencyPayloadKeys.CLI] as Map<*, *>
    val argvPrefix = cli[CodeGraphDependencyPayloadKeys.QUERY_JSON_ARGV_PREFIX] as? List<*>
      ?: throw CodeGraphRetrievalRefusalError("dependency declaration is missing query argv.")
    val startedAt = System.nanoTime()
    val output = runQuery(queryCommand(request, argvPrefix), request, pairId)
    val payload = parseQueryOutput(output, pairId)
    CodeGraphQueryOutputSchemaValidator.validate(payload, "codegraph-query")
    ensureStableSource(request, sourceIdentity)
    val hits = queryHits(payload, request)
    usageLedger.recordQuery(request.pairId ?: pairId, output.size.toLong())
    val receipt = queryReceipt(request, hits, payload.size, startedAt)
    CodeGraphQueryReceiptSchemaValidator.validate(receipt, "codegraph-query-receipt")
    return CodeGraphQueryResult(hits = hits, receipt = receipt)
  }

  private fun requireWorktree(request: CodeGraphQueryRequest) {
    if (!Files.isDirectory(request.worktreeRoot)) {
      throw CodeGraphRetrievalRefusalError("worktree root is missing for graph query.")
    }
  }

  private fun ensureIndex(request: CodeGraphQueryRequest, pairId: String): String =
    ProcessCodeGraphWorktreeIndex.ensureCurrent(
      CodeGraphIndexRequest(
        binaryPath = binaryPath,
        worktreeRoot = request.worktreeRoot,
        graphIndexDirectory = request.graphIndexDirectory,
        usageLedger = usageLedger,
        pairId = pairId,
      ),
    )

  private fun queryCommand(request: CodeGraphQueryRequest, argvPrefix: List<*>): List<String> = buildList {
    if (binaryPath.fileName.toString().endsWith(".cmd", ignoreCase = true)) {
      add("cmd")
      add("/c")
    }
    add(binaryPath.toString())
    argvPrefix.filterIsInstance<String>().forEach(::add)
    add(request.queryText)
    add("--path")
    add(request.worktreeRoot.toString())
    add("--limit")
    add(request.limit.coerceIn(1, MAX_QUERY_LIMIT).toString())
  }

  private fun runQuery(command: List<String>, request: CodeGraphQueryRequest, pairId: String): ByteArray {
    val builder = ProcessBuilder(command)
      .directory(request.worktreeRoot.toFile())
      .redirectErrorStream(true)
    builder.environment()["CODEGRAPH_TELEMETRY"] = "0"
    builder.environment()["DO_NOT_TRACK"] = "1"
    val process = builder.start()
    CodeGraphProcessRegistry.register(pairId, process)
    return try {
      val output = readBoundedOutput(process, pairId)
      if (process.exitValue() != 0) {
        val exitCode = process.exitValue()
        usageLedger.markDegraded(pairId, "query process exited with $exitCode")
        throw CodeGraphRetrievalRefusalError("CodeGraph query failed with exit $exitCode.")
      }
      output
    } finally {
      CodeGraphProcessRegistry.unregister(pairId, process)
    }
  }

  private fun parseQueryOutput(output: ByteArray, pairId: String): List<*> = try {
    mapper.readValue(output, List::class.java)
  } catch (error: JsonProcessingException) {
    usageLedger.markDegraded(pairId, "query output could not be parsed")
    throw CodeGraphRetrievalRefusalError(
      "CodeGraph query output could not be parsed: ${error.message.orEmpty()}",
      error,
    )
  }

  private fun ensureStableSource(request: CodeGraphQueryRequest, sourceIdentity: String) {
    val currentIdentity = ProcessCodeGraphWorktreeIndex.sourceIdentity(
      request.worktreeRoot,
      request.graphIndexDirectory,
    )
    if (currentIdentity != sourceIdentity) {
      throw StaleCodeGraphEvidenceException(
        "CodeGraph query evidence is stale because source changed during the read.",
      )
    }
  }

  private fun queryHits(payload: List<*>, request: CodeGraphQueryRequest): List<CodeGraphCandidateHit> {
    val queryLimit = request.limit.coerceIn(1, MAX_QUERY_LIMIT)
    return payload.filterIsInstance<Map<*, *>>()
      .mapNotNull { result -> queryHit(result, request.worktreeRoot) }
      .take(queryLimit)
  }

  private fun queryHit(result: Map<*, *>, worktreeRoot: Path): CodeGraphCandidateHit? {
    val node = result[CodeGraphQueryOutputPayloadKeys.NODE] as? Map<*, *> ?: return null
    val file = node[CodeGraphQueryOutputPayloadKeys.FILE_PATH]?.toString()?.trim().orEmpty()
    val relativePath = relativeSourcePath(worktreeRoot, file) ?: return null
    return CodeGraphCandidateHit(
      name = node[CodeGraphQueryOutputPayloadKeys.NAME]?.toString().orEmpty(),
      file = relativePath,
      kind = node[CodeGraphQueryOutputPayloadKeys.KIND]?.toString().orEmpty(),
      score = result[CodeGraphQueryOutputPayloadKeys.SCORE]?.toString()?.toDoubleOrNull(),
      line = node[CodeGraphQueryOutputPayloadKeys.START_LINE]?.toString()?.toIntOrNull(),
    )
  }

  private fun queryReceipt(
    request: CodeGraphQueryRequest,
    hits: List<CodeGraphCandidateHit>,
    payloadSize: Int,
    startedAt: Long,
  ): Map<String, Any?> {
    val queryLimit = request.limit.coerceIn(1, MAX_QUERY_LIMIT)
    return mapOf(
      CodeGraphQueryReceiptPayloadKeys.CONTRACT_VERSION to CODEGRAPH_QUERY_RECEIPT_CONTRACT_VERSION,
      CodeGraphQueryReceiptPayloadKeys.QUERY_TEXT to request.queryText,
      CodeGraphQueryReceiptPayloadKeys.HIT_COUNT to hits.size,
      CodeGraphQueryReceiptPayloadKeys.TRUNCATED to (payloadSize >= queryLimit),
      CodeGraphQueryReceiptPayloadKeys.DURATION_MS to
        ((System.nanoTime() - startedAt) / NANOS_PER_MILLISECOND).coerceAtLeast(0L),
      CodeGraphQueryReceiptPayloadKeys.OUTCOME to "ok",
      CodeGraphQueryReceiptPayloadKeys.UNRESOLVED_CONSTRUCT_NOTES to
        request.unresolvedConstructNotes.take(MAX_UNRESOLVED_NOTES),
    )
  }

  private fun relativeSourcePath(worktreeRoot: Path, rawPath: String): String? = runCatching {
    val root = worktreeRoot.toAbsolutePath().normalize()
    val path = Path.of(rawPath)
    val candidate = (if (path.isAbsolute) path else root.resolve(path)).normalize()
    if (!candidate.startsWith(root) || !Files.isRegularFile(candidate)) {
      null
    } else {
      root.relativize(candidate).toString().replace('\\', '/')
    }
  }.getOrNull()

  private fun readBoundedOutput(process: Process, pairId: String): ByteArray {
    val executor = Executors.newSingleThreadExecutor()
    val outputFuture = executor.submit<ByteArray> {
      process.inputStream.readNBytes(outputByteCap + 1)
    }
    val deadline = System.nanoTime() + queryTimeout.toNanos()
    try {
      while (process.isAlive) {
        if (outputFuture.isDone) {
          ensureBoundedOutput(outputFuture.get(), process, pairId)
        }
        val remaining = deadline - System.nanoTime()
        if (remaining <= 0L) {
          timeout(process, pairId)
        }
        process.waitFor(
          remaining.coerceAtMost(TimeUnit.MILLISECONDS.toNanos(PROCESS_POLL_MILLIS)),
          TimeUnit.NANOSECONDS,
        )
      }
      return ensureBoundedOutput(
        outputFuture.get(OUTPUT_SETTLE_MILLIS, TimeUnit.MILLISECONDS),
        process,
        pairId,
      )
    } finally {
      executor.shutdownNow()
    }
  }

  private fun ensureBoundedOutput(output: ByteArray, process: Process, pairId: String): ByteArray {
    if (output.size > outputByteCap) {
      process.destroyForcibly()
      usageLedger.markDegraded(pairId, "query output exceeded cap")
      throw CodeGraphRetrievalRefusalError("CodeGraph query output exceeded the configured cap.")
    }
    return output
  }

  private fun timeout(process: Process, pairId: String): Nothing {
    process.destroyForcibly()
    usageLedger.markDegraded(pairId, "query timed out")
    throw CodeGraphRetrievalRefusalError("CodeGraph query timed out.")
  }

  private class StaleCodeGraphEvidenceException(message: String) : RuntimeException(message)
}

class DeferredCodeGraphRetrievalPort(
  private val binaryPath: () -> Path?,
  private val usageLedger: CodeGraphUsageLedgerPort,
) : CodeGraphRetrievalPort {
  override fun query(request: CodeGraphQueryRequest): CodeGraphQueryResult {
    val resolvedBinary = binaryPath()
      ?: throw CodeGraphRetrievalRefusalError("managed CodeGraph executable is not installed for this pair.")
    return ProcessCodeGraphRetrievalAdapter(
      binaryPath = resolvedBinary,
      usageLedger = usageLedger,
    ).query(request)
  }
}
