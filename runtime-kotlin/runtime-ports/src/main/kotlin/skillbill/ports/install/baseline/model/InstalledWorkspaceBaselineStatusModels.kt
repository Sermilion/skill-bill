package skillbill.ports.install.baseline.model

import java.nio.file.Path

data class InstalledWorkspaceBaselineStatusRequest(
  val installRoot: Path,
  val installHome: Path,
)

data class InstalledWorkspaceBaselineStatusResult(
  val modifiedSkillRelativePaths: Set<String>,
)
