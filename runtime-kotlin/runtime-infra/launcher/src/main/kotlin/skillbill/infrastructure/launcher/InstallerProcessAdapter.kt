package skillbill.infrastructure.launcher

import me.tatarka.inject.annotations.Inject
import skillbill.infrastructure.host.process.BoundedExternalProcessRequest
import skillbill.infrastructure.host.process.BoundedExternalProcessRunner
import skillbill.ports.process.InstallerProcessPort
import skillbill.ports.process.model.InstallerProcessRequest
import skillbill.ports.process.model.InstallerProcessResult

@Inject
class InstallerProcessAdapter : InstallerProcessPort {
  override fun run(request: InstallerProcessRequest): InstallerProcessResult {
    val deadlineSeconds = request.deadlineSeconds.coerceAtLeast(1L)
    val result =
      BoundedExternalProcessRunner.run(
        BoundedExternalProcessRequest(
          argv = listOf(request.executable) + request.arguments,
          environment = request.environment,
          clearEnvironment = true,
          deadlineSeconds = deadlineSeconds,
        ),
      )
    if (result.launchFailure) {
      return InstallerProcessResult(
        exitCode = 1,
        output = result.output.replaceFirst("Failed to launch process", "Failed to launch installer"),
        launchFailure = true,
      )
    }
    if (result.timedOut) {
      return InstallerProcessResult(
        exitCode = result.exitCode,
        output = result.output,
        timedOut = true,
      )
    }
    return InstallerProcessResult(exitCode = result.exitCode, output = result.output)
  }
}
