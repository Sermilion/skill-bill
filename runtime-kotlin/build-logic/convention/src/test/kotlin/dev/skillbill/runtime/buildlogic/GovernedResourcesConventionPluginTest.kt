package dev.skillbill.runtime.buildlogic

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

private const val OWNER = "SKILL-000: canonical sample schema"

private const val SIDECAR_OWNER = "SKILL-000: canonical sidecar schema"

class GovernedResourcesConventionPluginTest {
  @Test
  fun `an absent governed source fails the copy task and writes nothing`(
    @TempDir projectDir: File,
  ) {
    writeGovernedProject(projectDir)
    writeSidecarSource(projectDir)

    val result = runner(projectDir).buildAndFail()

    val failure = Regex("""$OWNER is missing at (/\S*/source/sample\.yaml)\.""")
    assertTrue(
      failure.containsMatchIn(result.output),
      "A missing governed source must fail naming its owner and absolute path: ${result.output}",
    )
    assertFalse(
      generatedResource(projectDir).exists(),
      "A failed governed copy must leave no packaged resource behind.",
    )
  }

  @Test
  fun `each declared governed source lands under its own destination inside the generated root`(
    @TempDir projectDir: File,
  ) {
    writeGovernedProject(projectDir)
    writeSource(projectDir, "schema: sample\n")
    writeSidecarSource(projectDir)

    val result = runner(projectDir).build()

    assertEquals(
      TaskOutcome.SUCCESS,
      result.task(":copySample")?.outcome,
      "processResources must run the governed copy, or the jar ships without the schema.",
    )
    assertEquals(
      TaskOutcome.SUCCESS,
      result.task(":copySidecar")?.outcome,
      "An entry declared after the first must also be wired into processResources.",
    )
    assertEquals("schema: sample\n", generatedResource(projectDir).readText())
    assertEquals(
      "schema: sidecar\n",
      generatedSidecarResource(projectDir).readText(),
      "A per-entry destination override must place only its own file, at the classpath path the " +
        "runtime schema loader reads.",
    )
  }

  @Test
  fun `unchanged governed sources leave every copy task up to date`(
    @TempDir projectDir: File,
  ) {
    writeGovernedProject(projectDir)
    writeSource(projectDir, "schema: sample\n")
    writeSidecarSource(projectDir)
    runner(projectDir).build()

    val second = runner(projectDir).build()

    assertEquals(
      TaskOutcome.UP_TO_DATE,
      second.task(":copySample")?.outcome,
      "A governed copy that never reports up to date defeats incremental builds.",
    )
    assertEquals(
      TaskOutcome.UP_TO_DATE,
      second.task(":copySidecar")?.outcome,
      "Copy tasks sharing one generated root must not declare overlapping outputs.",
    )
  }

  private fun runner(projectDir: File): GradleRunner =
    GradleRunner.create()
      .withProjectDir(projectDir)
      .withPluginClasspath()
      .withArguments("processResources")

  private fun generatedResource(projectDir: File): File =
    projectDir.resolve("build/generated/${projectDir.name}/skillbill/generated/sample.yaml")

  private fun generatedSidecarResource(projectDir: File): File =
    projectDir.resolve("build/generated/${projectDir.name}/skillbill/sidecar/sidecar.yaml")

  private fun writeSource(
    projectDir: File,
    content: String,
  ) {
    val source = projectDir.resolve("source/sample.yaml")
    source.parentFile.mkdirs()
    source.writeText(content)
  }

  private fun writeSidecarSource(projectDir: File) {
    val source = projectDir.resolve("source/sidecar.yaml")
    source.parentFile.mkdirs()
    source.writeText("schema: sidecar\n")
  }

  private fun writeGovernedProject(projectDir: File) {
    val projectName = projectDir.name
    projectDir.resolve("settings.gradle.kts").writeText("rootProject.name = \"$projectName\"\n")
    projectDir.resolve("build.gradle.kts").writeText(
      """
      plugins {
        id("java-library")
        id("skillbill.governed-resources")
      }

      governedResources {
        sourceRoot.set(layout.projectDirectory.dir("source"))
        destination.set("skillbill/generated")

        copy("copySample", "sample.yaml", "$OWNER")
        copy("copySidecar", "sidecar.yaml", "$SIDECAR_OWNER", "skillbill/sidecar")
      }
      """.trimIndent() + "\n",
    )
  }
}
