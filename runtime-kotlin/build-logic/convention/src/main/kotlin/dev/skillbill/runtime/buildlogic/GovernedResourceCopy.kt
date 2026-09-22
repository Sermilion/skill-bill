package dev.skillbill.runtime.buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import javax.inject.Inject

@DisableCachingByDefault(because = "Copying one governed resource is cheaper than a build cache round trip.")
abstract class GovernedResourceCopy
  @Inject
  constructor(
    private val fileSystemOperations: FileSystemOperations,
  ) : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val source: ConfigurableFileCollection

    @get:Input
    abstract val owner: Property<String>

    @get:OutputFile
    abstract val destination: RegularFileProperty

    @TaskAction
    fun copyGovernedResource() {
      val resolvedSource = source.singleFile
      if (!resolvedSource.isFile) {
        throw GradleException("${owner.get()} is missing at ${resolvedSource.absolutePath}.")
      }
      val target = destination.get().asFile
      fileSystemOperations.copy {
        from(resolvedSource)
        into(target.parentFile)
      }
    }
  }
