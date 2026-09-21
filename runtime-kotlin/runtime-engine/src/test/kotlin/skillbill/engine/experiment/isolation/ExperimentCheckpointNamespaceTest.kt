package skillbill.engine.experiment.isolation
import skillbill.experiment.model.ExperimentArmId
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals

class ExperimentCheckpointNamespaceTest {
  @Test
  fun `checkpoint namespace isolates pair and arm`() {
    val control = ExperimentCheckpointNamespace.prefix("pair-a", ExperimentArmId.CONTROL)
    val treatment = ExperimentCheckpointNamespace.prefix("pair-a", ExperimentArmId.TREATMENT)
    val otherPair = ExperimentCheckpointNamespace.prefix("pair-b", ExperimentArmId.CONTROL)

    assertEquals("experiment/pair-a/control", control)
    assertEquals("experiment/pair-a/treatment", treatment)
    assertEquals("experiment/pair-b/control", otherPair)
  }

  @Test
  fun `arm worktree repository resolves its checkpoint namespace`() {
    assertEquals(
      "experiment/pair-a/treatment",
      ExperimentCheckpointNamespace.forRepositoryRoot(
        Path.of("/tmp/.skill-bill-experiments/pair-a/treatment"),
      ),
    )
  }
}
