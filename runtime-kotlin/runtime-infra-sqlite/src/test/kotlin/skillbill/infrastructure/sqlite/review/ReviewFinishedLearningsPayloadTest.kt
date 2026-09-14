package skillbill.infrastructure.sqlite.review

import skillbill.contracts.learning.LearningEntryDto
import skillbill.contracts.learning.LearningPayloadKeys
import skillbill.learnings.learningAppliedSessionWire
import kotlin.test.Test
import kotlin.test.assertEquals

class ReviewFinishedLearningsPayloadTest {
  @Test
  fun `learning applied session toPayload preserves review finished learnings field names`() {
    val entry = LearningEntryDto(
      reference = "L-001",
      scope = "repo",
      scopeKey = "skill-bill",
      status = "active",
      title = "Title",
      ruleText = "Rule",
      rationale = "Because",
      sourceReviewRunId = "run-1",
      sourceFindingId = "finding-1",
    )
    val payload = learningAppliedSessionWire("bill-code-review", listOf(entry)).toPayload()
    assertEquals("bill-code-review", payload[LearningPayloadKeys.SKILL_NAME])
    assertEquals(1, payload[LearningPayloadKeys.APPLIED_LEARNING_COUNT])
    assertEquals(listOf("L-001"), payload[LearningPayloadKeys.APPLIED_LEARNING_REFERENCES])
    assertEquals("L-001", payload[LearningPayloadKeys.APPLIED_LEARNINGS])
    val learnings = payload[LearningPayloadKeys.LEARNINGS] as List<*>
    val summary = learnings.single() as Map<*, *>
    assertEquals("L-001", summary[LearningPayloadKeys.REFERENCE])
    assertEquals("repo", summary[LearningPayloadKeys.SCOPE])
    assertEquals("Title", summary[LearningPayloadKeys.TITLE])
    assertEquals("Rule", summary[LearningPayloadKeys.RULE_TEXT])
  }
}
