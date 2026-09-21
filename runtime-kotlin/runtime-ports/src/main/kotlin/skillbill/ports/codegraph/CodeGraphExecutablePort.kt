package skillbill.ports.codegraph

import skillbill.codegraph.model.CodeGraphLifecycleObservation
import skillbill.ports.codegraph.model.CodeGraphCommandResult
import java.nio.file.Path

interface CodeGraphExecutablePort {
  fun isAvailable(): Boolean

  fun execute(
    command: List<String>,
    workingDirectory: Path,
    environment: Map<String, String> = emptyMap(),
  ): CodeGraphCommandResult
}

interface CodeGraphLifecycleStore {
  fun persist(observation: CodeGraphLifecycleObservation)
}
