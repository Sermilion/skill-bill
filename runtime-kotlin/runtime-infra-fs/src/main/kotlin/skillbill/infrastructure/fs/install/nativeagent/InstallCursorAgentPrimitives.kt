package skillbill.infrastructure.fs.install.nativeagent

import skillbill.infrastructure.fs.nativeagent.discovery.discoverNativeAgentFilesByDir
import skillbill.infrastructure.fs.nativeagent.rendering.NativeAgentProvider
import skillbill.infrastructure.fs.resolveUserHome
import java.nio.file.Path

internal fun discoverCursorAgentMarkdown(
  platformPacksRoot: Path,
  skillsRoot: Path? = null,
  selectedPlatforms: List<String>? = null,
): List<Path> = discoverNativeAgentFilesByDir(
  platformPacksRoot = platformPacksRoot,
  skillsRoot = skillsRoot,
  selectedPlatforms = selectedPlatforms,
  directoryName = NativeAgentProvider.Cursor.directoryName,
  extension = NativeAgentProvider.Cursor.extension,
)

internal fun uninstallCursorAgentMarkdown(
  platformPacksRoot: Path,
  home: Path? = null,
  skillsRoot: Path? = null,
  selectedPlatforms: List<String>? = null,
): List<Path> {
  val resolvedHome = home ?: resolveUserHome(null)
  return uninstallNativeAgentFiles(
    discoverCursorAgentMarkdown(platformPacksRoot, skillsRoot, selectedPlatforms),
    NativeAgentProvider.Cursor.homeAgentDirs(resolvedHome),
  )
}
