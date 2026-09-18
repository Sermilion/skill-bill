package skillbill.infrastructure.fs.scaffold

import skillbill.contracts.JsonCodec
import skillbill.infrastructure.fs.scaffold.platformpack.anchoredTopLevelFieldNames
import skillbill.infrastructure.fs.scaffold.platformpack.loadPlatformManifest
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PlatformPackCustomFieldsRoundTripTest {

  @Test
  fun `non-anchored top-level fields surface verbatim through customFields`() {
    val slug = "scenarioslug"
    val manifest = """
      platform: $slug
      contract_version: "1.8"
      routing_signals:
        strong: [".kt"]
      declared_code_review_areas: []
      custom_thing:
        a: 1
        b:
          - "x"
          - "y"
      another_custom: "hello"
    """.trimIndent()

    val packRoot = newTempPackRoot(slug, manifest)
    val pack = loadPlatformManifest(packRoot)

    assertTrue(
      "custom_thing" in pack.customFields,
      "Non-anchored top-level field 'custom_thing' is missing from PlatformManifest.customFields. " +
        "Keys present: ${pack.customFields.keys}",
    )
    assertTrue(
      "another_custom" in pack.customFields,
      "Non-anchored top-level field 'another_custom' is missing from PlatformManifest.customFields. " +
        "Keys present: ${pack.customFields.keys}",
    )
    assertEquals("hello", pack.customFields["another_custom"])
    val nested = requireNotNull(JsonCodec.anyToStringAnyMap(pack.customFields["custom_thing"])) {
      "Expected 'custom_thing' to deserialize to a Map but got ${pack.customFields["custom_thing"]}"
    }
    assertEquals(1, nested["a"])
    assertEquals(listOf("x", "y"), nested["b"])

    val anchored = anchoredTopLevelFieldNames()
    val leakedAnchored = pack.customFields.keys.intersect(anchored)
    assertTrue(
      leakedAnchored.isEmpty(),
      "Anchored top-level fields leaked into customFields: $leakedAnchored. " +
        "ShellContentLoader.buildPack must filter the anchored set out.",
    )
  }

  @Test
  fun `pack with no custom fields produces empty customFields map`() {
    val slug = "scenarioslug"
    val manifest = """
      platform: $slug
      contract_version: "1.8"
      routing_signals:
        strong: [".kt"]
      declared_code_review_areas: []
    """.trimIndent()

    val packRoot = newTempPackRoot(slug, manifest)
    val pack = loadPlatformManifest(packRoot)

    assertTrue(
      pack.customFields.isEmpty(),
      "Manifest with no fork-specific keys must produce empty customFields; got ${pack.customFields.keys}.",
    )

    val anchored = anchoredTopLevelFieldNames()
    assertFalse(pack.customFields.keys.any { it in anchored })
  }

  @Test
  fun `composition is typed and excluded from customFields`() {
    val slug = "kmp"
    val manifest = """
      platform: $slug
      contract_version: "1.8"
      routing_signals:
        strong: [".kt"]
      declared_code_review_areas: []
      declared_files:
        baseline: code-review/bill-kmp-code-review/content.md
      code_review_composition:
        baseline_layers:
          - platform: kotlin
            skill: bill-kotlin-code-review
            scope: same-review-scope
            required: true
            mode: kmp-baseline
      custom_thing: "hello"
    """.trimIndent()

    val packRoot = newTempPackRoot(slug, manifest)
    val pack = loadPlatformManifest(packRoot)

    val composition = assertNotNull(pack.codeReviewComposition)
    assertEquals("kotlin", composition.baselineLayers.single().platform)
    assertEquals("hello", pack.customFields["custom_thing"])
    assertFalse(
      "code_review_composition" in pack.customFields,
      "Anchored composition field must stay typed and never flow through customFields.",
    )
  }

  private fun newTempPackRoot(slug: String, manifest: String): Path {
    val tempDir = Files.createTempDirectory("skillbill-platform-pack-customfields-test-")
    val packRoot = tempDir.resolve(slug)
    Files.createDirectories(packRoot)
    Files.writeString(packRoot.resolve("platform.yaml"), manifest)
    return packRoot
  }
}
