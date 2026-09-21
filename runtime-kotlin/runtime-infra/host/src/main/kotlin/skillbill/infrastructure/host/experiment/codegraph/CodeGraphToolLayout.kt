package skillbill.infrastructure.host.experiment.codegraph

import java.nio.file.Path

object CodeGraphToolLayout {
  const val TOOLS_DIR_NAME: String = "tools"
  const val CODEGRAPH_DIR_NAME: String = "codegraph"
  const val INSTALL_LOCK_FILE: String = ".install.lock"
  const val ASSET_DIGEST_FILE: String = ".asset.sha256"

  fun toolsRoot(userHome: Path): Path = userHome.resolve(".skill-bill").resolve(TOOLS_DIR_NAME).normalize()

  fun versionRoot(userHome: Path, releaseTag: String): Path =
    toolsRoot(userHome).resolve(CODEGRAPH_DIR_NAME).resolve(releaseTag).normalize()

  fun binaryPath(
    userHome: Path,
    releaseTag: String,
    binaryName: String,
    platformId: String? = CodeGraphHostPlatform.currentPlatformId(),
  ): Path {
    val executableName = if (platformId?.startsWith("win32-") == true) "$binaryName.cmd" else binaryName
    return versionRoot(userHome, releaseTag).resolve("bin").resolve(executableName).normalize()
  }

  fun assetDigestPath(userHome: Path, releaseTag: String): Path =
    versionRoot(userHome, releaseTag).resolve(ASSET_DIGEST_FILE).normalize()
}
