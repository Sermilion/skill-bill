package skillbill.infrastructure.fs

import me.tatarka.inject.annotations.Inject
import skillbill.infrastructure.fs.scaffold.manifest.appendCodeReviewArea
import skillbill.infrastructure.fs.scaffold.manifest.appendGovernedAddonManifestRegistration
import skillbill.infrastructure.fs.scaffold.manifest.renderGovernedAddonManifestRegistration
import skillbill.infrastructure.fs.scaffold.manifest.setDeclaredQualityCheckFile
import skillbill.ports.scaffold.manifest.ScaffoldManifestPersistencePort
import skillbill.ports.scaffold.manifest.model.ScaffoldManifestAppendCodeReviewAreaRequest
import skillbill.ports.scaffold.manifest.model.ScaffoldManifestReadResult
import skillbill.ports.scaffold.manifest.model.ScaffoldManifestRegisterGovernedAddonRequest
import skillbill.ports.scaffold.manifest.model.ScaffoldManifestRenderPlatformPackRequest
import skillbill.ports.scaffold.manifest.model.ScaffoldManifestSetDeclaredQualityCheckRequest
import skillbill.ports.scaffold.manifest.model.ScaffoldManifestSnapshot
import skillbill.ports.scaffold.manifest.model.ScaffoldManifestWriteRequest
import skillbill.scaffold.policy.platformpack.model.PlatformPackManifestRenderRequest
import java.nio.file.Files
import java.nio.file.Path
import skillbill.scaffold.policy.platformpack.renderPlatformPackManifest as policyRenderPlatformPackManifest

@Inject
class FileSystemScaffoldManifestPersistence : ScaffoldManifestPersistencePort {
  override fun read(manifestPath: Path): ScaffoldManifestReadResult = ScaffoldManifestReadResult(
    manifestPath = manifestPath,
    content = Files.readString(manifestPath),
  )

  override fun snapshot(manifestPath: Path): ScaffoldManifestSnapshot = ScaffoldManifestSnapshot(
    manifestPath = manifestPath,
    originalBytes = Files.readAllBytes(manifestPath),
  )

  override fun restore(snapshot: ScaffoldManifestSnapshot) {
    Files.write(snapshot.manifestPath, snapshot.originalBytes)
  }

  override fun write(request: ScaffoldManifestWriteRequest) {
    Files.writeString(request.manifestPath, request.content)
  }

  override fun renderPlatformPackManifest(request: ScaffoldManifestRenderPlatformPackRequest): String =
    policyRenderPlatformPackManifest(
      PlatformPackManifestRenderRequest(
        platform = request.platform,
        displayName = request.displayName,
        strongSignals = request.strongSignals,
        tieBreakers = request.tieBreakers,
        declaredCodeReviewAreas = request.declaredCodeReviewAreas,
        baselineContentPath = request.baselineContentPath,
        declaredAreaFiles = request.declaredAreaFiles,
        declaredQualityCheckFile = request.declaredQualityCheckFile,
        areaMetadata = request.areaMetadata,
        baselineLayers = request.baselineLayers,
      ),
    )

  override fun appendCodeReviewArea(request: ScaffoldManifestAppendCodeReviewAreaRequest) {
    appendCodeReviewArea(
      manifestPath = request.manifestPath,
      area = request.area,
      relativeContentPath = request.relativeContentPath,
      areaFocus = request.areaFocus,
    )
  }

  override fun setDeclaredQualityCheckFile(request: ScaffoldManifestSetDeclaredQualityCheckRequest) {
    setDeclaredQualityCheckFile(
      manifestPath = request.manifestPath,
      relativeContentPath = request.relativeContentPath,
    )
  }

  override fun registerGovernedAddon(request: ScaffoldManifestRegisterGovernedAddonRequest) {
    appendGovernedAddonManifestRegistration(
      manifestPath = request.manifestPath,
      platform = request.platform,
      skillRelativeDirs = request.skillRelativeDirs,
      addonSlug = request.addonSlug,
    )
  }

  override fun renderGovernedAddonRegistrationPreview(
    currentText: String,
    request: ScaffoldManifestRegisterGovernedAddonRequest,
  ): String = renderGovernedAddonManifestRegistration(
    text = currentText,
    platform = request.platform,
    skillRelativeDirs = request.skillRelativeDirs,
    addonSlug = request.addonSlug,
  )
}
