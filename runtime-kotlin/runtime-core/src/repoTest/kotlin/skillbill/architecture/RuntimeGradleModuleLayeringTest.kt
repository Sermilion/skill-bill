package skillbill.architecture

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals

class RuntimeGradleModuleLayeringTest {
  private val runtimeRoot: Path = ArchitectureScanSupport.runtimeRoot

  @Test
  fun `settings declares runtime modules`() {
    assertEquals(
      RuntimeModuleCatalog.moduleEdgeExpectations.keys,
      declaredSettingsModules(),
    )
  }

  private fun declaredSettingsModules(): Set<String> {
    val settings = Files.readString(runtimeRoot.resolve("runtime-kotlin/settings.gradle.kts"))
    val includeBlock =
      Regex("include\\((.*?)\\)", RegexOption.DOT_MATCHES_ALL)
        .find(settings)
        ?.groupValues
        ?.get(1)
        .orEmpty()
    return Regex("\"([A-Za-z0-9:-]+)\"")
      .findAll(includeBlock)
      .map { match -> match.groupValues[1] }
      .toSet()
  }
}
