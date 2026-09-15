package skillbill.domain.skillremove.model

data class SkillRemovalPreview(

  val filesystemPaths: List<String>,

  val manifestEdits: List<ManifestEdit>,

  val agentSymlinkUnlinks: List<AgentSymlinkUnlink>,

  val readmeCatalogEdits: List<ReadmeCatalogEdit>,

  val skillDirRoot: String,

  val cascadedSkillNames: List<String> = emptyList(),
)

data class ManifestEdit(
  val manifestPath: String,
  val editKind: ManifestEditKind,
  val detail: String,
)

enum class ManifestEditKind {
  REMOVE_CODE_REVIEW_AREA,
  REMOVE_DECLARED_QUALITY_CHECK_FILE,
  REMOVE_DECLARED_FILES_AREA_ENTRY,
  REMOVE_AREA_METADATA_ENTRY,

  REMOVE_DECLARED_FILES_BASELINE,

  REMOVE_POINTERS_BLOCK_KEY,

  REMOVE_ADDON_REFERENCES,

  REMOVE_SKILL_CLASS_POINTER,
}

data class AgentSymlinkUnlink(
  val provider: AgentSymlinkProvider,
  val path: String,
)

enum class AgentSymlinkProvider {
  CLAUDE,
  CODEX,
  JUNIE,
  CURSOR,
}

data class ReadmeCatalogEdit(
  val readmePath: String,
  val kind: ReadmeCatalogEditKind,
  val detail: String,
)

enum class ReadmeCatalogEditKind {
  REMOVE_CATALOG_ROW,
  DECREMENT_SECTION_COUNT,
}
