package skillbill.ports.install.reconcile.model

import skillbill.install.model.ReconciliationPlan
import java.nio.file.Path

data class InstallReconcileApplyRequest(
  val home: Path,
  val upstreamRepoRoot: Path,
  val upstreamSkillsRoot: Path,
  val upstreamPlatformPacksRoot: Path,
  val localRepoRoot: Path,
  val localSkillsRoot: Path,
  val localPlatformPacksRoot: Path,
)

data class InstallReconcileApplyResult(
  val plan: ReconciliationPlan,
  val installedPaths: List<String>,
  val prunedPaths: List<String>,
)
