package skillbill.cli

import skillbill.cli.core.CliRuntime
import skillbill.cli.model.CliRuntimeContext
import skillbill.contracts.JsonCodec
import skillbill.contracts.experiment.config.ExperimentConfigPayloadKeys
import skillbill.contracts.experiment.config.ExperimentNames
import skillbill.contracts.typesafe.SystemOneConfigPayloadKeys
import skillbill.ports.telemetry.model.RemoteTransportResponse
import skillbill.ports.telemetry.transport.RemoteTransportPort
import skillbill.telemetry.CONFIG_ENVIRONMENT_KEY
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

class CliTypeSafeRuntimeTest {
  @Test
  fun `configure api key preserves unrelated config keys`() {
    val tempDir = Files.createTempDirectory("skillbill-cli-typesafe-configure")
    val configPath = writeRichConfig(tempDir)
    val context = CliRuntimeContext(environment = mapOf(CONFIG_ENVIRONMENT_KEY to configPath.toString()))
    val secret = "cli-stored-secret-value"

    val payload =
      runJson(
        listOf("typesafe", "configure", "--api-key", secret, "--format", "json"),
        context,
      )
    assertEquals(true, payload[SystemOneConfigPayloadKeys.API_KEY_CONFIGURED])
    assertEquals(false, payload[SystemOneConfigPayloadKeys.ENABLED])

    val stored = decodeJsonObject(Files.readString(configPath))
    assertEquals("retained-install-id", stored["install_id"])
    assertEquals(listOf("/tmp/addons"), stored["external_addon_sources"])
    assertEquals(mapOf("default" to "claude"), stored["execution_matrix"])
    val telemetry = stored["telemetry"] as Map<*, *>
    assertEquals("anonymous", telemetry["level"])
    assertEquals("", telemetry["proxy_url"])
    assertEquals(10, telemetry["batch_size"])
    val typesafe = stored[SystemOneConfigPayloadKeys.ROOT] as Map<*, *>
    assertEquals(secret, typesafe[SystemOneConfigPayloadKeys.API_KEY])
    assertFalse(stored.containsKey(ExperimentConfigPayloadKeys.EXPERIMENTS))
  }

  @Test
  fun `configure enable lists typesafe and accepts a supplied or previously stored key`() {
    val suppliedDir = Files.createTempDirectory("skillbill-cli-typesafe-enable-supplied")
    val suppliedPath = writeRichConfig(suppliedDir)
    val suppliedContext =
      CliRuntimeContext(environment = mapOf(CONFIG_ENVIRONMENT_KEY to suppliedPath.toString()))

    val supplied =
      runJson(
        listOf(
          "typesafe",
          "configure",
          "--enable",
          "--api-key",
          "supplied-secret",
          "--format",
          "json",
        ),
        suppliedContext,
      )

    assertEquals(true, supplied[SystemOneConfigPayloadKeys.ENABLED])
    assertEquals(true, supplied[SystemOneConfigPayloadKeys.API_KEY_CONFIGURED])
    val suppliedStored = decodeJsonObject(Files.readString(suppliedPath))
    assertEquals(listOf(ExperimentNames.TYPESAFE), suppliedStored[ExperimentConfigPayloadKeys.EXPERIMENTS])
    val suppliedTypesafe = suppliedStored[SystemOneConfigPayloadKeys.ROOT] as Map<*, *>
    assertEquals("supplied-secret", suppliedTypesafe[SystemOneConfigPayloadKeys.API_KEY])
    assertFalse(suppliedTypesafe.containsKey(SystemOneConfigPayloadKeys.ENABLED))

    val storedDir = Files.createTempDirectory("skillbill-cli-typesafe-enable-stored")
    val storedPath = writeRichConfig(storedDir)
    val storedContext =
      CliRuntimeContext(environment = mapOf(CONFIG_ENVIRONMENT_KEY to storedPath.toString()))
    runJson(
      listOf(
        "typesafe",
        "configure",
        "--api-key",
        "stored-secret",
        "--format",
        "json",
      ),
      storedContext,
    )

    val stored =
      runJson(
        listOf("typesafe", "configure", "--enable", "--format", "json"),
        storedContext,
      )

    assertEquals(true, stored[SystemOneConfigPayloadKeys.ENABLED])
    assertEquals(true, stored[SystemOneConfigPayloadKeys.API_KEY_CONFIGURED])
    val storedConfig = decodeJsonObject(Files.readString(storedPath))
    assertEquals(listOf(ExperimentNames.TYPESAFE), storedConfig[ExperimentConfigPayloadKeys.EXPERIMENTS])
    val storedTypesafe = storedConfig[SystemOneConfigPayloadKeys.ROOT] as Map<*, *>
    assertEquals("stored-secret", storedTypesafe[SystemOneConfigPayloadKeys.API_KEY])
  }

  @Test
  fun `status json reports experiment listing without leaking api key`() {
    val tempDir = Files.createTempDirectory("skillbill-cli-typesafe-status")
    val configPath = writeRichConfig(tempDir)
    val secret = "status-secret-value"
    Files.writeString(
      configPath,
      JsonCodec.mapToJsonString(
        richConfigPayload() +
          mapOf(
            ExperimentConfigPayloadKeys.EXPERIMENTS to listOf(ExperimentNames.TYPESAFE),
            SystemOneConfigPayloadKeys.ROOT to mapOf(SystemOneConfigPayloadKeys.API_KEY to secret),
          ),
      ) + "\n",
    )
    val context = CliRuntimeContext(environment = mapOf(CONFIG_ENVIRONMENT_KEY to configPath.toString()))

    val result = CliRuntime.run(listOf("typesafe", "status", "--format", "json"), context)
    assertEquals(0, result.exitCode, result.stdout)
    val payload = decodeJsonObject(result.stdout)
    assertEquals(true, payload[SystemOneConfigPayloadKeys.ENABLED])
    assertEquals(true, payload[SystemOneConfigPayloadKeys.API_KEY_CONFIGURED])
    assertFalse(result.stdout.contains(secret))
    assertFalse(payload.containsKey(SystemOneConfigPayloadKeys.API_KEY))
  }

  @Test
  fun `probe without typesafe experiment makes no http calls`() {
    val tempDir = Files.createTempDirectory("skillbill-cli-typesafe-probe")
    val configPath = writeRichConfig(tempDir)
    val capturedCalls = mutableListOf<String>()
    val context =
      CliRuntimeContext(
        environment = mapOf(CONFIG_ENVIRONMENT_KEY to configPath.toString()),
        requester =
        RemoteTransportPort { _, url, _, _ ->
          capturedCalls += url
          RemoteTransportResponse(statusCode = 200, body = """{"model":"jev-latest","answers":{}}""")
        },
      )

    val result = CliRuntime.run(listOf("typesafe", "probe", "hello", "--format", "json"), context)

    assertNotNull(result.exitCode)
    assertEquals(1, result.exitCode)
    assertEquals(0, capturedCalls.size)
  }

  private fun writeRichConfig(tempDir: Path): Path {
    val configPath = tempDir.resolve("config.json")
    Files.writeString(configPath, JsonCodec.mapToJsonString(richConfigPayload()) + "\n")
    return configPath
  }

  private fun richConfigPayload(): Map<String, Any?> = mapOf(
    "install_id" to "retained-install-id",
    "external_addon_sources" to listOf("/tmp/addons"),
    "execution_matrix" to mapOf("default" to "claude"),
    "telemetry" to mapOf("level" to "anonymous", "proxy_url" to "", "batch_size" to 10),
  )
}
