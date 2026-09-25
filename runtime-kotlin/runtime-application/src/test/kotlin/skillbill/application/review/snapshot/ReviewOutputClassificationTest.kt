package skillbill.application.review.snapshot

import skillbill.application.review.service.ReviewOutputAdmission
import skillbill.application.review.service.classifyReviewOutput
import skillbill.install.model.SupportedAgent
import skillbill.ports.agentrun.agentRunLaunchFacts
import skillbill.ports.agentrun.model.AgentRunTermination
import skillbill.ports.review.model.ReviewProcessOutcome
import kotlin.test.Test
import kotlin.test.assertEquals

class ReviewOutputClassificationTest {
  @Test fun `only a normal zero-exit envelope can be admitted or repaired`() {
    val valid =
      classifyReviewOutput(
        facts(stdout = "NO_FINDINGS"),
        resultEnvelopeValid = true,
      )
    assertEquals(ReviewProcessOutcome.ZERO_EXIT, valid.processOutcome)
    assertEquals(ReviewOutputAdmission.SUCCESS, valid.admission)

    val repairable =
      classifyReviewOutput(
        facts(stdout = "not-an-envelope"),
        resultEnvelopeValid = false,
      )
    assertEquals(ReviewOutputAdmission.SCHEMA_REPAIR_ELIGIBLE, repairable.admission)
  }

  @Test fun `empty zero-exit output is a missing result and is not repairable`() {
    val classification = classifyReviewOutput(facts(), resultEnvelopeValid = false)
    assertEquals(ReviewOutputAdmission.REJECTED, classification.admission)
  }

  @Test fun `process and lifecycle failures are never admitted through schema repair`() {
    listOf(
      facts(AgentRunTermination.TimedOut),
      facts(AgentRunTermination.Interrupted),
      facts(AgentRunTermination.Exited(7)),
      facts(AgentRunTermination.SpawnFailed),
      facts(stdoutTruncated = true),
    ).forEach { launchFacts ->
      val classification = classifyReviewOutput(launchFacts, resultEnvelopeValid = false)
      assertEquals(ReviewOutputAdmission.REJECTED, classification.admission)
      require(classification.processOutcome != ReviewProcessOutcome.ZERO_EXIT)
    }
  }

  private fun facts(
    termination: AgentRunTermination = AgentRunTermination.Exited(0),
    stdout: String = "",
    stdoutTruncated: Boolean = false,
  ) = agentRunLaunchFacts(
    agent = SupportedAgent.CODEX,
    termination = termination,
    stdout = stdout,
    stdoutTruncated = stdoutTruncated,
  )
}
