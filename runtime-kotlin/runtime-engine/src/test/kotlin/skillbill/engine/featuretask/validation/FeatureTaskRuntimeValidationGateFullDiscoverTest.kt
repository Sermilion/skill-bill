package skillbill.engine.featuretask.validation

import skillbill.engine.featuretask.validation.model.ValidationGateAgentRepairLauncher
import skillbill.engine.featuretask.validation.model.ValidationGateCycleRequest
import skillbill.engine.featuretask.validation.model.ValidationGateCycleResult
import skillbill.engine.featuretask.validation.model.ValidationGateCycleTerminalOutcome
import skillbill.ports.validation.model.ValidationGateFinding
import skillbill.workflow.goal.model.ValidationDepth
import skillbill.workflow.taskruntime.model.validation.FeatureTaskRuntimeValidationGateProgress
import skillbill.workflow.taskruntime.model.validation.FeatureTaskRuntimeValidationGateRepairWindowPhase
import skillbill.workflow.taskruntime.model.validation.ValidationGateCacheMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
class FeatureTaskRuntimeValidationGateFullDiscoverTest {
  private val declaration = validationGateTestDeclaration

  @Test
  fun `agent then gate then agent confirms with cache-bypassing verify`() {
    val progress = mutableListOf<FeatureTaskRuntimeValidationGateProgress>()
    val compiler = ValidationGateFinding("app", "e: Unresolved reference", "compile error", "Foo.kt")
    val laterTest = ValidationGateFinding("later-module", "LaterTest.fails", "assertion failed", "LaterTest.kt")
    val repairLaunchCounts = mutableListOf<Int>()
    val runner = ScriptedGateRunner(listOf(failedWith(compiler, laterTest), passed(forced = true)))
    val cycle = coordinator(declaredResolver(), runner, progress).execute(
      cycle = fullCycle { findings, _, _ ->
        repairLaunchCounts += findings.findings.size
        completedRepair()
      },
    )
    assertEquals(listOf(0, 0), repairLaunchCounts)
    assertEquals(2, runner.calls)
    assertEquals(listOf("echo", "collect-all-full"), runner.requests[0].argv)
    assertEquals(ValidationGateCacheMode.FORCED_FULL, runner.requests[0].cacheMode)
    assertEquals(true, runner.requests[0].terminalVerifying)
    assertEquals(listOf("echo", "collect-all-full"), runner.requests[1].argv)
    assertEquals(ValidationGateCacheMode.FORCED_FULL, runner.requests[1].cacheMode)
    assertEquals(true, runner.requests[1].terminalVerifying)
    assertTrue(runner.requests.none { it.argv == declaration.fullGateCommand })
    assertTrue(progress.any { it.completeFindings.size == 2 })
    assertTrue(
      progress.any {
        it.repairWindowPhase == FeatureTaskRuntimeValidationGateRepairWindowPhase.FINDINGS_OPEN
      },
    )
    assertIs<ValidationGateCycleTerminalOutcome.Completed>(
      assertIs<ValidationGateCycleResult.Terminal>(cycle).outcome,
    )
  }

  @Test
  fun `each validate agent launch receives an empty finding handoff`() {
    val findings = (1..65).map { index ->
      ValidationGateFinding("m$index", "t$index", "message-$index", "loc-$index")
    }
    val launchSizes = mutableListOf<Int>()
    val runner = ScriptedGateRunner(listOf(failedWith(*findings.toTypedArray()), passed(forced = true)))
    val cycle = coordinator(declaredResolver(), runner, mutableListOf()).execute(
      cycle = fullCycle { page, _, _ ->
        launchSizes += page.findings.size
        completedRepair()
      },
    )
    assertEquals(listOf(0, 0), launchSizes)
    assertEquals(2, runner.calls)
    assertIs<ValidationGateCycleTerminalOutcome.Completed>(
      assertIs<ValidationGateCycleResult.Terminal>(cycle).outcome,
    )
  }

  @Test
  fun `failing verify keeps launching validate agents until the three-turn cap then records remaining findings`() {
    val progress = mutableListOf<FeatureTaskRuntimeValidationGateProgress>()
    val verifyFinding = ValidationGateFinding("later", "LaterTest", "still failing", "LaterTest.kt")
    val repairLaunchCount = mutableListOf<Int>()
    val maxTurns = FeatureTaskRuntimeValidationGateCoordinator.MAX_REPAIR_TURNS
    val runner = ScriptedGateRunner(
      List(maxTurns) { failedWith(verifyFinding) },
    )
    val cycle = coordinator(declaredResolver(), runner, progress).execute(
      cycle = fullCycle { findings, _, _ ->
        repairLaunchCount += findings.findings.size
        completedRepair()
      },
    )
    val blocked = assertIs<ValidationGateCycleTerminalOutcome.Blocked>(
      assertIs<ValidationGateCycleResult.Terminal>(cycle).outcome,
    )
    assertEquals(maxTurns, repairLaunchCount.size)
    assertTrue(repairLaunchCount.all { it == 0 })
    assertEquals(maxTurns, runner.calls)
    assertEquals(
      FeatureTaskRuntimeValidationGateCoordinator.FINDINGS_REMAIN_AFTER_RESTARTS_REASON,
      blocked.reason,
    )
    assertEquals("LaterTest", blocked.remainingFindings?.findings?.single()?.ruleOrTestId)
    assertEquals(
      FeatureTaskRuntimeValidationGateRepairWindowPhase.FINDINGS_OPEN,
      progress.last().repairWindowPhase,
    )
    assertEquals(1, progress.last().completeFindings.size)
    assertEquals(maxTurns, progress.last().repairsUsed)
  }

  private fun fullCycle(repair: ValidationGateAgentRepairLauncher): ValidationGateCycleRequest =
    ValidationGateCycleRequest(
      repoRoot = validationGateTestRepoRoot,
      request = minimalRequest(),
      validationDepth = ValidationDepth.FULL,
      changedPaths = listOf("runtime-kotlin/foo.kt"),
      repositoryCheckpoint = "checkpoint",
      agentRepairLauncher = repair,
    )
}
