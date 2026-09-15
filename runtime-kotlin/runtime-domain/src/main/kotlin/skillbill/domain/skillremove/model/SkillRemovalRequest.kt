package skillbill.domain.skillremove.model

data class SkillRemovalRequest(
  val target: SkillRemovalTarget,
  val repoRootAbsolutePath: String,
  val userHomeAbsolutePath: String? = null,
  val environment: Map<String, String> = emptyMap(),
)
