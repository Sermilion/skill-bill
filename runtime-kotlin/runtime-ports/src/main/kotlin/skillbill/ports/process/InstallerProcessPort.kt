package skillbill.ports.process

interface InstallerProcessPort {
  fun run(request: InstallerProcessRequest): InstallerProcessResult
}

data class InstallerProcessRequest(
  val executable: String,
  val arguments: List<String>,
  val environment: Map<String, String>,
  val deadlineSeconds: Long = DEFAULT_INSTALLER_PROCESS_DEADLINE_SECONDS,
)

data class InstallerProcessResult(
  val exitCode: Int,
  val output: String,
  val timedOut: Boolean = false,
  val launchFailure: Boolean = false,
)

const val DEFAULT_INSTALLER_PROCESS_DEADLINE_SECONDS: Long = 600L

const val INSTALLER_PROCESS_OUTPUT_CAP_BYTES: Int = 1024 * 1024

const val INSTALLER_OUTPUT_TRUNCATION_SENTINEL: String =
  "\n...[installer output truncated at $INSTALLER_PROCESS_OUTPUT_CAP_BYTES bytes]..."
