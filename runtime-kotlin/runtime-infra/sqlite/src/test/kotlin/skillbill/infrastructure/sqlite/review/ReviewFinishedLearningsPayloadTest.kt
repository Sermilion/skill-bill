package skillbill.infrastructure.sqlite.review

import skillbill.application.learning.learningAppliedSessionWire
import skillbill.application.learning.learningEntrySessionJson
import skillbill.contracts.learning.LearningEntryDto
import skillbill.contracts.learning.LearningPayloadKeys
import skillbill.learnings.model.LearningEntry
import skillbill.learnings.model.LearningScope
import java.nio.file.Files
import java.nio.file.Path
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

  @Test
  fun `learning session JSON remains byte-identical to the captured baseline`() {
    val entry = LearningEntry(
      id = 1,
      reference = "L-001",
      scope = LearningScope.REPO,
      scopeKey = "skill-bill",
      status = "active",
      title = "Title",
      ruleText = "Rule",
      rationale = "Because",
      sourceReviewRunId = null,
      sourceFindingId = null,
    )
    val actual = learningEntrySessionJson("bill-code-review", listOf(entry)).toByteArray()
    val expected = Files.readAllBytes(
      repositoryRoot().resolve(
        ".feature-specs/done/SKILL-351-runtime-domain-boundaries-and-simplicity/" +
          "baselines/learnings-session.json",
      ),
    )

    assertEquals(expected.toList(), actual.toList())
  }
}

private fun repositoryRoot(): Path {
  var current: Path? = Path.of("").toAbsolutePath().normalize()
  while (current != null) {
    if (Files.isDirectory(current.resolve(".git"))) return current
    current = current.parent
  }
  error("Repository root is not available from the test working directory.")
}
