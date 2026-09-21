package skillbill.ports.codegraph

import java.nio.file.Path

interface CodeGraphMcpProcessPort {
  fun start(command: List<String>, repository: Path, environment: Map<String, String>): CodeGraphMcpProcess
}

interface CodeGraphMcpProcess : AutoCloseable {
  val alive: Boolean
  fun exchange(request: String): String
  fun notify(notification: String)
}
