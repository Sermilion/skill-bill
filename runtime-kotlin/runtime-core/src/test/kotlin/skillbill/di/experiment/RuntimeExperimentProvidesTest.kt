package skillbill.di.experiment

import skillbill.error.shellcontent.ExperimentIsolationCapabilityRefusalError
import skillbill.ports.experiment.navigation.ExperimentNavigationSessionRequest
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class RuntimeExperimentProvidesTest {
  @Test
  fun `production measurement binding is absent until a provider is configured`() {
    assertNull(object : RuntimeExperimentGoalProvides {}.experimentArmMeasurementPort())
  }

  @Test
  fun `production navigation binding refuses without a live model provider`() {
    val runner = object : RuntimeExperimentTelemetryProvides {}.experimentNavigationSessionRunner()

    assertFailsWith<ExperimentIsolationCapabilityRefusalError> {
      runner.runSession(
        ExperimentNavigationSessionRequest(
          pairId = "pair",
          armId = "control",
          repoRoot = Path.of("."),
          frozenSpecBytes = "spec".encodeToByteArray(),
          acceptanceCriteria = listOf("criterion"),
          treatmentEnabled = false,
        ),
      )
    }
  }
}
