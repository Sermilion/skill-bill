package skillbill.engine.featuretask

import skillbill.contracts.JsonCodec
import skillbill.contracts.SharedPayloadKeys
import skillbill.contracts.workflow.FEATURE_TASK_RUNTIME_CONTRACT_VERSION
import skillbill.contracts.workflow.ValidationEvidencePayloadKeys
import skillbill.engine.featuretask.validation.FeatureTaskRuntimeValidationGateCoordinator
import skillbill.error.InvalidFeatureTaskRuntimeValidationEvidenceSchemaError
import skillbill.workflow.model.WorkflowStepStatus
import skillbill.workflow.taskruntime.FeatureTaskRuntimePhaseWorkflowDefinition
import skillbill.workflow.taskruntime.decodeValidationGateExecutionEvidenceFromArtifact
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimePhaseOutput
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimePhaseRecord
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeValidationGateRunRecord
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeVerdict
import skillbill.workflow.taskruntime.model.NormalizedFeatureTaskRuntimePhaseOutput
import skillbill.workflow.taskruntime.model.ValidationGateCacheMode
import skillbill.workflow.taskruntime.model.ValidationGateRunOutcome
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
class FeatureTaskRuntimeValidationEvidenceSettlementTest {
  @Test
  fun `prose passed status cannot satisfy validate settlement when required command exited nonzero`() {
    val envelope = topLevelValidateEnvelope(
      validateEnvelope(
        command = "./gradlew check",
        exitCode = 1,
      ),
    )
    val evidence = requireNotNull(
      validationEvidenceFromEnvelope(envelope, FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_VALIDATE),
    )
    assertFailsWith<InvalidFeatureTaskRuntimeValidationEvidenceSchemaError> {
      evidence.requireSuccessfulCommand("./gradlew check", FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_VALIDATE)
    }
  }

  @Test
  fun `minimally shaped evidence with opaque metadata satisfies required command check`() {
    val envelope = topLevelValidateEnvelope(
      validateEnvelope(
        command = "./gradlew check",
        exitCode = 0,
        extraResultFields = mapOf("signal" to "BUILD SUCCESSFUL"),
      ),
    )
    val evidence = requireNotNull(
      validationEvidenceFromEnvelope(envelope, FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_VALIDATE),
    )
    assertEquals(0, evidence.requireSuccessfulCommand("./gradlew check", "validate").exitCode)
  }

  @Test
  fun `runtime owned output preserves every gate command and exit code`() {
    val output = FeatureTaskRuntimeValidationGateCoordinator.runtimeOwnedValidationOutput(
      repositoryCheckpoint = "checkpoint",
      measurements = listOf(
        FeatureTaskRuntimeValidationGateRunRecord(
          durationMs = 1,
          outcome = ValidationGateRunOutcome.FAILED,
          cacheMode = ValidationGateCacheMode.CACHE_ELIGIBLE,
          executedWorkUnits = 1,
          executedChecks = listOf("runtime-engine|compileKotlin"),
          command = "./gradlew check",
          exitCode = 1,
        ),
        FeatureTaskRuntimeValidationGateRunRecord(
          durationMs = 2,
          outcome = ValidationGateRunOutcome.PASSED,
          cacheMode = ValidationGateCacheMode.FORCED_FULL,
          executedWorkUnits = 1,
          executedChecks = listOf("runtime-engine|compileKotlin", "runtime-engine|test"),
          command = "./gradlew check --offline",
          exitCode = 0,
        ),
      ),
      requiredCommand = "./gradlew check --offline",
    )
    val envelope = requireNotNull(
      JsonCodec.parseObjectOrNull(output.payload)
        ?.let(JsonCodec::jsonElementToValue)
        ?.let(JsonCodec::anyToStringAnyMap),
    )
    val evidence = requireNotNull(
      validationEvidenceFromEnvelope(envelope, FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_VALIDATE),
    )

    assertEquals(
      listOf("./gradlew check", "./gradlew check --offline"),
      evidence.results.map { it.command },
    )
    assertEquals(listOf(1, 0), evidence.results.map { it.exitCode })
    assertEquals(FeatureTaskRuntimeVerdict.SATISFIED.wireValue, envelope[SharedPayloadKeys.VERDICT])
    val validationResult = JsonCodec.anyToStringAnyMap(
      JsonCodec.anyToStringAnyMap(envelope[SharedPayloadKeys.PRODUCED_OUTPUTS])
        ?.get(ValidationEvidencePayloadKeys.VALIDATION_RESULT),
    ) ?: error("validation_result missing")
    val gateEvidence = decodeValidationGateExecutionEvidenceFromArtifact(validationResult, "validate")
    assertEquals(
      listOf("runtime-engine|compileKotlin", "runtime-engine|test"),
      gateEvidence.checks,
    )
    assertEquals(2, gateEvidence.gateRunCount)
    assertEquals(ValidationGateCacheMode.CACHE_ELIGIBLE, gateEvidence.gateRuns.first().cacheMode)
    assertEquals(ValidationGateCacheMode.FORCED_FULL, gateEvidence.gateRuns.last().cacheMode)
  }

  @Test
  fun `resume invalidates completed validate when persisted evidence is red`() {
    val completed = mutableSetOf(FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_VALIDATE)
    val gateInvalidated = mutableSetOf<String>()
    val record = validatePhaseRecord(
      validateEnvelope(command = "./gradlew check", exitCode = 1),
    )
    val state = ValidationSettlementState(
        completed = completed,
        initialRecords = mapOf(FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_VALIDATE to record),
        transitions = FeatureTaskRuntimePhaseWorkflowDefinition.transitions,
        gateInvalidatedPhases = gateInvalidated,
      )
    invalidateIncompleteValidationSettlement(
      state = state,
      validation = absentGateValidation(),
    )
    assertFalse(FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_VALIDATE in state.completed)
    assertTrue(FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_VALIDATE in state.gateInvalidatedPhases)
  }

  @Test
  fun `resume keeps completed validate when persisted evidence is valid`() {
    val completed = mutableSetOf(FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_VALIDATE)
    val gateInvalidated = mutableSetOf<String>()
    val record = validatePhaseRecord(
      validateEnvelope(command = "./gradlew check", exitCode = 0),
    )
    val state = ValidationSettlementState(
        completed = completed,
        initialRecords = mapOf(FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_VALIDATE to record),
        transitions = FeatureTaskRuntimePhaseWorkflowDefinition.transitions,
        gateInvalidatedPhases = gateInvalidated,
      )
    invalidateIncompleteValidationSettlement(
      state = state,
      validation = absentGateValidation(),
    )
    assertTrue(FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_VALIDATE in state.completed)
    assertTrue(gateInvalidated.isEmpty())
  }

  @Test
  fun `resume invalidates completed validate when persisted evidence is missing`() {
    val completed = mutableSetOf(FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_VALIDATE)
    val gateInvalidated = mutableSetOf<String>()
    val state = ValidationSettlementState(
        completed = completed,
        initialRecords = mapOf(
          FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_VALIDATE to validatePhaseRecord(emptyMap()),
        ),
        transitions = FeatureTaskRuntimePhaseWorkflowDefinition.transitions,
        gateInvalidatedPhases = gateInvalidated,
      )
    invalidateIncompleteValidationSettlement(
      state = state,
      validation = absentGateValidation(),
    )

    assertFalse(FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_VALIDATE in state.completed)
    assertTrue(FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_VALIDATE in state.gateInvalidatedPhases)
  }

  @Test
  fun `resume invalidates completed validate when persisted evidence is malformed`() {
    val completed = mutableSetOf(FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_VALIDATE)
    val gateInvalidated = mutableSetOf<String>()
    val state = ValidationSettlementState(
        completed = completed,
        initialRecords = mapOf(
          FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_VALIDATE to validatePhaseRecord(
            mapOf(
              ValidationEvidencePayloadKeys.VALIDATION_EVIDENCE to mapOf(
                ValidationEvidencePayloadKeys.CONTRACT_VERSION to "0.1",
                ValidationEvidencePayloadKeys.RESULTS to listOf(
                  mapOf(ValidationEvidencePayloadKeys.COMMAND to "./gradlew check"),
                ),
              ),
            ),
          ),
        ),
        transitions = FeatureTaskRuntimePhaseWorkflowDefinition.transitions,
        gateInvalidatedPhases = gateInvalidated,
      )
    invalidateIncompleteValidationSettlement(
      state = state,
      validation = absentGateValidation(),
    )

    assertFalse(FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_VALIDATE in state.completed)
    assertTrue(FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_VALIDATE in state.gateInvalidatedPhases)
  }

  private fun absentGateValidation(): ValidationSettlementValidation = ValidationSettlementValidation(
    validatedRecordToOutput = { record ->
      record.outputArtifact?.let { artifact ->
        val envelope = JsonCodec.parseObjectOrNull(artifact)
          ?.let(JsonCodec::jsonElementToValue)
          ?.let(JsonCodec::anyToStringAnyMap)
          ?: return@let null
        FeatureTaskRuntimePhaseOutput(
          phaseId = record.phaseId,
          iteration = record.attemptCount,
          payload = artifact,
          normalizedOutput = NormalizedFeatureTaskRuntimePhaseOutput(
            canonicalJson = artifact,
            envelope = envelope,
          ),
        )
      }
    },
    validationEvidenceCommandResolver = { evidence -> evidence?.results?.lastOrNull()?.command },
    durableVerdictFor = { FeatureTaskRuntimeVerdict.SATISFIED },
  )

  private fun validatePhaseRecord(validationResult: Map<String, Any?>): FeatureTaskRuntimePhaseRecord =
    FeatureTaskRuntimePhaseRecord(
      phaseId = FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_VALIDATE,
      status = WorkflowStepStatus.COMPLETED,
      attemptCount = 1,
      startedAt = "2026-01-01T00:00:00Z",
      resolvedAgentId = "claude",
      finishedAt = "2026-01-01T00:01:00Z",
      outputArtifact = JsonCodec.mapToJsonString(
        topLevelValidateEnvelope(validationResult),
      ),
    )

  private fun topLevelValidateEnvelope(validationResult: Map<String, Any?>): Map<String, Any?> = mapOf(
    SharedPayloadKeys.CONTRACT_VERSION to FEATURE_TASK_RUNTIME_CONTRACT_VERSION,
    SharedPayloadKeys.PHASE_ID to FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_VALIDATE,
    SharedPayloadKeys.STATUS to "completed",
    SharedPayloadKeys.SUMMARY to "Validate output.",
    SharedPayloadKeys.PRODUCED_OUTPUTS to mapOf(
      ValidationEvidencePayloadKeys.VALIDATION_RESULT to validationResult,
    ),
  )

  private fun validateEnvelope(
    command: String,
    exitCode: Int,
    extraResultFields: Map<String, Any?> = emptyMap(),
  ): Map<String, Any?> = mapOf(
    "validation_status" to "passed",
    "checks" to emptyList<Any?>(),
    "repository_checkpoint" to mapOf("fingerprint" to "fixture-checkpoint-1"),
    ValidationEvidencePayloadKeys.VALIDATION_EVIDENCE to mapOf(
      ValidationEvidencePayloadKeys.CONTRACT_VERSION to "0.1",
      ValidationEvidencePayloadKeys.RESULTS to listOf(
        buildMap {
          put(ValidationEvidencePayloadKeys.COMMAND, command)
          put(ValidationEvidencePayloadKeys.EXIT_CODE, exitCode)
          putAll(extraResultFields)
        },
      ),
    ),
  )
}
