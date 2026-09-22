package skillbill.scaffold.policy.platformpack.model

import skillbill.scaffold.model.PlatformManifest

enum class PlatformPackSourceKind(val wireValue: String) {
  BUNDLED("bundled"),
  EXTERNAL("external"),
}

data class LoadedPlatformPack(
  val manifest: PlatformManifest,
  val sourceKind: PlatformPackSourceKind,
  val canonicalRoot: String,
)

data class EffectivePlatformPackEntry(
  val loaded: LoadedPlatformPack,
  val shadowedBundledSlug: String?,
)

data class EffectivePlatformPackCatalog(
  val entries: List<EffectivePlatformPackEntry>,
) {
  val manifests: List<PlatformManifest> = entries.map { entry -> entry.loaded.manifest }

  val manifestsBySlug: Map<String, PlatformManifest> = entries.associate { entry ->
    entry.loaded.manifest.slug to entry.loaded.manifest
  }

  fun entryForSlug(slug: String): EffectivePlatformPackEntry? =
    entries.firstOrNull { entry -> entry.loaded.manifest.slug == slug }
}
