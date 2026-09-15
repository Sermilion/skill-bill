package skillbill.ports.scaffold.repo.model

import java.nio.file.Path

data class ScaffoldAuthoringValidationRequest(
  val repoRoot: Path,
  val skillName: String,
  val packageName: String,
  val platform: String,
  val displayName: String,
  val family: String,
  val area: String,
  val skillFile: Path,
  val contentFile: Path,
)

data class ScaffoldAuthoringValidationResult(
  val issues: List<String>,
)
