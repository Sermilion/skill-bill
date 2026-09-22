package dev.skillbill.runtime.buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

object RuntimeImageLicense {
  fun stage(source: Path, destinations: List<Path>) {
    require(Files.isRegularFile(source)) { "Repository LICENSE is missing at $source." }
    destinations.forEach { destination ->
      Files.createDirectories(destination.parent)
      Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING)
    }
  }

  fun matches(source: Path, candidate: Path): Boolean =
    Files.isRegularFile(source) && Files.isRegularFile(candidate) && Files.mismatch(source, candidate) == -1L
}

abstract class VerifyRuntimeImageLicenseTask : DefaultTask() {

  @get:InputFile
  @get:PathSensitive(PathSensitivity.NONE)
  abstract val repositoryLicense: RegularFileProperty

  @get:InputFiles
  @get:PathSensitive(PathSensitivity.NONE)
  abstract val packagedLicenses: ConfigurableFileCollection

  @TaskAction
  fun verify() {
    val source = repositoryLicense.get().asFile.toPath()
    packagedLicenses.forEach { candidate ->
      if (!RuntimeImageLicense.matches(source, candidate.toPath())) {
        throw GradleException("Packaged LICENSE differs from repository LICENSE at ${candidate.toPath()}.")
      }
    }
  }
}
