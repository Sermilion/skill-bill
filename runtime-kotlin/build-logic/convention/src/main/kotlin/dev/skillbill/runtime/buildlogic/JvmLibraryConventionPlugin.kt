package dev.skillbill.runtime.buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.bundling.Jar
import org.gradle.kotlin.dsl.named

class JvmLibraryConventionPlugin : Plugin<Project> {
  override fun apply(target: Project) {
    with(target) {
      with(pluginManager) {
        apply("org.jetbrains.kotlin.jvm")
        apply("java-library")
      }

      configureKotlinJvm()
      configureNestedArchiveBaseName()
    }
  }

  private fun Project.configureNestedArchiveBaseName() {
    val parentName = parent?.name
    if (parentName == null || parent == rootProject) {
      return
    }
    tasks.named<Jar>("jar") {
      archiveBaseName.set("$parentName-${project.name}")
    }
  }
}
