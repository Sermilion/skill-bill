package skillbill.infrastructure.fs.install.plan

import skillbill.install.model.InstallPlatformPackDiscoverySnapshot
import skillbill.scaffold.model.PlatformManifest

internal fun List<PlatformManifest>.toDiscoverySnapshots(): List<InstallPlatformPackDiscoverySnapshot> =
  map { manifest ->
    InstallPlatformPackDiscoverySnapshot(
      slug = manifest.slug,
      packRoot = manifest.packRoot,
      baselineLayers = manifest.codeReviewComposition?.baselineLayers.orEmpty(),
    )
  }
