package skillbill.infrastructure.workflow.validation

import me.tatarka.inject.annotations.Inject
import skillbill.ports.validation.PrCheckProcessRunner
import skillbill.ports.validation.model.PrCheckRunResult
import java.nio.file.Path
import java.time.Clock
import java.util.concurrent.TimeUnit

@Inject
class FileSystemPrCheckProcessRunner(
  private val clock: Clock,
) : PrCheckProcessRunner {
  private companion object {
    const val PROCESS_TIMEOUT_MINUTES = 30L
  }

  override fun run(
    command: String,
    repoRoot: Path,
  ): PrCheckRunResult {
    require(command.isNotBlank()) { "Plugin check command must be non-blank." }
    val root = repoRoot.normalize().toAbsolutePath()
    val started = clock.millis()
    val process =
      ProcessBuilder(listOf("bash", "-lc", command))
        .directory(root.toFile())
        .redirectErrorStream(true)
        .start()
    val finished =
      process.waitFor(
        TimeUnit.MINUTES.toMillis(PROCESS_TIMEOUT_MINUTES),
        TimeUnit.MILLISECONDS,
      )
    val durationMs = clock.millis() - started
    if (!finished) {
      process.destroyForcibly()
      return PrCheckRunResult(exitCode = 124, durationMs = durationMs)
    }
    return PrCheckRunResult(exitCode = process.exitValue(), durationMs = durationMs)
  }
}
