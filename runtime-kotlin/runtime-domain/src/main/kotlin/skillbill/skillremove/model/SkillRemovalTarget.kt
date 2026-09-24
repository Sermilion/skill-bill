package skillbill.skillremove.model

sealed class SkillRemovalTarget {
  data class HorizontalSkill(
    val skillName: String,
    val allowShipped: Boolean = false,
  ) : SkillRemovalTarget()

  data class PlatformPack(
    val platform: String,
    val allowShipped: Boolean = false,
  ) : SkillRemovalTarget()

  data class AddOn(
    val relativePath: String,
  ) : SkillRemovalTarget()

  data class ExternalAddOn(
    val sourceRootAbsolutePath: String,
    val platform: String,
    val fileName: String,
  ) : SkillRemovalTarget()

  companion object {
    val BUILT_IN_NAMES: Set<String> = setOf(".bill-shared")

    const val HORIZONTAL_PRODUCT_PREFIX: String = "bill-"

    fun isProtectedHorizontalName(name: String): Boolean =
      name in BUILT_IN_NAMES || name.startsWith(HORIZONTAL_PRODUCT_PREFIX)

    fun isProtectedPlatformName(name: String): Boolean = name == ".bill-shared"

    fun isBuiltInName(name: String): Boolean = name in BUILT_IN_NAMES || name.startsWith(HORIZONTAL_PRODUCT_PREFIX)
  }
}
