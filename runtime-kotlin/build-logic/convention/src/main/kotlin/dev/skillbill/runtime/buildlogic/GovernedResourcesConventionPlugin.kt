package dev.skillbill.runtime.buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension

class GovernedResourcesConventionPlugin : Plugin<Project> {
  override fun apply(target: Project) {
    val extension =
      target.extensions.create(
        "governedResources",
        GovernedResourcesExtension::class.java,
        target,
      )
    extension.sourceRoot.convention(target.rootProject.layout.projectDirectory.dir(".."))
    target.extensions.getByType(JavaPluginExtension::class.java)
      .sourceSets.named("main") {
        resources.srcDir(extension.generatedRoot)
      }
  }
}
