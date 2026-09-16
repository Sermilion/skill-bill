package skillbill.ports.process

import java.nio.file.Path

interface InstallerScriptFetchPort {
  fun fetch(request: InstallerScriptFetchRequest): InstallerScriptFetchResult

  fun cleanup(scriptPath: Path)
}

data class InstallerScriptFetchRequest(
  val url: String,
)

sealed interface InstallerScriptFetchResult {
  data class Ready(val scriptPath: Path) : InstallerScriptFetchResult

  data class Failed(val message: String) : InstallerScriptFetchResult
}
