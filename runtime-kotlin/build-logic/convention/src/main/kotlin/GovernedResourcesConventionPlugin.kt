import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.Directory
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.TaskProvider
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.register
import java.io.File

class GovernedResourcesConventionPlugin : Plugin<Project> {
  override fun apply(target: Project) {
    val extension = target.extensions.create<GovernedResourcesExtension>("governedResources")
    target.afterEvaluate {
      val template =
        extension.missingSourceMessageTemplate.orNull
          ?: error("governedResources.missingSourceMessageTemplate must be set for ${target.path}")
      val generatedRoot = target.layout.buildDirectory.dir("generated/${target.name}")
      val repoRootDir = target.rootProject.projectDir.parentFile
      val runtimeKotlinProjectDir = target.rootProject.projectDir
      val copyTasks =
        extension.registeredEntries().map { entry ->
          registerGovernedCopy(
            target = target,
            entry = entry,
            messageTemplate = template,
            generatedRoot = generatedRoot,
            repoRootDir = repoRootDir,
            runtimeKotlinProjectDir = runtimeKotlinProjectDir,
          )
        }
      target.extensions.getByType(org.gradle.api.plugins.JavaPluginExtension::class.java)
        .sourceSets.named("main") {
          resources.srcDir(generatedRoot)
        }
      val entries = extension.registeredEntries()
      target.tasks.named("processResources") {
        entries.zip(copyTasks).forEach { (entry, task) ->
          if (entry.includeInMainProcessResources) {
            dependsOn(task)
          }
        }
      }
      target.tasks.named("processTestResources") {
        entries.zip(copyTasks).forEach { (entry, task) ->
          if (entry.includeInTestProcessResources) {
            dependsOn(task)
          }
        }
      }
    }
  }

  private fun registerGovernedCopy(
    target: Project,
    entry: GovernedResourceEntry,
    messageTemplate: String,
    generatedRoot: Provider<Directory>,
    repoRootDir: File,
    runtimeKotlinProjectDir: File,
  ): TaskProvider<Copy> {
    val base = if (entry.sourceFromRuntimeKotlinProject) runtimeKotlinProjectDir else repoRootDir
    val sourceFile = base.resolve(entry.repoRelativeSource)
    val sourcePath = sourceFile.absolutePath
    val validateSource =
      target.tasks.register("validate${entry.taskName.replaceFirstChar { char -> char.uppercase() }}Source") {
        doLast {
          val failureMessage =
            messageTemplate
              .replace("\$sourcePath", sourcePath)
              .replace("\$schemaPath", sourcePath)
              .replace("\$guardPath", sourcePath)
              .replace("\$contractPath", sourcePath)
              .replace("\$owner", entry.owner)
          if (entry.requireSourceIsFile) {
            require(sourceFile.isFile) { failureMessage }
          } else {
            require(sourceFile.exists()) { failureMessage }
          }
        }
      }
    return target.tasks.register<Copy>(entry.taskName) {
      dependsOn(validateSource)
      from(sourcePath)
      into(generatedRoot.map { dir -> dir.dir(entry.destinationDir) })
      inputs.file(sourcePath)
    }
  }
}
