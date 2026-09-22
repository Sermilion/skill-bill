package dev.skillbill.runtime.buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.Directory
import org.gradle.api.plugins.JavaPluginExtension
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
      val copyContext =
        GovernedResourceCopyContext(
          target = target,
          messageTemplate = template,
          generatedRoot = generatedRoot,
          repoRootDir = repoRootDir,
          runtimeKotlinProjectDir = runtimeKotlinProjectDir,
        )
      val copyTasks =
        extension.registeredEntries().map { entry ->
          registerGovernedCopy(copyContext, entry)
        }
      target.extensions.getByType(JavaPluginExtension::class.java)
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
    context: GovernedResourceCopyContext,
    entry: GovernedResourceEntry,
  ): TaskProvider<Copy> {
    val base =
      if (entry.sourceFromRuntimeKotlinProject) {
        context.runtimeKotlinProjectDir
      } else {
        context.repoRootDir
      }
    val sourceFile = base.resolve(entry.repoRelativeSource)
    val sourcePath = sourceFile.absolutePath
    val messageTemplate = context.messageTemplate
    val owner = entry.owner
    val requireSourceIsFile = entry.requireSourceIsFile
    val generatedRoot = context.generatedRoot
    val validateSource = context.target.tasks.register(
      "validate${entry.taskName.replaceFirstChar { char -> char.uppercase() }}Source",
    ) {
      doLast {
        val failureMessage =
          messageTemplate
            .replace("\$sourcePath", sourcePath)
            .replace("\$schemaPath", sourcePath)
            .replace("\$guardPath", sourcePath)
            .replace("\$contractPath", sourcePath)
            .replace("\$owner", owner)
        if (requireSourceIsFile) {
          require(sourceFile.isFile) { failureMessage }
        } else {
          require(sourceFile.exists()) { failureMessage }
        }
      }
    }
    return context.target.tasks.register<Copy>(entry.taskName) {
      dependsOn(validateSource)
      from(sourcePath)
      into(generatedRoot.map { dir -> dir.dir(entry.destinationDir) })
      inputs.file(sourcePath)
    }
  }
}

private data class GovernedResourceCopyContext(
  val target: Project,
  val messageTemplate: String,
  val generatedRoot: Provider<Directory>,
  val repoRootDir: File,
  val runtimeKotlinProjectDir: File,
)
