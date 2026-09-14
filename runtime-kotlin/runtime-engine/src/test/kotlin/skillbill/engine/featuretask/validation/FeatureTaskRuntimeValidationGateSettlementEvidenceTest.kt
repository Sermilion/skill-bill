package skillbill.engine.featuretask.validation

import skillbill.contracts.JsonCodec
import skillbill.contracts.SharedPayloadKeys
import skillbill.contracts.workflow.ValidationEvidencePayloadKeys
import skillbill.engine.featuretask.validation.model.ValidationGateAgentRepairLauncher
import skillbill.engine.featuretask.validation.model.ValidationGateCycleRequest
import skillbill.engine.featuretask.validation.model.ValidationGateCycleResult
import skillbill.engine.featuretask.validation.model.ValidationGateCycleTerminalOutcome
import skillbill.infrastructure.fs.contracts.workflow.FeatureTaskRuntimePhaseOutputSchemaValidator
import skillbill.infrastructure.fs.contracts.workflow.FeatureTaskRuntimeValidationEvidenceSchemaValidator
import skillbill.ports.validation.model.ValidationGateFinding
import skillbill.ports.validation.model.ValidationGateRunResult
import skillbill.workflow.goal.model.ValidationDepth
import skillbill.workflow.taskruntime.decodeValidationGateExecutionEvidenceFromArtifact
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeValidationGateProgress
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeValidationGateRunRecord
import skillbill.workflow.taskruntime.model.ValidationGateCacheMode
import skillbill.workflow.taskruntime.model.ValidationGateRunOutcome
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
class FeatureTaskRuntimeValidationGateSettlementEvidenceTest {
  @Test
  fun `settlement projects executed work and checks from the gate runner result`() {
    val result = ValidationGateRunResult(
      exitCode = 0,
      durationMs = 3,
      outcome = ValidationGateRunOutcome.PASSED,
      cacheMode = ValidationGateCacheMode.CACHE_ELIGIBLE,
      executedWorkUnits = 3,
      executedCheckIdentities = listOf(
        "runtime-engine|compileKotlin",
        "runtime-engine|compileTestKotlin",
        "runtime-engine|test",
      ),
      findings = emptyList(),
    )
    val cycle = coordinator(
      resolver = declaredResolver(),
      runner = ScriptedGateRunner(listOf(result)),
      progress = mutableListOf(),
    ).execute(
      cycle = ValidationGateCycleRequest(
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
    val terminal = assertIs<ValidationGateCycleResult.Terminal>(cycle)
    val output = assertIs<ValidationGateCycleTerminalOutcome.Completed>(terminal.outcome).output
    val gateEvidence = decodeValidationGateExecutionEvidenceFromArtifact(
      validationResultFrom(output.payload),
      "validate",
    )!!

    assertEquals(3, gateEvidence.gateRuns.single().executedWorkUnits)
    assertEquals(result.executedCheckIdentities, gateEvidence.gateRuns.single().executedChecks)
    assertEquals(result.executedCheckIdentities, gateEvidence.checks)
  }

  @Test
  fun `coordinator persists cache-eligible failure and forced-full retry evidence`() {
    val progress = mutableListOf<FeatureTaskRuntimeValidationGateProgress>()
    val completed = executeRetryCycle(
      progress = progress,
      runner = ScriptedGateRunner(listOf(cacheEligibleFailure(), forcedFullPass())),
    )
    assertSettledRuns(progress.last().gateRuns)
    assertSettledArtifact(completed)
  }

  private fun cacheEligibleFailure(): ValidationGateRunResult = ValidationGateRunResult(
    exitCode = 1,
    durationMs = 3,
    outcome = ValidationGateRunOutcome.FAILED,
    cacheMode = ValidationGateCacheMode.CACHE_ELIGIBLE,
    executedWorkUnits = 2,
    executedCheckIdentities = listOf("runtime-engine|compileKotlin"),
    findings = listOf(ValidationGateFinding("runtime-engine", "test", "failed", "Test.kt")),
  )

  private fun forcedFullPass(): ValidationGateRunResult = ValidationGateRunResult(
    exitCode = 0,
    durationMs = 4,
    outcome = ValidationGateRunOutcome.PASSED,
    cacheMode = ValidationGateCacheMode.FORCED_FULL,
    executedWorkUnits = 3,
    executedCheckIdentities = listOf("runtime-engine|compileKotlin", "runtime-engine|test"),
    findings = emptyList(),
  )

  private fun executeRetryCycle(
    progress: MutableList<FeatureTaskRuntimeValidationGateProgress>,
    runner: ScriptedGateRunner,
  ): ValidationGateCycleTerminalOutcome.Completed {
    val cycle = coordinator(
      resolver = declaredResolver(),
      runner = runner,
      progress = progress,
    ).execute(
      cycle = ValidationGateCycleRequest(
        repoRoot = validationGateTestRepoRoot,
        request = minimalRequest(),
        validationDepth = ValidationDepth.DEFAULT,
        changedPaths = listOf("runtime-kotlin/foo.kt"),
        repositoryCheckpoint = "checkpoint",
        agentRepairLauncher = { _, _, _ -> completedRepair() },
      ),
    )
    return assertIs<ValidationGateCycleTerminalOutcome.Completed>(
      assertIs<ValidationGateCycleResult.Terminal>(cycle).outcome,
    )
  }

  private fun assertSettledRuns(settled: List<FeatureTaskRuntimeValidationGateRunRecord>) {
    assertEquals(2, settled.size)
    assertEquals(
      listOf(ValidationGateCacheMode.CACHE_ELIGIBLE, ValidationGateCacheMode.FORCED_FULL),
      settled.map { it.cacheMode },
    )
    assertEquals(
      listOf(ValidationGateRunOutcome.FAILED, ValidationGateRunOutcome.PASSED),
      settled.map { it.outcome },
    )
    assertEquals(listOf(2, 3), settled.map { it.executedWorkUnits })
    assertEquals(
      listOf(
        listOf("runtime-engine|compileKotlin"),
        listOf("runtime-engine|compileKotlin", "runtime-engine|test"),
      ),
      settled.map { it.executedChecks },
    )
  }

  private fun assertSettledArtifact(completed: ValidationGateCycleTerminalOutcome.Completed) {
    val settledArtifact = decodeValidationGateExecutionEvidenceFromArtifact(
      validationResultFrom(completed.output.payload),
      "validate",
    )!!
    assertEquals(
      listOf(ValidationGateRunOutcome.FAILED, ValidationGateRunOutcome.PASSED),
      settledArtifact.gateRuns.map { it.outcome },
    )
    assertEquals(listOf(2, 3), settledArtifact.gateRuns.map { it.executedWorkUnits })
    assertEquals(
      listOf(
        listOf("runtime-engine|compileKotlin"),
        listOf("runtime-engine|compileKotlin", "runtime-engine|test"),
      ),
      settledArtifact.gateRuns.map { it.executedChecks },
    )
  }

  @Test
  fun `gradle compile and test tasks with zero executed work units still record check identities`() {
    val output = FeatureTaskRuntimeValidationGateCoordinator.runtimeOwnedValidationOutput(
      repositoryCheckpoint = "checkpoint",
      measurements = listOf(
        FeatureTaskRuntimeValidationGateRunRecord(
          durationMs = 10,
          outcome = ValidationGateRunOutcome.PASSED,
          cacheMode = ValidationGateCacheMode.CACHE_ELIGIBLE,
          executedWorkUnits = 0,
          executedChecks = listOf(
            "runtime-engine|compileKotlin",
            "runtime-engine|compileTestKotlin",
            "runtime-engine|test",
          ),
          command = "./gradlew check --continue",
          exitCode = 0,
        ),
      ),
      requiredCommand = "./gradlew check --continue",
    )
    val validationResult = validationResultFrom(output.payload)
    val gateEvidence = decodeValidationGateExecutionEvidenceFromArtifact(validationResult, "validate")!!
    FeatureTaskRuntimePhaseOutputSchemaValidator.validatePhaseOutputText(output.payload, "validate")
    assertEquals(0, gateEvidence.gateRuns.single().executedWorkUnits)
    assertFalse(gateEvidence.zeroWork)
    assertTrue(gateEvidence.evidenceRecorded)
    assertEquals(
      listOf(
        "runtime-engine|compileKotlin",
        "runtime-engine|compileTestKotlin",
        "runtime-engine|test",
      ),
      gateEvidence.checks,
    )
  }

  @Test
  fun `settlement preserves cache-eligible failure and forced-full pass evidence independently`() {
    val output = FeatureTaskRuntimeValidationGateCoordinator.runtimeOwnedValidationOutput(
      repositoryCheckpoint = "checkpoint",
      measurements = listOf(
        gateRun(
          GateRunFixture(
            outcome = ValidationGateRunOutcome.FAILED,
            cacheMode = ValidationGateCacheMode.CACHE_ELIGIBLE,
            execution = GateExecution(3, listOf("runtime-engine|compileKotlin")),
            command = "./gradlew check --continue",
            exitCode = 1,
          ),
        ),
        gateRun(
          GateRunFixture(
            outcome = ValidationGateRunOutcome.PASSED,
            cacheMode = ValidationGateCacheMode.FORCED_FULL,
            execution = GateExecution(
              3,
              listOf("runtime-engine|compileKotlin", "runtime-engine|test"),
            ),
            command = "./gradlew check --continue --rerun-tasks",
            exitCode = 0,
          ),
        ),
      ),
      requiredCommand = "./gradlew check --continue --rerun-tasks",
    )
    val validationResult = validationResultFrom(output.payload)
    val gateEvidence = decodeValidationGateExecutionEvidenceFromArtifact(
      validationResult,
      "validate",
    )!!
    assertEquals(2, gateEvidence.gateRunCount)
    assertEquals(ValidationGateCacheMode.CACHE_ELIGIBLE, gateEvidence.gateRuns.first().cacheMode)
    assertEquals(ValidationGateRunOutcome.FAILED, gateEvidence.gateRuns.first().outcome)
    assertEquals(ValidationGateCacheMode.FORCED_FULL, gateEvidence.gateRuns.last().cacheMode)
    assertEquals(
      listOf("runtime-engine|compileKotlin", "runtime-engine|test"),
      gateEvidence.checks,
    )
    assertEquals(
      listOf(3, 3),
      gateEvidence.gateRuns.map { it.executedWorkUnits },
    )
    assertEquals(
      listOf(ValidationGateRunOutcome.FAILED, ValidationGateRunOutcome.PASSED),
      gateEvidence.gateRuns.map { it.outcome },
    )
    assertFalse(gateEvidence.zeroWork)
    FeatureTaskRuntimeValidationEvidenceSchemaValidator.validate(
      JsonCodec.anyToStringAnyMap(validationResult[ValidationEvidencePayloadKeys.VALIDATION_EVIDENCE])
        ?: error("validation_evidence missing"),
      "validate",
    )
  }

  private fun gateRun(fixture: GateRunFixture): FeatureTaskRuntimeValidationGateRunRecord =
    FeatureTaskRuntimeValidationGateRunRecord(
      durationMs = 1,
      outcome = fixture.outcome,
      cacheMode = fixture.cacheMode,
      executedWorkUnits = fixture.execution.workUnits,
      executedChecks = fixture.execution.checks,
      command = fixture.command,
      exitCode = fixture.exitCode,
    )

  private data class GateRunFixture(
    val outcome: ValidationGateRunOutcome,
    val cacheMode: ValidationGateCacheMode,
    val execution: GateExecution,
    val command: String,
    val exitCode: Int,
  )

  private data class GateExecution(
    val workUnits: Int,
    val checks: List<String>,
  )

  private fun validationResultFrom(payload: String): Map<String, Any?> = JsonCodec.anyToStringAnyMap(
    JsonCodec.anyToStringAnyMap(
      JsonCodec.parseObjectOrNull(payload)
        ?.let(JsonCodec::jsonElementToValue)
        ?.let(JsonCodec::anyToStringAnyMap)
        ?.get(SharedPayloadKeys.PRODUCED_OUTPUTS),
    )?.get(ValidationEvidencePayloadKeys.VALIDATION_RESULT),
  ) ?: error("validation_result missing")
}
