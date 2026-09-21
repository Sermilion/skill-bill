package skillbill.config.model

import skillbill.contracts.typesafe.SystemOneConfigPayloadKeys
import skillbill.contracts.typesafe.SystemOneDefaults
import skillbill.telemetry.model.TelemetryConfigDocument
import skillbill.telemetry.model.TelemetryOpenDocument
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull

class TypeSafeSettingsModelsTest {
  @Test
  fun `parse on null returns default credential settings`() {
    val parsed = parseTypeSafeSettings(null)
    assertIs<TypeSafeSettingsParse.Valid>(parsed)
    assertNull(parsed.settings.apiKey)
    assertEquals(SystemOneDefaults.BASE_URL, parsed.settings.baseUrl)
    assertEquals(SystemOneDefaults.MODEL, parsed.settings.defaultModel)
  }

  @Test
  fun `unknown typesafe field fails parse`() {
    val parsed =
      parseTypeSafeSettings(
        mapOf(
          SystemOneConfigPayloadKeys.API_KEY to "secret",
          "typo_field" to "oops",
        ),
      )
    assertIs<TypeSafeSettingsParse.Invalid>(parsed)
    assertEquals("${SystemOneConfigPayloadKeys.ROOT}.typo_field", parsed.keyPath)
  }

  @Test
  fun `withTypeSafeSettings preserves unrelated payload keys and strips enabled`() {
    val document =
      TelemetryConfigDocument(
        TelemetryOpenDocument.from(
          mapOf(
            "install_id" to "retained-install-id",
            "external_addon_sources" to listOf("/tmp/addons"),
            "execution_matrix" to mapOf("default" to "claude"),
            "telemetry" to mapOf("level" to "anonymous", "proxy_url" to "", "batch_size" to 10),
            SystemOneConfigPayloadKeys.ROOT to mapOf(SystemOneConfigPayloadKeys.ENABLED to true),
          ),
        ),
      )

    val updated =
      document.withTypeSafeSettings(
        TypeSafeSettingsPatch(apiKey = "stored-key"),
      )

    assertEquals("retained-install-id", updated.payload["install_id"])
    assertEquals(listOf("/tmp/addons"), updated.payload["external_addon_sources"])
    assertEquals(mapOf("default" to "claude"), updated.payload["execution_matrix"])
    val telemetry = updated.payload["telemetry"] as Map<*, *>
    assertEquals("anonymous", telemetry["level"])
    assertEquals("", telemetry["proxy_url"])
    assertEquals(10, telemetry["batch_size"])
    val typesafe = updated.payload[SystemOneConfigPayloadKeys.ROOT] as Map<*, *>
    assertEquals("stored-key", typesafe[SystemOneConfigPayloadKeys.API_KEY])
    assertFalse(typesafe.containsKey(SystemOneConfigPayloadKeys.ENABLED))
  }
}
