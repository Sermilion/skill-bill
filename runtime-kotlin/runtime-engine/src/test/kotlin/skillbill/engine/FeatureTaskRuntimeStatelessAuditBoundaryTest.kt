package skillbill.engine

import skillbill.application.realFeatureTaskRuntimePhaseOutputValidator
import skillbill.engine.featuretask.model.core.FeatureTaskRuntimeRunReport
import skillbill.ports.agentrun.model.AgentRunLaunchFacts
import skillbill.workflow.taskruntime.artifact.asWorkflowArtifactEntry
import skillbill.workflow.taskruntime.artifact.toWorkflowArtifactMap
import skillbill.workflow.taskruntime.model.persistence.task.runtime.persistence.FEATURE_TASK_RUNTIME_DELIVERED_PROJECTIONS_ARTIFACT_KEY
import skillbill.workflow.taskruntime.model.persistence.task.runtime.persistence.FEATURE_TASK_RUNTIME_PHASE_BRIEFINGS_ARTIFACT_KEY
import skillbill.workflow.taskruntime.model.persistence.task.runtime.run.FEATURE_TASK_RUNTIME_RUN_INVARIANTS_ARTIFACT_KEY
import skillbill.workflow.taskruntime.model.persistence.task.runtime.run.FeatureTaskRuntimeRunInvariantPromptField
import skillbill.workflow.taskruntime.model.phase.FeatureTaskRuntimeFailureDisposition
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
class FeatureTaskRuntimeStatelessAuditBoundaryTest {
  @Test
  fun `one audit session repairs production and missing assertions before review can start`() {
    val root = Files.createTempDirectory("stateless-audit-repair")
    try {
      Files.writeString(root.resolve("Calculator.kt"), "fun twice(value: Int) = value")
      Files.writeString(root.resolve("CalculatorTest.kt"), "fun twiceTest() {}")
      val checked = mutableListOf<String>()
      val launcher = RuntimeRecordingLauncher { request ->
        val prompt = requireNotNull(request.skillRunRequest.promptOverride)
        if (phaseIdFromPrompt(prompt) != "audit") return@RuntimeRecordingLauncher facts(defaultPhaseOutput(request))
        assertFalse(request.skillRunRequest.readOnlyPhase)
        assertAuditInstructions(prompt)
        CRITERIA.forEach { criterion ->
          assertContains(prompt, criterion)
          checked += criterion
        }
        assertEquals("fun twice(value: Int) = value", Files.readString(root.resolve("Calculator.kt")))
        assertEquals("fun twiceTest() {}", Files.readString(root.resolve("CalculatorTest.kt")))
        Files.writeString(root.resolve("Calculator.kt"), IMPLEMENTATION)
        Files.writeString(root.resolve("CalculatorTest.kt"), TEST_SOURCE)
        inspectBothCriteria(root, checked)
        facts(auditSatisfiedOutput())
      }
      val harness = runnerHarness(
        RuntimeHarnessConfig(
          repoRoot = root,
          acceptanceCriteria = CRITERIA,
          launcher = launcher,
          validator = realFeatureTaskRuntimePhaseOutputValidator,
        ),
      )
      val report = harness.runner.run(harness.request())
      assertIs<FeatureTaskRuntimeRunReport.Completed>(report)
      assertEquals(CRITERIA + CRITERIA, checked)
      assertEquals(1, harness.launchedPromptPhaseOrder().count { it == "audit" })
      assertEquals(1, harness.launchedPromptPhaseOrder().count { it == "implement" })
      assertTrue("implement_fix" !in harness.launchedPromptPhaseOrder())
      assertTrue(harness.launchOrder().indexOf("review") > harness.launchOrder().indexOf("audit"))
      val laterPrompts = launcher.requests.dropWhile {
        phaseIdFromPrompt(requireNotNull(it.skillRunRequest.promptOverride)) != "audit"
      }.drop(1).map { requireNotNull(it.skillRunRequest.promptOverride) }
      assertTrue(laterPrompts.none { it.contains("from: audit") || it.contains("audit_prose") })
      assertEquals(IMPLEMENTATION, Files.readString(root.resolve("Calculator.kt")))
      assertEquals(TEST_SOURCE, Files.readString(root.resolve("CalculatorTest.kt")))
      val artifacts = harness.repository.taskRuntimeArtifacts(WORKFLOW_ID)
      val briefings = artifacts[FEATURE_TASK_RUNTIME_PHASE_BRIEFINGS_ARTIFACT_KEY] as Map<*, *>
      val projections = artifacts[FEATURE_TASK_RUNTIME_DELIVERED_PROJECTIONS_ARTIFACT_KEY] as Map<*, *>
      assertFalse(briefings.containsKey("audit"))
      assertTrue(projections.keys.none { it.toString().split('|').getOrNull(1) == "audit" })
    } finally {
      root.toFile().deleteRecursively()
    }
  }

  @Test
  fun `process failure preserves edits and ordinary resume checks the full frozen list in a fresh launch`() {
    val root = Files.createTempDirectory("stateless-audit-restart")
    try {
      Files.writeString(root.resolve("Calculator.kt"), IMPLEMENTATION)
      Files.writeString(root.resolve("CalculatorTest.kt"), "fun twiceTest() {}")
      val checked = mutableListOf<String>()
      var auditLaunches = 0
      val launcher = RuntimeRecordingLauncher { request ->
        val prompt = requireNotNull(request.skillRunRequest.promptOverride)
        if (phaseIdFromPrompt(prompt) != "audit") return@RuntimeRecordingLauncher facts(defaultPhaseOutput(request))
        auditLaunches += 1
        CRITERIA.forEach { assertContains(prompt, it) }
        assertAuditInstructions(prompt)
        assertFalse(prompt.contains("PRIVATE-PARTIAL-AUDIT"))
        if (auditLaunches == 1) {
          assertEquals(IMPLEMENTATION, Files.readString(root.resolve("Calculator.kt")))
          checked += CRITERIA.first()
          Files.writeString(root.resolve("CalculatorTest.kt"), TEST_SOURCE)
          (facts("PRIVATE-PARTIAL-AUDIT") as AgentRunLaunchFacts).copy(
            exitStatus = 1,
            stderr = "agent process interrupted after writing the test",
          )
        } else {
          inspectBothCriteria(root, checked)
          facts(auditSatisfiedOutput())
        }
      }
      val harness = runnerHarness(
        RuntimeHarnessConfig(
          repoRoot = root,
          acceptanceCriteria = CRITERIA,
          launcher = launcher,
          validator = realFeatureTaskRuntimePhaseOutputValidator,
        ),
      )
      val interrupted = harness.runner.run(harness.request())
      assertIs<FeatureTaskRuntimeRunReport.Blocked>(interrupted)
      assertEquals("audit", interrupted.lastIncompletePhase)
      assertEquals(1, auditLaunches)
      assertTrue("review" !in harness.launchOrder())
      assertEquals(TEST_SOURCE, Files.readString(root.resolve("CalculatorTest.kt")))
      assertEquals(
        FeatureTaskRuntimeFailureDisposition.PROCESS_FAILURE,
        harness.recorder.loadPhaseRecords(WORKFLOW_ID)?.get("audit")?.failureDisposition,
      )
      val restarted = harness.runner.run(
        harness.request().copy(
          runInvariants = harness.request().runInvariants.copy(acceptanceCriteria = listOf(CRITERIA.last())),
        ),
      )
      assertIs<FeatureTaskRuntimeRunReport.Completed>(restarted)
      assertEquals(2, auditLaunches)
      assertEquals(listOf(CRITERIA.first()) + CRITERIA, checked)
      assertEquals(1, harness.launchedPromptPhaseOrder().count { it == "implement" })
    } finally {
      root.toFile().deleteRecursively()
    }
  }

  @Test
  fun `external repair dependency blocks without launching repair or downstream agents`() {
    val reason = "Private SDK is unavailable and its API is required to repair AC-002."
    val launcher = RuntimeRecordingLauncher { request ->
      val phase = phaseIdFromPrompt(requireNotNull(request.skillRunRequest.promptOverride))
      facts(if (phase == "audit") auditBlockedOutput(reason) else defaultPhaseOutput(request))
    }
    val harness = runnerHarness(
      RuntimeHarnessConfig(launcher = launcher, validator = realFeatureTaskRuntimePhaseOutputValidator),
    )
    val result = harness.runner.run(harness.request())
    assertIs<FeatureTaskRuntimeRunReport.Blocked>(result)
    assertEquals("audit", result.lastIncompletePhase)
    assertContains(result.blockedReason, reason)
    val record = requireNotNull(harness.recorder.loadPhaseRecords(WORKFLOW_ID)?.get("audit"))
    assertEquals(FeatureTaskRuntimeFailureDisposition.NEEDS_USER_ACTION, record.failureDisposition)
    assertTrue("audit" !in result.completedPhaseIds)
    assertEquals(listOf("preplan", "plan", "implement", "audit"), harness.launchedPromptPhaseOrder())
  }

  @Test
  fun `missing or malformed planned criteria block before any agent can claim audit completion`() {
    val field = FeatureTaskRuntimeRunInvariantPromptField.ACCEPTANCE_CRITERIA.wireValue
    listOf(null, "not a criterion list", emptyList<String>(), listOf(" ")).forEach { invalid ->
      val harness = runnerHarness(RuntimeHarnessConfig(launcher = satisfiedAuditLauncher()))
      harness.seedPhase("preplan", "completed", 1, "claude", PREPLAN_OUTPUT)
      harness.seedPhase("plan", "completed", 1, "claude", PLAN_OUTPUT)
      harness.seedPhase("implement", "completed", 1, "claude", IMPLEMENT_OUTPUT)
      val invariants = harness.request().runInvariants.asWorkflowArtifactEntry().toWorkflowArtifactMap().toMutableMap()
      if (invalid == null) invariants.remove(field) else invariants[field] = invalid
      harness.repository.replaceTaskRuntimeArtifacts(
        WORKFLOW_ID,
        harness.repository.taskRuntimeArtifacts(WORKFLOW_ID) +
          (FEATURE_TASK_RUNTIME_RUN_INVARIANTS_ARTIFACT_KEY to invariants),
      )
      val result = harness.runner.run(harness.request())
      assertIs<FeatureTaskRuntimeRunReport.Blocked>(result)
      assertContains(result.blockedReason, "Planning inputs are unreadable")
      assertEquals("audit", result.lastIncompletePhase)
      assertContains(result.blockedReason.lowercase(), "acceptance")
      assertTrue(harness.launcher.requests.isEmpty())
      assertTrue("audit" !in result.completedPhaseIds)
    }
  }

  private fun assertAuditInstructions(prompt: String) {
    assertContains(prompt, "Repair every fixable gap in this same agent session")
    assertContains(prompt, "re-check the entire in-scope criterion list from the beginning")
    assertContains(prompt, "complete in-scope criterion set from scratch")
    assertContains(prompt, "mock-only interaction, or tautological assertion is not coverage")
    assertContains(prompt, "Do not spawn subagents, invoke repair skills, or hand findings")
    assertContains(prompt, "never run a build, a test")
  }

  private fun inspectBothCriteria(root: Path, checked: MutableList<String>) {
    assertContains(Files.readString(root.resolve("Calculator.kt")), "value * 2")
    checked += CRITERIA.first()
    assertContains(Files.readString(root.resolve("CalculatorTest.kt")), "assertEquals(6, twice(3))")
    checked += CRITERIA.last()
  }

  private companion object {
    val CRITERIA = listOf(
      "AC-007. twice returns twice its input.",
      "AC-023. A test asserts that twice(3) returns 6.",
    )
    const val IMPLEMENTATION = "fun twice(value: Int) = value * 2"
    val TEST_SOURCE = """
      import kotlin.test.Test
      import kotlin.test.assertEquals

      class CalculatorTest {
        @Test
        fun twiceTest() { assertEquals(6, twice(3)) }
      }
    """.trimIndent()
  }
}
