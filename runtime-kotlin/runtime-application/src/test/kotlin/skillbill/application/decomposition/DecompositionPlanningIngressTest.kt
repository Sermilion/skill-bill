package skillbill.application.decomposition

import skillbill.contracts.decomposition.DecompositionPlanningResult
import skillbill.error.InvalidDecompositionManifestSchemaError
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFailsWith

class DecompositionPlanningIngressTest {
  @Test
  fun `typed planning ingress rejects empty subtasks`() {
    val error = assertFailsWith<InvalidDecompositionManifestSchemaError> {
      parseSubtasks(
        DecompositionPlanningResult(
          mode = "decompose",
          subtasks = emptyList(),
        ),
        "typed-planning-result",
      )
    }
    assertContains(error.reason, "at least one subtask")
  }
}
