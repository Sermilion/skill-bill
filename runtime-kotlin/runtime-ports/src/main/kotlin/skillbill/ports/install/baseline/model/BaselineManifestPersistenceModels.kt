package skillbill.ports.install.baseline.model

import skillbill.install.model.BaselineManifest
import java.nio.file.Path

data class ReadBaselineManifestRequest(
  val installHome: Path,
)

data class ReadBaselineManifestResult(
  val manifest: BaselineManifest,
  val existed: Boolean,
)

data class WriteBaselineManifestRequest(
  val installHome: Path,
  val manifest: BaselineManifest,
)

data class WriteBaselineManifestResult(
  val path: Path,
)
