package skillbill.application.review.review
import skillbill.application.review.packet.entries
import skillbill.application.review.packet.first
import skillbill.application.review.parallel.core.code.review.bundled.review
import skillbill.application.review.parallel.core.code.review.claim.first
import skillbill.application.review.parallel.core.code.review.end.first
import skillbill.application.review.parallel.core.code.review.evidence.entries
import skillbill.application.review.parallel.core.code.review.spec.first
import skillbill.application.review.parallel.core.review.error
import skillbill.application.review.parallel.core.review.first
import skillbill.application.review.parallel.core.review.parent
import skillbill.application.review.parallel.verification.error
import skillbill.application.review.preparation.error
import skillbill.application.review.preparation.first
import skillbill.application.review.service.error
import skillbill.application.review.service.review
import skillbill.application.review.spec.error
import skillbill.application.review.spec.resolve
import skillbill.application.review.stats.parent
import skillbill.review.context.model.launch.ReviewIntegrationTerminalOutcome
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals

class ReviewIntegrationTerminalOutcomeTest {

  @Test fun `integration terminal states match the governed schema enum`() {
    val schema = Files.readString(findRepositoryFile("orchestration/contracts/review-context-schema.yaml"))
    val enumLine = schema.lines()
      .dropWhile { !it.contains("integration_accounting:") }
      .first { it.trimStart().startsWith("enum: [") }
    val governed = enumLine.substringAfter("[").substringBefore("]").split(",").map { it.trim() }.toSet()

    assertEquals(governed, ReviewIntegrationTerminalOutcome.entries.map { it.wireValue }.toSet())
  }

  @Test fun `only a settled integration pass is a durable boundary`() {
    val durable = ReviewIntegrationTerminalOutcome.entries.filter { it.isDurablyComplete }.toSet()

    assertEquals(
      setOf(
        ReviewIntegrationTerminalOutcome.COMPLETED,
        ReviewIntegrationTerminalOutcome.SKIPPED_NOT_APPLICABLE,
        ReviewIntegrationTerminalOutcome.NO_OP_RESUME,
      ),
      durable,
      "A crashed, timed-out, or interrupted integration pass must be re-run by the next resume.",
    )
  }

  private fun findRepositoryFile(relative: String): Path {
    var current: Path? = Path.of("").toAbsolutePath().normalize()
    while (current != null) {
      val candidate = current.resolve(relative)
      if (Files.isRegularFile(candidate)) return candidate
      current = current.parent
    }
    error("Repository file '$relative' not found.")
  }
}
