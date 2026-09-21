package skillbill.infrastructure.host.experiment.codegraph

import skillbill.contracts.experiment.codegraph.CodeGraphDependencyPayloadKeys
import skillbill.error.shellcontent.CodeGraphRetrievalRefusalError
import skillbill.ports.experiment.codegraph.CodeGraphUsageLedgerPort
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.time.Duration
import java.util.concurrent.TimeUnit
import kotlin.system.measureTimeMillis

private const val INDEX_TIMEOUT_MINUTES: Long = 2L
private const val INDEX_ATTEMPTS: Int = 2

internal data class CodeGraphIndexRequest(
  val binaryPath: Path,
  val worktreeRoot: Path,
  val graphIndexDirectory: Path?,
  val usageLedger: CodeGraphUsageLedgerPort,
  val pairId: String,
  val indexTimeout: Duration = Duration.ofMinutes(INDEX_TIMEOUT_MINUTES),
)

internal object ProcessCodeGraphWorktreeIndex {
  fun ensureCurrent(request: CodeGraphIndexRequest): String {
    val dependency = CodeGraphDependencyLoader.load(request.worktreeRoot)
    val cli = dependency[CodeGraphDependencyPayloadKeys.CLI] as Map<*, *>
    val initArgv = requiredArgv(cli, CodeGraphDependencyPayloadKeys.INIT_ARGV, "init")
    val indexArgv = requiredArgv(cli, CodeGraphDependencyPayloadKeys.INDEX_ARGV, "index")
    val syncArgv = requiredArgv(cli, CodeGraphDependencyPayloadKeys.SYNC_ARGV, "sync")
    val indexDir = request.graphIndexDirectory ?: request.worktreeRoot.resolve(".skill-bill/graph-index")
    Files.createDirectories(indexDir)
    val marker = indexDir.resolve(".skill-bill-index-ready")
    repeat(INDEX_ATTEMPTS) {
      val before = sourceIdentity(request.worktreeRoot, indexDir)
      val initialized = Files.isRegularFile(request.worktreeRoot.resolve(".codegraph/codegraph.db"))
      if (!initialized || !Files.isRegularFile(marker) || Files.readString(marker) != before) {
        val duration = measureTimeMillis {
          runCli(
            request,
            if (initialized) indexArgv else initArgv,
          )
        }
        request.usageLedger.recordIndex(request.pairId, duration)
      }
      val duration = measureTimeMillis {
        runCli(request, syncArgv)
      }
      request.usageLedger.recordSync(request.pairId, duration)
      val after = sourceIdentity(request.worktreeRoot, indexDir)
      if (before == after) {
        Files.writeString(marker, after)
        return after
      }
    }
    request.usageLedger.markDegraded(
      request.pairId,
      "source changed while CodeGraph index was synchronized",
    )
    throw CodeGraphRetrievalRefusalError("CodeGraph source changed during index synchronization.")
  }

  private fun requiredArgv(cli: Map<*, *>, key: String, name: String): List<*> =
    cli[key] as? List<*> ?: throw CodeGraphRetrievalRefusalError("dependency declaration is missing $name argv.")

  private fun runCli(request: CodeGraphIndexRequest, argvSuffix: List<*>) {
    val command = buildList {
      if (request.binaryPath.fileName.toString().endsWith(".cmd", ignoreCase = true)) {
        add("cmd")
        add("/c")
      }
      add(request.binaryPath.toString())
      argvSuffix.filterIsInstance<String>().forEach(::add)
      add(request.worktreeRoot.toString())
    }
    val builder = ProcessBuilder(command)
      .directory(request.worktreeRoot.toFile())
      .redirectErrorStream(true)
      .redirectOutput(ProcessBuilder.Redirect.DISCARD)
    builder.environment()["CODEGRAPH_TELEMETRY"] = "0"
    builder.environment()["DO_NOT_TRACK"] = "1"
    val process = builder.start()
    CodeGraphProcessRegistry.register(pairId = request.pairId, process = process)
    try {
      if (!process.waitFor(request.indexTimeout.toMillis(), TimeUnit.MILLISECONDS)) {
        process.destroyForcibly()
        request.usageLedger.markDegraded(request.pairId, "CodeGraph index command timed out.")
        throw CodeGraphRetrievalRefusalError("CodeGraph index command timed out.")
      }
      if (process.exitValue() != 0) {
        request.usageLedger.markDegraded(
          request.pairId,
          "CodeGraph index command failed with exit ${process.exitValue()}.",
        )
        throw CodeGraphRetrievalRefusalError(
          "CodeGraph index command failed with exit ${process.exitValue()}.",
        )
      }
    } finally {
      CodeGraphProcessRegistry.unregister(request.pairId, process)
    }
  }

  internal fun sourceIdentity(worktreeRoot: Path, indexDir: Path?): String {
    val resolvedIndexDir = indexDir ?: worktreeRoot.resolve(".skill-bill/graph-index")
    val codeGraphIndexDir = worktreeRoot.resolve(".codegraph")
    val digest = MessageDigest.getInstance("SHA-256")
    Files.walk(worktreeRoot).use { paths ->
      paths
        .filter(Files::isRegularFile)
        .filter { path -> !path.startsWith(resolvedIndexDir) }
        .filter { path -> !path.startsWith(codeGraphIndexDir) }
        .filter { path -> !path.startsWith(worktreeRoot.resolve(".git")) }
        .sorted()
        .forEach { path ->
          digest.update(worktreeRoot.relativize(path).toString().replace('\\', '/').encodeToByteArray())
          digest.update(0)
          digest.update(Files.readAllBytes(path))
          digest.update(0)
        }
    }
    return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
  }
}
