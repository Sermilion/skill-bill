package skillbill.ports.process

import skillbill.ports.process.model.InstallerScriptFetchRequest
import skillbill.ports.process.model.InstallerScriptFetchResult
import java.nio.file.Path

interface InstallerScriptFetchPort {
  fun fetch(request: InstallerScriptFetchRequest): InstallerScriptFetchResult

  fun cleanup(scriptPath: Path)
}
