package skillbill.ports.experiment.codegraph.model

import java.nio.file.Path

data class CodeGraphCandidateHit(
  val name: String,
  val file: String,
  val kind: String,
  val score: Double? = null,
  val line: Int? = null,
)

data class CodeGraphQueryRequest(
  val worktreeRoot: Path,
  val queryText: String,
  val limit: Int = 32,
  val graphIndexDirectory: Path? = null,
  val pairId: String? = null,
  val unresolvedConstructNotes: List<String> = emptyList(),
)

data class CodeGraphQueryResult(
  val hits: List<CodeGraphCandidateHit>,
  val receipt: Map<String, Any?>,
)

data class CodeGraphInstalledTool(
  val releaseTag: String,
  val binaryPath: Path,
  val platformId: String,
  val sha256: String,
  val provenance: String = "managed",
)

data class CodeGraphToolInstallRequest(
  val userHome: Path,
  val pairId: String,
  val pinnedReleaseTag: String? = null,
  val localExecutableOverride: Path? = null,
)

data class CodeGraphPairSetupRequest(
  val pairId: String,
  val userHome: Path,
  val selectedExperimentNames: List<String>,
  val pinnedReleaseTag: String? = null,
  val localExecutableOverride: Path? = null,
)

data class CodeGraphPairSetupResult(
  val provisioned: Boolean,
  val installedTool: CodeGraphInstalledTool? = null,
  val installDurationMs: Long = 0,
  val reason: String? = null,
)

data class CodeGraphPairUsageSnapshot(
  val pairId: String,
  val installDurationMs: Long = 0,
  val indexDurationMs: Long = 0,
  val syncDurationMs: Long = 0,
  val queryCount: Int = 0,
  val evidenceConsumedBytes: Long = 0,
  val degraded: Boolean = false,
  val notExercised: Boolean = false,
  val degradationReason: String? = null,
)
