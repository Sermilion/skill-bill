package skillbill.infrastructure.launcher.process.launch

import skillbill.error.shellcontent.ExperimentIsolationCapabilityRefusalError
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class AgentRunProcessExperimentCapabilityTest {
  @Test
  fun `request builder preserves experiment capability policy`() {
    val request =
      testAgentRunProcessRequest(listOf("agent"), Path.of(".")) {
        treatmentCapabilitiesEnabled = setOf("fixture-treatment")
        denyRemotePublication = true
      }

    assertEquals(setOf("fixture-treatment"), request.experimentCapabilities.treatmentCapabilitiesEnabled)
    assertTrue(request.experimentCapabilities.denyRemotePublication)
  }

  @Test
  fun `control capability policy refuses treatment command and remote publication`() {
    assertFailsWith<ExperimentIsolationCapabilityRefusalError> {
      requestCapabilities(setOf("fixture-treatment"), denyRemotePublication = true)
        .validate(listOf("fixture-treatment"))
    }
    assertFailsWith<ExperimentIsolationCapabilityRefusalError> {
      requestCapabilities(emptySet(), denyRemotePublication = true)
        .validate(listOf("git", "push", "origin", "HEAD"))
    }
  }

  private fun requestCapabilities(
    denied: Set<String>,
    denyRemotePublication: Boolean,
  ) = AgentRunProcessExperimentCapabilityFields(
    treatmentCapabilitiesDenied = denied,
    denyRemotePublication = denyRemotePublication,
  )
}
