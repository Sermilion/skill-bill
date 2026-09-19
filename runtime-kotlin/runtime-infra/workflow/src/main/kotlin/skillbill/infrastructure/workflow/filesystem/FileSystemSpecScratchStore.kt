package skillbill.infrastructure.workflow.filesystem
import me.tatarka.inject.annotations.Inject
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
