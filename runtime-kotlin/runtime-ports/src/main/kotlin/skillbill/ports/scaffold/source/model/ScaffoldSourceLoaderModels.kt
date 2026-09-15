package skillbill.ports.scaffold.source.model

import skillbill.scaffold.model.PlatformManifest
import java.nio.file.Path

data class ScaffoldPlatformPackLoadRequest(
  val packRoot: Path,
)

data class ScaffoldPlatformPackLoadResult(
  val packRoot: Path,
  val manifest: PlatformManifest,
)
