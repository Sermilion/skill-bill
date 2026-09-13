package skillbill.workflow.taskruntime

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class FeatureTaskRuntimeAuditRemainingAcInterpretationTest {
  @Test
  fun `explicit empty list forms are recognized`() {
    listOf("[]", "  []  ", "```\n[]\n```", "```json\n[]\n```").forEach { text ->
      assertIs<FeatureTaskRuntimeAuditRemainingAcInterpretation.Result.EmptyRemainingList>(
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
          assertIs<FeatureTaskRuntimeAuditRemainingAcInterpretation.Result.RemainingCriteriaText>(
            FeatureTaskRuntimeAuditRemainingAcInterpretation.interpret(text),
          )
          ).text,
      )
    }
  }

  @Test
  fun `whitespace only and missing responses do not complete audit`() {
    assertIs<FeatureTaskRuntimeAuditRemainingAcInterpretation.Result.WhitespaceOnlyFinalResponse>(
      FeatureTaskRuntimeAuditRemainingAcInterpretation.interpret("   "),
    )
    assertIs<FeatureTaskRuntimeAuditRemainingAcInterpretation.Result.MissingFinalResponse>(
      FeatureTaskRuntimeAuditRemainingAcInterpretation.interpret(null),
    )
  }
}
