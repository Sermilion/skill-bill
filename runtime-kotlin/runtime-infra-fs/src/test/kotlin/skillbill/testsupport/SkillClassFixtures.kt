package skillbill.testsupport

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

object SkillClassFixtures {
  private const val SHIPPED_DIR = "orchestration/skill-classes"

  fun seedShippedSkillClasses(repoRoot: Path) {
    val sourceDir = findRealRepoRoot().resolve(SHIPPED_DIR)
    val destDir = repoRoot.resolve(SHIPPED_DIR)
    Files.createDirectories(destDir)
    Files.list(sourceDir).use { stream ->
      stream
        .filter { Files.isRegularFile(it) && it.fileName.toString().endsWith(".yaml") }
        .forEach { file ->
          Files.copy(file, destDir.resolve(file.fileName.toString()), StandardCopyOption.REPLACE_EXISTING)
        }
    }
  }

  private fun findRealRepoRoot(): Path {
    var current: Path? = Path.of("").toAbsolutePath().normalize()
    while (current != null) {
      if (Files.isRegularFile(current.resolve("runtime-kotlin/settings.gradle.kts")) &&
        Files.isDirectory(current.resolve(SHIPPED_DIR))
      ) {
        return current
      }
      current = current.parent
    }
    error("Could not locate real skill-bill repo root to seed skill classes from")
  }
}
