package skillbill.scaffold.policy.platformpack

import skillbill.error.core.AmbiguousExternalPlatformPackError
import skillbill.scaffold.policy.platformpack.model.EffectivePlatformPackCatalog
import skillbill.scaffold.policy.platformpack.model.EffectivePlatformPackEntry
import skillbill.scaffold.policy.platformpack.model.LoadedPlatformPack

fun buildEffectivePlatformPackCatalog(
  bundled: List<LoadedPlatformPack>,
  external: List<LoadedPlatformPack>,
): EffectivePlatformPackCatalog {
  val bundledBySlug = bundled.associateBy { pack -> pack.manifest.slug }
  val externalBySlug = linkedMapOf<String, LoadedPlatformPack>()
  external.forEach { pack ->
    val slug = pack.manifest.slug
    val existing = externalBySlug[slug]
    if (existing != null && existing.canonicalRoot != pack.canonicalRoot) {
      throw AmbiguousExternalPlatformPackError(
        "External platform pack slug '$slug' is declared by multiple roots: " +
          "'${existing.canonicalRoot}' and '${pack.canonicalRoot}'.",
      )
    }
    externalBySlug[slug] = pack
  }
  val slugs = (bundledBySlug.keys + externalBySlug.keys).toSortedSet()
  val entries = slugs.map { slug ->
    val externalPack = externalBySlug[slug]
    if (externalPack != null) {
      EffectivePlatformPackEntry(
        loaded = externalPack,
        shadowedBundledSlug = slug.takeIf { bundledBySlug.containsKey(slug) },
      )
    } else {
      val bundledPack = bundledBySlug.getValue(slug)
      EffectivePlatformPackEntry(loaded = bundledPack, shadowedBundledSlug = null)
    }
  }
  return EffectivePlatformPackCatalog(entries)
}
