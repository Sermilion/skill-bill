package skillbill.ports.process

import skillbill.ports.process.model.InstallerProcessRequest
import skillbill.ports.process.model.InstallerProcessResult

interface InstallerProcessPort {
  fun run(request: InstallerProcessRequest): InstallerProcessResult
}

const val DEFAULT_INSTALLER_PROCESS_DEADLINE_SECONDS: Long = 600L

const val INSTALLER_PROCESS_OUTPUT_CAP_BYTES: Int = 1024 * 1024

const val INSTALLER_OUTPUT_TRUNCATION_SENTINEL: String =
  "\n...[installer output truncated at $INSTALLER_PROCESS_OUTPUT_CAP_BYTES bytes]..."
