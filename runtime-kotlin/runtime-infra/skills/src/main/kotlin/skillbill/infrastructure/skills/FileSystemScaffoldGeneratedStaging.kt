package skillbill.infrastructure.skills

import me.tatarka.inject.annotations.Inject
import skillbill.error.SkillAlreadyExistsError
import skillbill.infrastructure.host.jvm.rollbackDeleteEmptyDirectory
import skillbill.infrastructure.host.jvm.rollbackDeleteRegularFileOrSymlink
import skillbill.ports.scaffold.staging.ScaffoldGeneratedStagingPort
import skillbill.ports.scaffold.staging.model.ScaffoldStageFileRequest
import skillbill.ports.scaffold.staging.model.ScaffoldStageFileResult
import java.nio.file.Files
import java.nio.file.Path

@Inject
class FileSystemScaffoldGeneratedStaging : ScaffoldGeneratedStagingPort {
  override fun stageFile(request: ScaffoldStageFileRequest): ScaffoldStageFileResult {
    val path = request.targetPath
    if (Files.exists(path)) {
      throw SkillAlreadyExistsError(
        "Skill target '$path' already exists. Remove it or pick a new name before retrying.",
      )
    }
    val createdDirs = mutableListOf<Path>()
    var cursor = path.parent
    while (cursor != null && !Files.exists(cursor)) {
      createdDirs.add(cursor)
      cursor = cursor.parent
    }
    createdDirs.asReversed().forEach { dir ->
      Files.createDirectories(dir)
    }
    Files.writeString(path, request.content)
    return ScaffoldStageFileResult(
      createdFile = path,
      createdDirectories = createdDirs.toList(),
    )
  }

  override fun rollbackFile(path: Path) {
    rollbackDeleteRegularFileOrSymlink(path)
  }

  override fun rollbackDirectory(path: Path) {
    rollbackDeleteEmptyDirectory(path)
  }
}
