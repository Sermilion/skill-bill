package skillbill.infrastructure.skills.scaffold

import skillbill.infrastructure.skills.externaladdon.FileSystemExternalAddonOverlay
import skillbill.infrastructure.skills.externalplatformpack.FileExternalPlatformPackSourceConfigStore
import skillbill.infrastructure.skills.scaffold.platformpack.catalog.PlatformPackCatalogLoader

internal fun overlayPort(): FileSystemExternalAddonOverlay {
  val store = FileExternalPlatformPackSourceConfigStore()
  return FileSystemExternalAddonOverlay(store, PlatformPackCatalogLoader(store))
}
