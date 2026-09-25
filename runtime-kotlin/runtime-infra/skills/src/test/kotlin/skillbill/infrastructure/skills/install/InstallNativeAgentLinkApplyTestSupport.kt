package skillbill.infrastructure.skills.install

import java.nio.file.Path

open class InstallNativeAgentLinkApplyTestSupport : InstallApplyTestSupport() {
  protected fun inventoryJson(
    logicalName: String,
    installedPath: Path,
    cacheTargetPath: Path,
    sourceRoot: Path,
  ): String {
    val contentDigest = "0".repeat(64)
    return """
      {"contract_version":"0.2","entries":[
        {"logical_name":"$logicalName","provider":"codex","installed_path":"$installedPath",
          "cache_target_path":"$cacheTargetPath","content_digest":"$contentDigest","source_root":"$sourceRoot"}
      ]}
      """.trimIndent()
  }
}
