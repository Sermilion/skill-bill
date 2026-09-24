package skillbill.engine.featuretask.runloop.core

import skillbill.application.decomposition.decompositionManifestPath
import skillbill.application.decomposition.parentSpecPath
import skillbill.engine.featuretask.lifecycle.branch.Blocked
import skillbill.engine.featuretask.model.core.FeatureTaskRuntimeRunReport
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertNull

class FeatureTaskRuntimeRunLoopSessionIsolationTest {
  @Test
  fun `run loop helpers do not accept the coordinator or an all access bundle`() {
    val sourceRoot =
      listOf(
        Path.of("runtime-kotlin/runtime-engine/src/main/kotlin/skillbill/engine/featuretask"),
        Path.of("runtime-engine/src/main/kotlin/skillbill/engine/featuretask"),
        Path.of("src/main/kotlin/skillbill/engine/featuretask"),
      ).first(Files::isDirectory)
    val source =
      Files.walk(sourceRoot).use { paths ->
        paths
          .filter { path -> path.toString().endsWith(".kt") }
          .map(Path::toFile)
          .toList()
          .joinToString("\n") { file -> file.readText() }
      }

    val persistencePortsName = listOf("FeatureTaskRuntimeRunLoop", "PersistencePorts").joinToString("")
    assertFalse(source.contains(persistencePortsName))
  }

  @Test
  fun `run loop sessions do not share per-run mutable flags`() {
    val sessionOne =
      FeatureTaskRuntimeRunLoopSession(
        operatorBlockRetry = null,
        initialPendingReentry = null,
      )
    val sessionTwo =
      FeatureTaskRuntimeRunLoopSession(
        operatorBlockRetry = null,
        initialPendingReentry = null,
      )
    sessionOne.transitionResolvedBranch("feature-branch")
    sessionOne.consumeOperatorBlockRetryCompletion("implement")
    val identities = mutableMapOf("src/Foo.kt" to "abc")
    sessionOne.recordPhaseContentIdentities("implement", identities)
    identities["src/Bar.kt"] = "def"
    assertNull(sessionTwo.resolvedBranch)
    assertEquals(false, sessionTwo.operatorBlockRetryCompleted)
    assertEquals(emptyMap(), sessionTwo.phaseContentIdentitiesFor("implement"))
    assertEquals(mapOf("src/Foo.kt" to "abc"), sessionOne.phaseContentIdentitiesFor("implement"))
    assertNotSame(sessionOne, sessionTwo)
  }

  @Test
  fun `terminal transition replaces prior terminal report`() {
    val session = FeatureTaskRuntimeRunLoopSession(operatorBlockRetry = null, initialPendingReentry = null)
    session.transitionToBlocked(
      FeatureTaskRuntimeRunReport.Blocked(
        issueKey = "SKILL-1",
        workflowId = "wf-1",
        featureSize = "MEDIUM",
        lastIncompletePhase = "implement",
        blockedReason = "first",
        completedPhaseIds = emptyList(),
        resolvedBranch = null,
      ),
    )
    session.transitionToPaused(
      FeatureTaskRuntimeRunReport.Paused(
        issueKey = "SKILL-1",
        workflowId = "wf-1",
        featureSize = "MEDIUM",
        pausedPhase = "implement",
        pauseReason = "second",
        resumableStep = "implement",
        completedPhaseIds = emptyList(),
        resolvedBranch = null,
      ),
    )
    assertNull(session.blocked)
    assertEquals("second", session.paused?.pauseReason)
    session.transitionToDecomposed(
      FeatureTaskRuntimeRunReport.Decomposed(
        issueKey = "SKILL-1",
        workflowId = "wf-1",
        featureSize = "MEDIUM",
        reason = "third",
        completedPhaseIds = emptyList(),
        parentSpecPath = "spec.md",
        decompositionManifestPath = "manifest.yaml",
        subtaskSpecPaths = listOf("subtask.md"),
        resolvedBranch = null,
      ),
    )
    assertNull(session.blocked)
    assertNull(session.paused)
    assertEquals("third", session.decomposed?.reason)
  }
}
