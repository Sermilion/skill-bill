package skillbill.infrastructure.launcher.codegraph

import skillbill.codegraph.model.CodeGraphLifecycleObservation
import skillbill.ports.codegraph.CodeGraphExecutablePort
import skillbill.ports.codegraph.CodeGraphLifecycleStore
import skillbill.ports.codegraph.CodeGraphMcpProcess
import skillbill.ports.codegraph.CodeGraphMcpProcessPort
import skillbill.ports.codegraph.model.CodeGraphCommandResult
import skillbill.ports.codegraph.model.CodeGraphSessionRequest
import java.nio.file.Files
import java.nio.file.Path
import java.util.Collections
import skillbill.contracts.codegraph.CodeGraphMcpKeys as K

internal class CodeGraphBoundaryFixture(val root: Path) {
  val observations: MutableList<CodeGraphLifecycleObservation> = Collections.synchronizedList(mutableListOf())
  val commands = mutableListOf<List<String>>()
  val scopes = mutableListOf<Path>()
  val environments = mutableListOf<Map<String, String>>()
  var available = true
  var availabilityChecks = 0
  var initializeCreatesGraph = true
  var status = CodeGraphCommandResult(0, "Nodes: 2\nEdges: 1\nFiles: 1\nJournal: wal", "")
  var initialization = CodeGraphCommandResult(0, "", "")
  var startFailure: Exception? = null
  var starts = 0
  val process = BoundaryMcpProcess()
  val store = object : CodeGraphLifecycleStore {
    private val files = FileSystemCodeGraphLifecycleStore(root)
    override fun persist(observation: CodeGraphLifecycleObservation) {
      observations += observation
      files.persist(observation)
    }
  }
  val session = FileSystemCodeGraphSession(
    executable = object : CodeGraphExecutablePort {
      override fun isAvailable(): Boolean {
        availabilityChecks++
        return available
      }
      override fun execute(
        command: List<String>,
        workingDirectory: Path,
        environment: Map<String, String>,
      ): CodeGraphCommandResult {
        commands += command
        scopes.add(workingDirectory)
        environments.add(environment)
        if (command[1] == "init") {
          if (initialization.exitCode == 0 && initializeCreatesGraph) {
            Files.createDirectories(
              root.resolve(".codegraph"),
            )
          }
          return initialization
        }
        return status
      }
    },
    lifecycleStoreFactory = { store },
    mcp = object : CodeGraphMcpProcessPort {
      override fun start(
        command: List<String>,
        repository: Path,
        environment: Map<String, String>,
      ): CodeGraphMcpProcess {
        starts++
        commands += command
        scopes.add(repository)
        environments.add(environment)
        startFailure?.let { throw it }
        return process
      }
    },
  )
  fun request(agent: String = "codex") = CodeGraphSessionRequest(root, "child-1", agent)
  fun prepared() {
    Files.createDirectories(root.resolve(".codegraph"))
  }
  fun persisted(): String = Files.readString(
    root.resolve(".skill-bill/runtime/codegraph-sessions/${observations.last().sessionId}.json"),
  )
}

internal class BoundaryMcpProcess : CodeGraphMcpProcess {
  @Volatile override var alive = true
  var closes = 0
  var failCleanup = false
  var failHandshake = false
  var tool = "codegraph_explore"
  var queryText = "fresh source"
  var queryError = false
  var queryFailure: Exception? = null
  val requests = mutableListOf<String>()

  override fun exchange(request: String): String {
    check(alive) { "CodeGraph process has exited." }
    requests += request
    val mapper = CodeGraphMcpProtocol.mapper
    val node = mapper.readTree(request)
    val result = mapper.createObjectNode()
    when (node.path(K.METHOD).asText()) {
      "initialize" -> {
        check(!failHandshake) { "source and secrets must not persist" }
        result.put(K.PROTOCOL_VERSION, CodeGraphMcpProtocol.PROTOCOL)
      }
      "tools/list" -> result.putArray(K.TOOLS).addObject().put(K.NAME, tool)
      "tools/call" -> {
        queryFailure?.let { throw it }
        result.put(K.IS_ERROR, queryError)
        result.putArray(K.CONTENT).addObject().put(K.TYPE, "text").put(K.TEXT, queryText)
      }
    }
    return CodeGraphMcpProtocol.result(node.path(K.ID), result).toString()
  }
  override fun notify(notification: String) {
    requests += notification
  }
  override fun close() {
    closes++
    alive = false
    check(!failCleanup) { "private source, token, and credentials" }
  }
}
