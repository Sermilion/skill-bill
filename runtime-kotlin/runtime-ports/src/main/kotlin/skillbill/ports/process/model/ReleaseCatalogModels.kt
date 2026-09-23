package skillbill.ports.process.model

sealed interface ReleaseCatalogResult {
  data class Releases(val entries: List<ReleaseCatalogEntry>) : ReleaseCatalogResult

  data class Failure(val reason: String) : ReleaseCatalogResult
}

sealed interface ReleaseCatalogEntry {
  val prerelease: Boolean
  val draft: Boolean

  data class Release(
    val tagName: String,
    val url: String,
    val notes: String?,
    override val prerelease: Boolean,
    override val draft: Boolean,
  ) : ReleaseCatalogEntry

  data class Malformed(
    override val prerelease: Boolean,
    override val draft: Boolean,
  ) : ReleaseCatalogEntry
}
