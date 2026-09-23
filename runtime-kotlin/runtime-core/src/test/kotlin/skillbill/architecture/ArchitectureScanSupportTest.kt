package skillbill.architecture

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertFailsWith

class ArchitectureScanSupportTest {
  @Test
  fun `named source walkers fail instead of treating a missing root as empty`() {
    val missingRoot = Files.createTempDirectory("missing-architecture-root").resolve("src/main/kotlin")

    assertFailsWith<IllegalStateException> {
      ArchitectureScanSupport.kotlinFilesUnder(missingRoot)
    }
    assertFailsWith<IllegalStateException> {
      ArchitectureScanSupport.authoredKotlinSourcesUnder(missingRoot)
    }
  }
}
