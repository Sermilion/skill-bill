package dev.skillbill.runtime.buildlogic

import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.kotlin.dsl.register
import javax.inject.Inject

abstract class GovernedResourcesExtension
  @Inject
  constructor(private val project: Project) {
    abstract val sourceRoot: DirectoryProperty

    abstract val destination: Property<String>

    internal val generatedRoot = project.layout.buildDirectory.dir("generated/${project.name}")

    fun copy(
      taskName: String,
      source: String,
      owner: String,
      destination: String = this.destination.get(),
    ) {
      val declaredSource = sourceRoot.file(source)
      val declaredOwner = owner
      val declaredTarget =
        generatedRoot.map { root -> root.file("$destination/${source.substringAfterLast('/')}") }
      val copyTask =
        project.tasks.register<GovernedResourceCopy>(taskName) {
          this.source.from(declaredSource)
          this.owner.set(declaredOwner)
          this.destination.set(declaredTarget)
        }
      project.tasks.named("processResources") { dependsOn(copyTask) }
    }
  }
