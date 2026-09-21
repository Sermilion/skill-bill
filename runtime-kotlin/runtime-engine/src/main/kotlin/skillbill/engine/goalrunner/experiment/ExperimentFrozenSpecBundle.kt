package skillbill.engine.goalrunner.experiment

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

object ExperimentFrozenSpecBundle {
  fun copy(source: Path, destination: Path) {
    Files.walk(source).use { paths ->
      paths.forEach { path ->
        val target = destination.resolve(source.relativize(path).toString())
        if (Files.isDirectory(path)) {
          Files.createDirectories(target)
        } else {
          Files.createDirectories(requireNotNull(target.parent))
          Files.copy(path, target, StandardCopyOption.REPLACE_EXISTING)
        }
      }
    }
  }
}
