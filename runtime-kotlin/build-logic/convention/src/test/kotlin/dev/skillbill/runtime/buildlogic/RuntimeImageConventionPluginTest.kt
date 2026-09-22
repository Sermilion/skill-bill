package dev.skillbill.runtime.buildlogic

import org.gradle.api.Task
import org.gradle.api.tasks.TaskDependency
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RuntimeImageConventionPluginTest {
  @Test
  fun `runtime zip verifies the packaged license and is finalized by the sidecar`() {
    val project = ProjectBuilder.builder().build()
    project.pluginManager.apply("application")
    project.pluginManager.apply(RuntimeImageConventionPlugin::class.java)

    val runtimeZip = project.tasks.getByName("runtimeZip")

    assertTrue(
      runtimeZip.taskDependencies.dependencyNames(runtimeZip).contains("verifyRuntimeImageLicense"),
      "runtimeZip must not publish an image whose packaged LICENSE was never verified.",
    )
    assertTrue(
      runtimeZip.finalizedBy.dependencyNames(runtimeZip).contains("runtimeZipSha256"),
      "runtimeZip must produce its SHA-256 sidecar, or a release asset ships unverifiable.",
    )
  }

  @Test
  fun `license verification inspects the installed distribution and the runtime image`() {
    val project = ProjectBuilder.builder().build()
    project.pluginManager.apply("application")
    project.pluginManager.apply(RuntimeImageConventionPlugin::class.java)

    val verification =
      project.tasks.getByName("verifyRuntimeImageLicense") as VerifyRuntimeImageLicenseTask
    val inspected = verification.packagedLicenses.files.map { file -> file.invariantSeparatorsPath }

    assertTrue(
      inspected.any { path -> path.contains("/install/") && path.endsWith("/LICENSE") },
      "Verification that inspects nothing under build/install passes vacuously: $inspected",
    )
    assertTrue(
      inspected.any { path -> path.endsWith("/image/LICENSE") },
      "Verification that skips build/image lets the runtime image ship without a LICENSE: $inspected",
    )
  }
}

private fun TaskDependency.dependencyNames(task: Task): Set<String> =
  getDependencies(task).map(Task::getName).toSet()
