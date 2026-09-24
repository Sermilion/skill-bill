package skillbill.skillremove.model

data class AppliedCascade(
  val removedPaths: List<String>,
  val editedManifests: List<String>,
  val unlinkedSymlinks: List<String>,
  val readmeWarnings: List<ReadmeCatalogWarning> = emptyList(),
)

data class ReadmeCatalogWarning(
  val readmePath: String,
  val kind: ReadmeCatalogEditKind,
  val reason: String,
)
