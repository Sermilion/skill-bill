package dev.skillbill.runtime.buildlogic

private val STABLE_SEMVER_TAG = Regex("""^v?(\d+)\.(\d+)\.(\d+)$""")

fun resolveSkillBillVersion(
  releaseVersion: String?,
  tags: Iterable<String>,
): String = releaseVersion?.takeIf(String::isNotBlank) ?: nextDevSnapshotVersion(tags) ?: UNVERSIONED_SNAPSHOT

fun nextDevSnapshotVersion(tags: Iterable<String>): String? {
  val latest =
    tags.mapNotNull(::parseStableSemver)
      .maxWithOrNull(compareBy(StableSemver::major, StableSemver::minor, StableSemver::patch))
      ?: return null
  return "${latest.major}.${latest.minor}.${latest.patch + 1}-SNAPSHOT"
}

private data class StableSemver(val major: Int, val minor: Int, val patch: Int)

private fun parseStableSemver(tag: String): StableSemver? {
  val match = STABLE_SEMVER_TAG.matchEntire(tag.trim()) ?: return null
  val (major, minor, patch) = match.destructured
  return StableSemver(major.toInt(), minor.toInt(), patch.toInt())
}

internal const val UNVERSIONED_SNAPSHOT = "0.0.0-SNAPSHOT"
