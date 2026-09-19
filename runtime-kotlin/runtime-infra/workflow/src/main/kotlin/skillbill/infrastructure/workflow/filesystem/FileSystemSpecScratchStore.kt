package skillbill.infrastructure.workflow.filesystem
import me.tatarka.inject.annotations.Inject
import skillbill.infrastructure.workflow.git.scoped.paths
import skillbill.infrastructure.workflow.git.standard.path
import skillbill.infrastructure.workflow.git.standard.paths
import skillbill.infrastructure.workflow.git.suppression.path
import skillbill.infrastructure.workflow.git.workflow.path
import skillbill.infrastructure.workflow.git.workflow.paths
import skillbill.infrastructure.workflow.review.broker.path
import skillbill.infrastructure.workflow.review.specialists.system.directory
import skillbill.infrastructure.workflow.review.specialists.system.path
import skillbill.ports.workflow.specscratch.SpecScratchStore
import java.nio.file.Files
import java.nio.file.Path
import java.util.Comparator

@Inject
class FileSystemSpecScratchStore : SpecScratchStore {
  override fun deleteFileIfExists(path: Path) {
    Files.deleteIfExists(path)
  }

  override fun deleteDirectoryIfExists(directory: Path) {
    if (!Files.exists(directory)) return
    Files.walk(directory).use { paths ->
      paths
        .sorted(Comparator.reverseOrder())
        .forEach(Files::deleteIfExists)
    }
  }
}
