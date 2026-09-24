package skillbill.contracts.system

import skillbill.contracts.JsonPayloadContract
import skillbill.contracts.SharedPayloadKeys

data class VersionContract(
  val version: String,
) : JsonPayloadContract {
  override fun toPayload(): Map<String, Any?> = linkedMapOf("version" to version)
}

data class RuntimeProvenanceContract(
  val executablePath: String,
  val version: String,
  val buildId: String,
) : JsonPayloadContract {
  override fun toPayload(): Map<String, Any?> =
    linkedMapOf(
      "executable_path" to executablePath,
      "version" to version,
      "build_id" to buildId,
    )
}

data class DoctorContract(
  val version: String,
  val dbPath: String,
  val dbExists: Boolean,
  val telemetryEnabled: Boolean,
  val telemetryLevel: String,
) : JsonPayloadContract {
  override fun toPayload(): Map<String, Any?> =
    linkedMapOf(
      "version" to version,
      "db_path" to dbPath,
      "db_exists" to dbExists,
      "telemetry_enabled" to telemetryEnabled,
      "telemetry_level" to telemetryLevel,
    )
}

data class UpdateCheckContract(
  val status: String,
  val installedVersion: String? = null,
  val latestVersion: String? = null,
  val releaseUrl: String? = null,
  val recommendedInstallCommand: String? = null,
  val reason: String? = null,
  val releaseNotes: String? = null,
) : JsonPayloadContract {
  override fun toPayload(): Map<String, Any?> =
    buildMap {
      put(SharedPayloadKeys.STATUS, status)
      put(UpdateCheckPayloadKeys.INSTALLED_VERSION, installedVersion)
      put(UpdateCheckPayloadKeys.LATEST_VERSION, latestVersion)
      put(UpdateCheckPayloadKeys.RELEASE_URL, releaseUrl)
      put(UpdateCheckPayloadKeys.RECOMMENDED_INSTALL_COMMAND, recommendedInstallCommand)
      put(UpdateCheckPayloadKeys.REASON, reason)
      put(UpdateCheckPayloadKeys.RELEASE_NOTES, releaseNotes)
    }
}

fun VersionContract.toRuntimeProvenance(
  executablePath: String,
  buildId: String = version,
): RuntimeProvenanceContract =
  RuntimeProvenanceContract(
    executablePath = executablePath,
    version = version,
    buildId = buildId,
  )
