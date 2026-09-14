package skillbill.workflow.taskruntime

import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeValidateRemainingCriteriaResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class FeatureTaskRuntimeValidateRemainingCriteriaInterpretationTest {
  @Test
  fun `empty json array completes the repair turn`() {
    assertIs<FeatureTaskRuntimeValidateRemainingCriteriaResult.EmptyRemainingList>(
      FeatureTaskRuntimeValidateRemainingCriteriaInterpretation.interpret("[]"),
    )
  }

  @Test
  fun `json array of strings names unfixed criteria`() {
    val result = assertIs<FeatureTaskRuntimeValidateRemainingCriteriaResult.UnfixedCriteria>(
      FeatureTaskRuntimeValidateRemainingCriteriaInterpretation.interpret(
        """["AC-007 missing test", "detekt TooManyFunctions"]""",
      ),
    )
    assertEquals(
      setOf("ac-007 missing test", "detekt toomanyfunctions"),
      FeatureTaskRuntimeValidateRemainingCriteriaInterpretation.normalizedFingerprint(result.items),
    )
  }

  @Test
  fun `duplicate fingerprint detects unchanged unfixed criteria`() {
    val first = FeatureTaskRuntimeValidateRemainingCriteriaInterpretation.interpret("- AC-007\n")
    val second = FeatureTaskRuntimeValidateRemainingCriteriaInterpretation.interpret("* AC-007")
    val firstItems = assertIs<FeatureTaskRuntimeValidateRemainingCriteriaResult.UnfixedCriteria>(first).items
    val secondItems = assertIs<FeatureTaskRuntimeValidateRemainingCriteriaResult.UnfixedCriteria>(second).items
    assertEquals(
      FeatureTaskRuntimeValidateRemainingCriteriaInterpretation.normalizedFingerprint(firstItems),
      FeatureTaskRuntimeValidateRemainingCriteriaInterpretation.normalizedFingerprint(secondItems),
    )
  }
}
