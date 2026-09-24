package skillbill.infrastructure.workflow.validation

import me.tatarka.inject.annotations.Inject
import skillbill.infrastructure.host.process.BoundedExternalProcessOutput
import skillbill.infrastructure.host.process.BoundedExternalProcessRequest
import skillbill.infrastructure.host.process.BoundedExternalProcessRunner
import skillbill.ports.validation.PrCheckProcessRunner
import skillbill.ports.validation.model.PrCheckRunResult
import java.nio.file.Path
import java.time.Clock
import java.util.concurrent.TimeUnit

class FileSystemPrCheckProcessRunner internal constructor(
  private val clock: Clock,
  private val timeoutSeconds: Long,
) : PrCheckProcessRunner {
  @Inject
  constructor(clock: Clock) : this(clock, TimeUnit.MINUTES.toSeconds(PROCESS_TIMEOUT_MINUTES))

  override fun run(
    command: String,
    repoRoot: Path,
  ): PrCheckRunResult {
    require(command.isNotBlank()) { "Plugin check command must be non-blank." }
    val root = repoRoot.normalize().toAbsolutePath()
    val started = clock.millis()
    val result =
      BoundedExternalProcessRunner.run(
        BoundedExternalProcessRequest(
          argv = listOf("bash", "-lc", command),
          workingDirectory = root,
          deadlineSeconds = timeoutSeconds,
          output = BoundedExternalProcessOutput.Captured(OUTPUT_CAP_BYTES),
        ),
      )
    return PrCheckRunResult(exitCode = result.exitCode, durationMs = clock.millis() - started)
  }
}

private const val PROCESS_TIMEOUT_MINUTES = 30L
private const val OUTPUT_CAP_BYTES = 8L * 1024L
