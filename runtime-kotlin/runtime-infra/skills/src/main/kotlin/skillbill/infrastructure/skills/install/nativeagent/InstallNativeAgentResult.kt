package skillbill.infrastructure.skills.install.nativeagent

import java.nio.file.Path

sealed class InstallNativeAgentResult {
  data class Linked(val link: Path) : InstallNativeAgentResult()

  data class Skipped(val link: Path, val reason: String) : InstallNativeAgentResult()
}
