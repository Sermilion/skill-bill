package skillbill.ports.scaffold.staging.model

import java.nio.file.Path

data class ScaffoldStageFileRequest(
  val targetPath: Path,
  val content: String,
)

data class ScaffoldStageFileResult(
  val createdFile: Path,
  val createdDirectories: List<Path>,
)
