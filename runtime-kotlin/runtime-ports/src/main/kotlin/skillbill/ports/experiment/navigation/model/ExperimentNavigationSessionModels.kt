package skillbill.ports.experiment.navigation.model

import java.nio.file.Path

data class ExperimentNavigationSessionRequest(
  val pairId: String,
  val armId: String,
  val repoRoot: Path,
  val revision: String = "HEAD",
  val frozenSpecBytes: ByteArray,
  val acceptanceCriteria: List<String>,
  val treatmentEnabled: Boolean,
) {
  init {
    require(pairId.isNotBlank()) { "pairId is required." }
    require(armId.isNotBlank()) { "armId is required." }
    require(revision.isNotBlank()) { "revision is required." }
    require(frozenSpecBytes.isNotEmpty()) { "frozenSpecBytes must not be empty." }
    require(acceptanceCriteria.isNotEmpty()) { "acceptanceCriteria must not be empty." }
  }
}

enum class ExperimentNavigationTerminalOutcome {
  SEARCH_COMPLETED,
  BUDGET_EXHAUSTED,
  INSUFFICIENT_EVIDENCE,
  CANCELLED,
  FAILED,
}

data class ExperimentNavigationSessionResult(
  val outcome: ExperimentNavigationTerminalOutcome,
  val deliveredPaths: List<String>,
  val shortlistedPaths: List<String>,
  val readReceipts: List<ExperimentNavigationReadReceipt> = emptyList(),
  val attemptCount: Int = 0,
  val labelCoverage: ExperimentNavigationLabelCoverage = ExperimentNavigationLabelCoverage(),
  val excludedPaths: List<String> = emptyList(),
  val restrictedBaseline: Boolean = false,
)

data class ExperimentNavigationRunRequest(
  val name: String,
  val repoRoot: Path,
  val revision: String,
  val specBytes: ByteArray,
  val acceptanceCriteria: List<String>,
) {
  init {
    require(name.isNotBlank()) { "name is required." }
    require(revision.isNotBlank()) { "revision is required." }
    require(specBytes.isNotEmpty()) { "specBytes must not be empty." }
    require(acceptanceCriteria.isNotEmpty()) { "acceptanceCriteria must not be empty." }
  }
}

data class ExperimentNavigationReadReceipt(
  val path: String,
  val purpose: String,
)

data class ExperimentNavigationLabelCoverage(
  val labelledCriteria: Int = 0,
  val totalCriteria: Int = 0,
  val precisionAvailable: Boolean = false,
)
