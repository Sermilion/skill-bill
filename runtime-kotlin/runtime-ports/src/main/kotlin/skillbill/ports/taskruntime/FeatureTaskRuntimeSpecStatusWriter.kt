package skillbill.ports.taskruntime

import java.nio.file.Path

fun interface FeatureTaskRuntimeSpecStatusWriter {
  fun writeFinalizingAgent(
    specPath: Path,
    finalizingAgentId: String,
  )
}
