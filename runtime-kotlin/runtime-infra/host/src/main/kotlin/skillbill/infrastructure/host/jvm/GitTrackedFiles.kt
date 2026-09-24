package skillbill.infrastructure.host.jvm

import skillbill.infrastructure.host.process.BoundedExternalProcessOutput
import skillbill.infrastructure.host.process.BoundedExternalProcessRequest
import skillbill.infrastructure.host.process.BoundedExternalProcessRunner
import java.nio.file.Path

private const val GIT_TRACKED_FILES_TIMEOUT_SECONDS = 30L

fun readGitTrackedFiles(repoRoot: Path): Set<String>? {
  val result =
    BoundedExternalProcessRunner.run(
      BoundedExternalProcessRequest(
        argv = listOf("git", "-C", repoRoot.toString(), "ls-files"),
        deadlineSeconds = GIT_TRACKED_FILES_TIMEOUT_SECONDS,
        output = BoundedExternalProcessOutput.Captured(capBytes = null),
      ),
    )
  if (result.launchFailure || result.timedOut || result.exitCode != 0) {
    return null
  }
  return result.output.lineSequence()
    .map(String::trim)
    .filter(String::isNotEmpty)
    .toSet()
}
