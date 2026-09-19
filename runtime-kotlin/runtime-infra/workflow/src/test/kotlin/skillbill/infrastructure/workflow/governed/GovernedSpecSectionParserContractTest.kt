package skillbill.infrastructure.workflow.governed
import skillbill.infrastructure.workflow.decomposition.read
import skillbill.infrastructure.workflow.featuretask.FileSystemFeatureTaskRuntimeRunInvariantsSource
import skillbill.infrastructure.workflow.filesystem.path
import skillbill.infrastructure.workflow.git.scoped.read
import skillbill.infrastructure.workflow.git.standard.path
import skillbill.infrastructure.workflow.git.suppression.path
import skillbill.infrastructure.workflow.git.workflow.path
import skillbill.infrastructure.workflow.git.workflow.read
import skillbill.infrastructure.workflow.review.broker.path
import skillbill.infrastructure.workflow.review.specialists.system.path
import skillbill.review.spec.GovernedSpecSectionParser
import skillbill.review.spec.GovernedSpecSectionParser.ACCEPTANCE_CRITERIA_PREFIX
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

class GovernedSpecSectionParserContractTest {
  @Test
  fun `a spec accepted by the invariants reader yields the same ordered acceptance criteria from the shared parser`() {
    val spec = Files.createTempDirectory("governed-spec-parser").resolve("spec.md").also { path ->
      Files.writeString(
        path,
        """
        # Runtime spec

        ## Acceptance Criteria
        1. Tests cover, at minimum:
           - running duplicate protection;
           - repository isolation.
        2. Maintainer validation passes.
        """.trimIndent(),
      )
    }
    val fromReader = FileSystemFeatureTaskRuntimeRunInvariantsSource().read(spec).acceptanceCriteria
    val fromParser = GovernedSpecSectionParser.parseListSection(Files.readString(spec)) {
      it.startsWith(ACCEPTANCE_CRITERIA_PREFIX)
    }
    assertEquals(fromReader, fromParser)
  }
}
