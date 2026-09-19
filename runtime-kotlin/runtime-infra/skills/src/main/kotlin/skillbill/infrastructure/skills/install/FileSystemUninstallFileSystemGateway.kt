package skillbill.infrastructure.skills.install
import me.tatarka.inject.annotations.Inject
import skillbill.infrastructure.skills.externaladdon.entries
import skillbill.infrastructure.skills.externaladdon.entry
import skillbill.infrastructure.skills.externaladdon.map
import skillbill.infrastructure.skills.externaladdon.root
import skillbill.infrastructure.skills.file.map
import skillbill.infrastructure.skills.nativeagent.root
import skillbill.infrastructure.skills.scaffold.path
import skillbill.infrastructure.skills.scaffold.skills
import skillbill.ports.system.UninstallPathsPort
import java.nio.file.Files
import java.nio.file.Path
import java.util.Comparator

@Inject
class FileSystemUninstallFileSystemGateway : UninstallPathsPort {
  override fun listImmediateDirectoryNames(root: Path): List<String> {
    if (!Files.isDirectory(root)) {
      return emptyList()
    }
    return Files.newDirectoryStream(root).use { entries ->
      entries.map { entry -> entry.fileName.toString() }
    }
  }

  override fun exists(path: Path): Boolean = Files.exists(path)

  override fun isSymbolicLink(path: Path): Boolean = Files.isSymbolicLink(path)

  override fun readSymbolicLink(path: Path): Path = Files.readSymbolicLink(path)

  override fun deleteIfExists(path: Path): Boolean = Files.deleteIfExists(path)

  override fun removeTree(path: Path): List<Path> {
    val removed = mutableListOf<Path>()
    Files.walk(path).use { stream ->
      stream.sorted(Comparator.reverseOrder()).forEach { entry ->
        if (Files.deleteIfExists(entry)) {
          removed.add(entry)
        }
      }
    }
    return removed
  }
}
