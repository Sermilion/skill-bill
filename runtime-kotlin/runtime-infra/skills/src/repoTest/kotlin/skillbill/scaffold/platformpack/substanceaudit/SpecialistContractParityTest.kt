
package skillbill.scaffold.platformpack.substanceaudit

import skillbill.infrastructure.skills.scaffold.runtime.validation.RepoValidationRuntime
import skillbill.testing.repoRootFromTest
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

class SpecialistContractParityTest {
  @Test
  fun `delegated specialist subset exactly matches canonical sections`() {
    val root = repoRootFromTest()
    val canonical = Files.readString(root.resolve("orchestration/review-orchestrator/PLAYBOOK.md"))
    val specialist = Files.readString(root.resolve("orchestration/review-orchestrator/specialist-contract.md"))
    listOf("Shared Contract For Every Specialist", "Shared Report Structure").forEach { heading ->
      assertEquals(
        RepoValidationRuntime.extractSecondLevelHeading(canonical, heading),
        RepoValidationRuntime.extractSecondLevelHeading(specialist, heading),
      )
    }
  }
}
