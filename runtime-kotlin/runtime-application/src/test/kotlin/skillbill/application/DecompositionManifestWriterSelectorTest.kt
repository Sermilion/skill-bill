package skillbill.application

import skillbill.application.decomposition.model.DecompositionManifestWriteRequest
import skillbill.contracts.decomposition.DecompositionPlanningResult
import skillbill.error.InvalidDecompositionManifestSchemaError
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFailsWith

class DecompositionManifestWriterSelectorTest {
  @Test
  fun `decomposition planning rejects present invalid selector ids`() {
    val repoRoot = Files.createTempDirectory("skillbill-decomposition-selector-int")
    val parentSpecPath = repoRoot.resolve(".feature-specs/SKILL-51-decomposition/spec.md")
    Files.createDirectories(parentSpecPath.parent)
    Files.writeString(parentSpecPath, "# Parent spec\n")

    val invalidCurrent = assertFailsWith<InvalidDecompositionManifestSchemaError> {
      DecompositionPlanningResult.fromWireMap(
        decompositionPlanningPlan(parentSpecPath).toPayload().toMutableMap().apply {
          put("current_subtask_id", 1.5)
        },
        parentSpecPath.toString(),
      )
    }
    val invalidRecommended = assertFailsWith<InvalidDecompositionManifestSchemaError> {
      DecompositionPlanningResult.fromWireMap(
        decompositionPlanningPlan(parentSpecPath).toPayload().toMutableMap().apply {
          put("recommended_first_subtask_id", 1.5)
        },
        parentSpecPath.toString(),
      )
    }

    assertContains(invalidCurrent.reason, "current_subtask_id must be an integer")
    assertContains(invalidRecommended.reason, "recommended_first_subtask_id must be an integer")
  }
}
