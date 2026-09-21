package skillbill.infrastructure.launcher.codegraph

import me.tatarka.inject.annotations.Inject
import skillbill.infrastructure.host.process.BoundedExternalProcessRequest
import skillbill.infrastructure.host.process.BoundedExternalProcessRunner
import skillbill.ports.agentrun.ExecutableLookup
import skillbill.ports.codegraph.CodeGraphExecutablePort
import skillbill.ports.codegraph.model.CodeGraphCommandResult
import java.nio.file.Path

private const val MAX_SETUP_OUTPUT_CHARS = 8_192

@Inject
internal class JvmCodeGraphExecutable(
  private val executableLookup: ExecutableLookup,
) : CodeGraphExecutablePort {
  override fun isAvailable(): Boolean = executableLookup.onPath("codegraph")

  override fun execute(
    command: List<String>,
    workingDirectory: Path,
    environment: Map<String, String>,
  ): CodeGraphCommandResult {
    val result = BoundedExternalProcessRunner.run(
      BoundedExternalProcessRequest(
        argv = command,
        workingDirectory = workingDirectory,
        mergeEnvironment = environment,
        deadlineSeconds = 60,
        outputCapBytes = MAX_SETUP_OUTPUT_CHARS.toLong(),
      ),
    )
    return CodeGraphCommandResult(
      exitCode = result.exitCode.takeUnless { result.timedOut || result.launchFailure },
      stdout = result.output.take(MAX_SETUP_OUTPUT_CHARS),
      stderr = "",
      started = !result.launchFailure,
    )
  }
}
