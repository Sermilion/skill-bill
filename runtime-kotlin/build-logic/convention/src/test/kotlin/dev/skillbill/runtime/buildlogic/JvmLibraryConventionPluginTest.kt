package dev.skillbill.runtime.buildlogic

import org.gradle.api.tasks.bundling.Jar
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class JvmLibraryConventionPluginTest {
  @Test
  fun `nested module jars carry the parent prefixed archive name`() {
    val root = ProjectBuilder.builder().withName("runtime-kotlin").build()
    val parent = ProjectBuilder.builder().withName("runtime-infra").withParent(root).build()
    val nested = ProjectBuilder.builder().withName("skills").withParent(parent).build()

    nested.pluginManager.apply(JvmLibraryConventionPlugin::class.java)

    assertEquals(
      "runtime-infra-skills",
      nested.tasks.named("jar", Jar::class.java).get().archiveBaseName.get(),
      "Nested runtime-infra module jars collide on flat names without the parent prefix.",
    )
  }
}
