package skillbill.infrastructure.fs.jvm

import java.io.IOException
import java.nio.file.Path
import java.util.concurrent.TimeUnit

private const val GIT_TRACKED_FILES_TIMEOUT_SECONDS = 30L

internal fun readGitTrackedFiles(repoRoot: Path): Set<String>? {
  val process = try {
    ProcessBuilder("git", "-C", repoRoot.toString(), "ls-files")
      .redirectErrorStream(true)
      .start()
  } catch (_: IOException) {
    return null
  }
  val output = process.inputStream.bufferedReader().use { reader -> reader.readText() }
  if (!process.waitFor(GIT_TRACKED_FILES_TIMEOUT_SECONDS, TimeUnit.SECONDS) || process.exitValue() != 0) {
    process.destroyForcibly()
    return null
  }
  return output.lineSequence()
    .map(String::trim)
    .filter(String::isNotEmpty)
    .toSet()
}
