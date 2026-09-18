package skillbill.infrastructure.fs.install.nativeagent

import skillbill.install.model.SupportedAgent
import java.nio.file.Files
import java.nio.file.Path

internal fun parseEmbeddedLogicalName(path: Path, agent: SupportedAgent): String? {
  val text = Files.readString(path)
  val pattern = if (agent == SupportedAgent.CODEX) {
    Regex("(?m)^name\\s*=\\s*\\\"([^\\\"]+)\\\"")
  } else {
    Regex("(?m)^name:\\s*['\\\"]?([^'\\\"\\r\\n]+)")
  }
  return pattern.find(text)?.groupValues?.get(1)?.trim()
}
