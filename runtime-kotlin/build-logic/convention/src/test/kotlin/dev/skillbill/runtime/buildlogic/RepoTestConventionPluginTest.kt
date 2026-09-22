package dev.skillbill.runtime.buildlogic

import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class RepoTestConventionPluginTest {
  @Test
  fun `applying repo-test to a module without repository-contract sources fails at apply time`() {
    val root = ProjectBuilder.builder().withName("runtime-kotlin").build()
    val module = ProjectBuilder.builder().withName("runtime-example").withParent(root).build()

    val failure = assertThrows<Exception> {
      module.pluginManager.apply(RepoTestConventionPlugin::class.java)
    }

    val reported = generateSequence(failure as Throwable) { cause -> cause.cause }
      .mapNotNull { cause -> cause.message }
      .joinToString(separator = "\n")
    assertTrue(
      reported.contains(":runtime-example") && reported.contains("src/repoTest/kotlin"),
      "Silently skipping the wiring would drop the repoTest suites from check with a green build: $reported",
    )
  }
}
