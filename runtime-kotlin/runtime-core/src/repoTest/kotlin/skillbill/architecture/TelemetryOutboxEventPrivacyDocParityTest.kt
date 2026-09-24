package skillbill.architecture

import skillbill.contracts.telemetry.TelemetryOutboxEvent
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

class TelemetryOutboxEventPrivacyDocParityTest {
  @Test
  fun `every registered outbox event has its own section in the privacy doc`() {
    val headings =
      Files.readAllLines(runtimeArchitectureRoot.resolve("docs/telemetry-privacy.md"))
        .filter { line -> line.startsWith("### ") }

    assertEquals(
      emptyList(),
      undocumentedEvents(headings),
      "Every event the runtime can upload needs a docs/telemetry-privacy.md section stating what it carries.",
    )
  }

  @Test
  fun `an event whose only mention is inside a longer event name counts as undocumented`() {
    val headings = TelemetryOutboxEvent.entries.map { event -> "### `${event.wireValue}`" }
    val withoutLegacyRegenerated =
      headings.filterNot { heading ->
        TelemetryOutboxEvent.REVIEW_FINISHED_LEGACY_REGENERATED.wireValue in heading
      }

    assertEquals(
      listOf(TelemetryOutboxEvent.REVIEW_FINISHED_LEGACY_REGENERATED.wireValue),
      undocumentedEvents(withoutLegacyRegenerated),
      "The `skillbill_review_finished` section must not stand in for the event whose name extends it.",
    )
  }

  private fun undocumentedEvents(headings: List<String>): List<String> =
    TelemetryOutboxEvent.entries
      .filter { event -> headings.none { heading -> "`${event.wireValue}`" in heading } }
      .map(TelemetryOutboxEvent::wireValue)
}
