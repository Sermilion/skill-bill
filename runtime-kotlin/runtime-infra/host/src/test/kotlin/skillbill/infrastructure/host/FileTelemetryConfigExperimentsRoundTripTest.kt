package skillbill.infrastructure.host

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals

class FileTelemetryConfigExperimentsRoundTripTest {
  @TempDir
  lateinit var tempDir: Path

  @Test
  fun `ensure preserves experiments array`() {
    val config = tempDir.resolve("config.json")
    Files.writeString(config, """{"install_id":"x","telemetry":{"level":"off"},"experiments":["codegraph"]}""")
    val doc = ensureTelemetryConfigFile(config)
    assertEquals(listOf("codegraph"), doc.payload["experiments"])
    assertEquals("off", (doc.payload["telemetry"] as Map<*, *>)["level"])
  }

  @Test
  fun `ensure does not invent an experiment selection`() {
    val config = tempDir.resolve("config-without-experiments.json")
    Files.writeString(config, """{"install_id":"x","telemetry":{"level":"off"}}""")

    val doc = ensureTelemetryConfigFile(config)

    assertEquals(false, doc.payload.containsKey("experiments"))
    assertEquals("off", (doc.payload["telemetry"] as Map<*, *>)["level"])
  }
}
