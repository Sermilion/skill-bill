package skillbill.architecture

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RuntimeCoreSerializationDependencyArchitectureTest {
  @Test
  fun `runtime-core keeps kotlinx serialization on the test classpath only`() {
    val buildFile = ArchitectureScanSupport.runtimeRoot.resolve("runtime-kotlin/runtime-core/build.gradle.kts")
    val source = Files.readString(buildFile)
    assertTrue(
      source.contains("testImplementation(libs.kotlinx.serialization.json)"),
      "runtime-core must declare kotlinx.serialization.json as testImplementation.",
    )
    assertFalse(
      Regex("""^\s*implementation\(libs\.kotlinx\.serialization\.json\)""", RegexOption.MULTILINE)
        .containsMatchIn(source),
      "runtime-core must not declare kotlinx.serialization.json as implementation.",
    )
  }
}
