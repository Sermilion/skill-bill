package skillbill.engine.featuretask.validation

import skillbill.contracts.workflow.FEATURE_TASK_RUNTIME_PERSISTENCE_CONTRACT_VERSION
import skillbill.engine.featuretask.validation.model.ValidationGateAgentRepairLauncher
import skillbill.engine.featuretask.validation.model.ValidationGateAgentTriageLauncher
import skillbill.engine.featuretask.validation.model.ValidationGateCycleRequest
import skillbill.engine.featuretask.validation.model.ValidationGateCycleResult
import skillbill.engine.featuretask.validation.model.ValidationGateCycleTerminalOutcome
import skillbill.engine.featuretask.validation.model.ValidationGateResolution
import skillbill.engine.featuretask.validation.model.ValidationGateTriageResult
import skillbill.ports.validation.model.ValidationGateFinding
import skillbill.workflow.goal.model.ValidationDepth
import skillbill.workflow.taskruntime.decodeValidationGateProgressFromArtifact
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeValidationGateProgress
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeValidationGateRepairWindowPhase
import skillbill.workflow.taskruntime.model.ValidationGateCacheMode
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class FeatureTaskRuntimeValidationGateTest {
  @Test
  fun `unparseable gate after agent still retries agent without injected findings`() {
    val progress = mutableListOf<FeatureTaskRuntimeValidationGateProgress>()
    val triageLaunches = AtomicInteger(0)
    val repairLaunches = AtomicInteger(0)
    val runner = ScriptedGateRunner(
      listOf(failedEmptyFindings("Execution failed for task :spotlessCheck."), passed(forced = true)),
    )
    val cycle = coordinator(declaredResolver(), runner, progress).execute(
      cycle = ValidationGateCycleRequest(
        repoRoot = validationGateTestRepoRoot,
        request = minimalRequest(),
        validationDepth = ValidationDepth.DEFAULT,
        changedPaths = listOf("runtime-kotlin/foo.kt"),
        repositoryCheckpoint = "checkpoint",
        agentTriageLauncher = ValidationGateAgentTriageLauncher { _ ->
          triageLaunches.incrementAndGet()
          ValidationGateTriageResult.Captured("should not run")
        },
        agentRepairLauncher = ValidationGateAgentRepairLauncher { findings, _, triagePlan ->
          repairLaunches.incrementAndGet()
          assertEquals(0, findings.findings.size)
          assertEquals(null, triagePlan)
          completedRepair()
        },
      ),
    )
    assertEquals(0, triageLaunches.get())
    assertEquals(2, repairLaunches.get())
    assertIs<ValidationGateCycleResult.Terminal>(cycle)
    assertIs<ValidationGateCycleTerminalOutcome.Completed>(cycle.outcome)
  }

  @Test
  fun `discrete findings after agent launch another agent without handing findings in`() {
    val findingOne = ValidationGateFinding("m1", "r1", "msg1", "loc1")
    val findingTwo = ValidationGateFinding("m2", "r2", "msg2", "loc2")
    val triageLaunches = AtomicInteger(0)
    val repairLaunches = AtomicInteger(0)
    val runner = ScriptedGateRunner(listOf(failedWith(findingOne, findingTwo), passed(forced = true)))
    val cycle = coordinator(declaredResolver(), runner, mutableListOf()).execute(
      ValidationGateCycleRequest(
        repoRoot = validationGateTestRepoRoot,
        request = minimalRequest(),
        validationDepth = ValidationDepth.DEFAULT,
        changedPaths = listOf("runtime-kotlin/foo.kt"),
        repositoryCheckpoint = "checkpoint",
        agentTriageLauncher = ValidationGateAgentTriageLauncher { _ ->
          triageLaunches.incrementAndGet()
          ValidationGateTriageResult.Captured("should not run")
        },
        agentRepairLauncher = ValidationGateAgentRepairLauncher { findings, _, triagePlan ->
          repairLaunches.incrementAndGet()
          assertEquals(0, findings.findings.size)
          assertEquals(null, triagePlan)
          completedRepair()
        },
      ),
    )
    assertEquals(0, triageLaunches.get())
    assertEquals(2, repairLaunches.get())
    assertIs<ValidationGateCycleTerminalOutcome.Completed>(
      assertIs<ValidationGateCycleResult.Terminal>(cycle).outcome,
    )
  }

  @Test
  fun `agent runs before first gate check`() {
    val repairLaunches = AtomicInteger(0)
    val runner = ScriptedGateRunner(
      listOf(failedEmptyFindings("unparseable blob"), passed(forced = true)),
    )
    coordinator(declaredResolver(), runner, mutableListOf()).execute(
      ValidationGateCycleRequest(
        repoRoot = validationGateTestRepoRoot,
        request = minimalRequest(),
        validationDepth = ValidationDepth.DEFAULT,
        changedPaths = listOf("runtime-kotlin/foo.kt"),
        repositoryCheckpoint = "checkpoint",
        agentRepairLauncher = ValidationGateAgentRepairLauncher { _, _, _ ->
          repairLaunches.incrementAndGet()
          completedRepair()
        },
      ),
    )
    assertEquals(2, repairLaunches.get())
    assertEquals(2, runner.calls)
  }

  @Test
  fun `second gate verify uses cache bypass`() {
    val progress = mutableListOf<FeatureTaskRuntimeValidationGateProgress>()
    val runner = ScriptedGateRunner(
      listOf(failedEmptyFindings("blob"), passed(forced = true)),
    )
    coordinator(declaredResolver(), runner, progress).execute(
      ValidationGateCycleRequest(
        repoRoot = validationGateTestRepoRoot,
        request = minimalRequest(),
        validationDepth = ValidationDepth.DEFAULT,
        changedPaths = listOf("runtime-kotlin/foo.kt"),
        repositoryCheckpoint = "checkpoint",
        agentRepairLauncher = ValidationGateAgentRepairLauncher { _, _, _ ->
          completedRepair()
        },
      ),
    )
    assertEquals(2, runner.calls)
    assertEquals(2, progress.last().gateRunCount)
    assertEquals(ValidationGateCacheMode.FORCED_FULL, runner.requests.last().cacheMode)
    assertEquals(true, runner.requests.last().terminalVerifying)
  }

  @Test
  fun `fromArtifactMap decodes findings_open with complete findings and legacy rows without repair_window_phase`() {
    val findingOne = linkedMapOf(
      "module" to "m1",
      "rule_or_test_id" to "r1",
      "message" to "msg1",
      "location" to "loc1",
    )
    val findingTwo = linkedMapOf(
      "module" to "m2",
      "rule_or_test_id" to "r2",
      "message" to "msg2",
      "location" to "loc2",
    )
    val decodedOpen = decodeValidationGateProgressFromArtifact(
      progressArtifact(
        gateRunCount = 1,
        outcome = "failed",
        cacheMode = "cache_eligible",
        extra = mapOf(
          "complete_findings" to listOf(findingOne, findingTwo),
          "repair_window_phase" to "findings_open",
        ),
      ),
    )
    assertEquals(FeatureTaskRuntimeValidationGateRepairWindowPhase.FINDINGS_OPEN, decodedOpen.repairWindowPhase)
    assertEquals(2, decodedOpen.completeFindings.size)
    assertEquals(0, decodedOpen.repairsUsed)

    val decodedLegacy = decodeValidationGateProgressFromArtifact(
      progressArtifact(
        gateRunCount = 1,
        outcome = "passed",
        cacheMode = "cache_eligible",
        extra = mapOf(
          "remaining_findings_dropped_count" to 0,
          "confirmation_retries_used" to 3,
          "substantiation_receipts" to listOf(mapOf("identity" to "legacy")),
        ),
      ),
    )
    assertEquals(FeatureTaskRuntimeValidationGateRepairWindowPhase.NONE, decodedLegacy.repairWindowPhase)
    assertEquals(1, decodedLegacy.gateRunCount)
    assertEquals(emptyList(), decodedLegacy.completeFindings)
    assertEquals(0, decodedLegacy.repairsUsed)

    val decodedWithRepairsUsed = decodeValidationGateProgressFromArtifact(
      progressArtifact(
        gateRunCount = 2,
        outcome = "failed",
        cacheMode = "forced_full",
        extra = mapOf(
          "complete_findings" to listOf(findingOne),
          "repair_window_phase" to "findings_open",
          "repairs_used" to 3,
        ),
      ),
    )
    assertEquals(3, decodedWithRepairsUsed.repairsUsed)
  }

  @Test
  fun `blocked agent turn still runs confirmation and can complete`() {
    val repairLaunches = AtomicInteger(0)
    val runner = ScriptedGateRunner(listOf(failedEmptyFindings("still red"), passed(forced = true)))
    val cycle = coordinator(declaredResolver(), runner, mutableListOf()).execute(
      ValidationGateCycleRequest(
        repoRoot = validationGateTestRepoRoot,
        request = minimalRequest(),
        validationDepth = ValidationDepth.DEFAULT,
        changedPaths = listOf("runtime-kotlin/foo.kt"),
        repositoryCheckpoint = "checkpoint",
        agentRepairLauncher = ValidationGateAgentRepairLauncher { _, _, _ ->
          val launch = repairLaunches.incrementAndGet()
          if (launch == 1) blockedRepair() else completedRepair()
        },
      ),
    )
    assertEquals(2, repairLaunches.get())
    assertEquals(2, runner.calls)
    assertIs<ValidationGateCycleTerminalOutcome.Completed>(
      assertIs<ValidationGateCycleResult.Terminal>(cycle).outcome,
    )
  }

  @Test
  fun `missing pack validation_gate blocks without launching an agent`() {
    val repairLaunches = AtomicInteger(0)
    val cycle = coordinator(
      ValidationGateResolver { listOf(kotlinPackWithoutGate()) },
      neverRunsGate(),
      mutableListOf(),
    ).execute(
      ValidationGateCycleRequest(
        repoRoot = validationGateTestRepoRoot,
        request = minimalRequest(),
        validationDepth = ValidationDepth.DEFAULT,
        changedPaths = listOf("runtime-kotlin/foo.kt"),
        repositoryCheckpoint = "checkpoint",
        agentRepairLauncher = ValidationGateAgentRepairLauncher { _, _, _ ->
          repairLaunches.incrementAndGet()
          error("validate must not launch an agent when no pack declares validation_gate")
        },
      ),
    )
    assertEquals(0, repairLaunches.get())
    val blocked = assertIs<ValidationGateCycleTerminalOutcome.Blocked>(
      assertIs<ValidationGateCycleResult.Terminal>(cycle).outcome,
    )
    assertEquals(FeatureTaskRuntimeValidationGateCoordinator.ABSENT_VALIDATION_GATE_REASON, blocked.reason)
  }

  @Test
  fun `catalog gate wins when review routing only selects a no-gate fallback pack`() {
    val resolver = ValidationGateResolver {
      listOf(
        reviewFallbackPackWithoutGate(),
        kotlinPackWithoutGate().copy(validationGate = validationGateTestDeclaration),
      )
    }
    val resolution = resolver.resolve(listOf("notes.txt"))
    val declared = assertIs<ValidationGateResolution.Declared>(resolution)
    assertEquals("kotlin", declared.packSlug)
  }

  private fun progressArtifact(
    gateRunCount: Int,
    outcome: String,
    cacheMode: String,
    extra: Map<String, Any?>,
  ): Map<String, Any?> = mapOf(
    "contract_version" to FEATURE_TASK_RUNTIME_PERSISTENCE_CONTRACT_VERSION,
    "gate_run_count" to gateRunCount,
    "gate_runs" to listOf(
      mapOf(
        "duration_ms" to 1L,
        "outcome" to outcome,
        "cache_mode" to cacheMode,
        "executed_work_units" to 1,
      ),
    ),
    "remaining_findings" to emptyList<Map<String, String?>>(),
  ) + extra
}
