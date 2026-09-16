package skillbill.application.updatecheck.model

enum class UpdateRunStatus {
  COMPLETED,
  FAILED,
  DRY_RUN,
  SKIPPED,
  CHECK_FAILED,
  DOWNLOAD_FAILED,
}

data class UpdateRunRequest(
  val releaseTag: String?,
  val clean: Boolean,
  val dryRun: Boolean,
  val userHome: java.nio.file.Path,
  val environment: Map<String, String>,
)

data class UpdateRunPlan(
  val command: String,
  val installerArgs: List<String>,
  val scriptUrl: String,
)

data class UpdateRunResult(
  val status: UpdateRunStatus,
  val exitCode: Int,
  val plan: UpdateRunPlan,
  val installerOutput: String? = null,
  val updateCheck: UpdateCheckResult? = null,
  val reason: String? = null,
)

const val INSTALL_SCRIPT_URL: String = "https://raw.githubusercontent.com/oila-gmbh/skill-bill/main/install.sh"
