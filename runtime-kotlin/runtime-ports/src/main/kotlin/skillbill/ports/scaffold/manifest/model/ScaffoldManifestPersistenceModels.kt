package skillbill.ports.scaffold.manifest.model

import skillbill.scaffold.model.CodeReviewBaselineLayer
import java.nio.file.Path

data class ScaffoldManifestWriteRequest(
  val manifestPath: Path,
  val content: String,
)

data class ScaffoldManifestAppendCodeReviewAreaRequest(
  val manifestPath: Path,
  val area: String,
  val relativeContentPath: String,
  val areaFocus: String,
)

data class ScaffoldManifestSetDeclaredQualityCheckRequest(
  val manifestPath: Path,
  val relativeContentPath: String,
)

data class ScaffoldManifestRegisterGovernedAddonRequest(
  val manifestPath: Path,
  val platform: String,
  val skillRelativeDirs: List<String>,
  val addonSlug: String,
)

data class ScaffoldManifestReadResult(
  val manifestPath: Path,
  val content: String,
)

data class ScaffoldManifestRenderPlatformPackRequest(
  val platform: String,
  val displayName: String,
  val strongSignals: List<String>,
  val tieBreakers: List<String>,
  val declaredCodeReviewAreas: List<String>,
  val baselineContentPath: String,
  val declaredAreaFiles: Map<String, String>,
  val declaredQualityCheckFile: String?,
  val areaMetadata: Map<String, String>,
  val baselineLayers: List<CodeReviewBaselineLayer>,
)

data class ScaffoldManifestSnapshot(
  val manifestPath: Path,
  val originalBytes: ByteArray,
) {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is ScaffoldManifestSnapshot) return false
    return manifestPath == other.manifestPath && originalBytes.contentEquals(other.originalBytes)
  }

  override fun hashCode(): Int = 31 * manifestPath.hashCode() + originalBytes.contentHashCode()
}
