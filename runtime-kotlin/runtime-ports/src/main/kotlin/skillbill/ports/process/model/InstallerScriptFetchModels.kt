package skillbill.ports.process.model

import java.nio.file.Path

data class InstallerScriptFetchRequest(
  val url: String,
)

sealed interface InstallerScriptFetchResult {
  data class Ready(val scriptPath: Path) : InstallerScriptFetchResult

  data class Failed(val message: String) : InstallerScriptFetchResult
}
