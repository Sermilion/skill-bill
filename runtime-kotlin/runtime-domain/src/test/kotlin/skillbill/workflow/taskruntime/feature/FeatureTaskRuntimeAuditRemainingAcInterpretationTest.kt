package skillbill.workflow.taskruntime.feature

import skillbill.workflow.taskruntime.model.audit.FeatureTaskRuntimeAuditRemainingAcResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class FeatureTaskRuntimeAuditRemainingAcInterpretationTest {
  @Test
  fun `explicit empty list forms are recognized`() {
    listOf("[]", "  []  ", "```\n[]\n```", "```json\n[]\n```").forEach { text ->
      assertIs<FeatureTaskRuntimeAuditRemainingAcResult.EmptyRemainingList>(
        FeatureTaskRuntimeAuditRemainingAcInterpretation.interpret(text),
      )
    }
  }

  @Test
  fun `nonempty explanations with incidental brackets stay nonempty`() {
    listOf(
      "- AC-002 still open",
      "1. AC-002 still open",
      "AC-002 still open because [implementation gap]",
    ).forEach { text ->
      assertEquals(
        text,
        (
          assertIs<FeatureTaskRuntimeAuditRemainingAcResult.RemainingCriteriaText>(
            FeatureTaskRuntimeAuditRemainingAcInterpretation.interpret(text),
          )
        ).text,
      )
    }
  }

  @Test
  fun `whitespace only and missing responses do not complete audit`() {
    assertIs<FeatureTaskRuntimeAuditRemainingAcResult.WhitespaceOnlyFinalResponse>(
      FeatureTaskRuntimeAuditRemainingAcInterpretation.interpret("   "),
    )
    assertIs<FeatureTaskRuntimeAuditRemainingAcResult.MissingFinalResponse>(
      FeatureTaskRuntimeAuditRemainingAcInterpretation.interpret(null),
    )
  }
}
