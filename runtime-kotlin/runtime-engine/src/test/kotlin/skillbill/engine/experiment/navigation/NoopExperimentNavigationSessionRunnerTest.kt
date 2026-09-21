package skillbill.engine.experiment.navigation

import skillbill.error.shellcontent.ExperimentIsolationCapabilityRefusalError
import skillbill.ports.experiment.navigation.ExperimentNavigationSessionRequest
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertFailsWith

class NoopExperimentNavigationSessionRunnerTest {
  @Test
  fun `unconfigured navigation runner refuses instead of reporting search completion`() {
    assertFailsWith<ExperimentIsolationCapabilityRefusalError> {
      NoopExperimentNavigationSessionRunner().runSession(
        ExperimentNavigationSessionRequest(
          pairId = "pair",
          armId = "control",
          repoRoot = Path.of("."),
          frozenSpecBytes = "spec".toByteArray(),
          acceptanceCriteria = listOf("criterion"),
          treatmentEnabled = false,
        ),
      )
    }
  }
}
