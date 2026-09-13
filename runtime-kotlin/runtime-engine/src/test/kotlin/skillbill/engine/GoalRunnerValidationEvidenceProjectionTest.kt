package skillbill.engine

import skillbill.contracts.JsonCodec
import skillbill.contracts.SharedPayloadKeys
import skillbill.contracts.workflow.FEATURE_TASK_RUNTIME_CONTRACT_VERSION
import skillbill.contracts.workflow.ValidationEvidencePayloadKeys
import skillbill.engine.featuretask.model.FeatureTaskRuntimePhaseStateRequest
import skillbill.engine.featuretask.validation.FeatureTaskRuntimeValidationGateCoordinator
import skillbill.engine.goalrunner.goalRunnerDefaultPhaseRecorder
import skillbill.engine.goalrunner.model.GoalRunnerStatusRequest
import skillbill.engine.goalrunner.testGoalRunnerStatusService
import skillbill.workflow.decomposition.model.DecompositionManifest
import skillbill.workflow.decomposition.model.DecompositionSubtask
import skillbill.workflow.taskruntime.FeatureTaskRuntimePhaseWorkflowDefinition
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeValidationGateRunRecord
import skillbill.workflow.taskruntime.model.ValidationGateCacheMode
import skillbill.workflow.taskruntime.model.ValidationGateRunOutcome
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GoalRunnerValidationEvidenceProjectionTest {
  @Test
  fun `completed subtask status reports command and exit code for valid evidence`() {
    val workflowId = "wfl-validation-evidence"
    val recorder = goalRunnerDefaultPhaseRecorder()
    recorder.ensureWorkflowOpen(workflowId, "goal-validation-evidence")
    recorder.recordPhaseState(
      FeatureTaskRuntimePhaseStateRequest(
        workflowId = workflowId,
        phaseId = FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_VALIDATE,
        status = "completed",
        attemptCount = 1,
        resolvedAgentId = "claude",
        finished = true,
        outputArtifact = JsonCodec.mapToJsonString(validValidateOutput()),
      ),
    )
    val manifest = completedManifest(workflowId)
    val service = testGoalRunnerStatusService(
      manifestStore = InMemoryGoalManifestStore(manifest),
      outcomeStore = RecordingOutcomeStore(),
      phaseRecorder = recorder,
    )
    val status = requireNotNull(
      service.status(GoalRunnerStatusRequest(issueKey = manifest.issueKey, invokedAgentId = "codex")),
    )
    val validation = status.completedSubtaskValidation.single()
    assertEquals(1, validation.subtaskId)
    assertNull(validation.integrityProblem)
    assertEquals("./gradlew check", validation.evidence?.results?.single()?.command)
    assertEquals(0, validation.evidence?.results?.single()?.exitCode)
  }

  @Test
  fun `completed subtask status exposes gate execution checks from settled validate artifact`() {
    val workflowId = "wfl-gate-checks"
    val recorder = goalRunnerDefaultPhaseRecorder()
    recorder.ensureWorkflowOpen(workflowId, "goal-gate-checks")
    recorder.recordPhaseState(
      FeatureTaskRuntimePhaseStateRequest(
        workflowId = workflowId,
        phaseId = FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_VALIDATE,
        status = "completed",
        attemptCount = 1,
        resolvedAgentId = "claude",
        finished = true,
        outputArtifact = gateEvidenceOutput(),
      ),
    )
    val manifest = completedManifest(workflowId)
    val service = testGoalRunnerStatusService(
      manifestStore = InMemoryGoalManifestStore(manifest),
      outcomeStore = RecordingOutcomeStore(),
      phaseRecorder = recorder,
    )
    val validation = requireNotNull(
      service.status(GoalRunnerStatusRequest(issueKey = manifest.issueKey, invokedAgentId = "codex")),
    ).completedSubtaskValidation.single()
    assertEquals(listOf("runtime-engine|compileKotlin"), validation.gateExecutionEvidence?.checks)
    assertEquals(1, validation.gateExecutionEvidence?.gateRunCount)
    assertEquals(
      ValidationGateCacheMode.FORCED_FULL,
      validation.gateExecutionEvidence?.gateRuns?.single()?.cacheMode,
    )
    assertEquals(
      ValidationGateRunOutcome.PASSED,
      validation.gateExecutionEvidence?.gateRuns?.single()?.outcome,
    )
    assertEquals(1, validation.gateExecutionEvidence?.gateRuns?.single()?.executedWorkUnits)
    val projectedResult = JsonCodec.anyToStringAnyMap(
      validation.toStatusMap()[ValidationEvidencePayloadKeys.VALIDATION_RESULT],
    )
    assertEquals(
      listOf("runtime-engine|compileKotlin"),
      projectedResult?.get(ValidationEvidencePayloadKeys.CHECKS),
    )
    assertEquals(
      1,
      projectedResult?.get(ValidationEvidencePayloadKeys.GATE_RUN_COUNT),
    )
  }

  private fun gateEvidenceOutput(): String = FeatureTaskRuntimeValidationGateCoordinator.runtimeOwnedValidationOutput(
    repositoryCheckpoint = "fixture-checkpoint-1",
    measurements = listOf(
      FeatureTaskRuntimeValidationGateRunRecord(
        durationMs = 1,
        outcome = ValidationGateRunOutcome.PASSED,
        cacheMode = ValidationGateCacheMode.FORCED_FULL,
        executedWorkUnits = 1,
        executedChecks = listOf("runtime-engine|compileKotlin"),
        command = "./gradlew check",
        exitCode = 0,
      ),
    ),
    requiredCommand = "./gradlew check",
  ).payload

  @Test
  fun `completed subtask status reports integrity problem when evidence is missing`() {
    val workflowId = "wfl-missing-evidence"
    val manifest = completedManifest(workflowId)
    val service = testGoalRunnerStatusService(
      manifestStore = InMemoryGoalManifestStore(manifest),
      outcomeStore = RecordingOutcomeStore(),
      phaseRecorder = goalRunnerDefaultPhaseRecorder(),
    )
    val status = requireNotNull(
      service.status(GoalRunnerStatusRequest(issueKey = manifest.issueKey, invokedAgentId = "codex")),
    )
    val validation = status.completedSubtaskValidation.single()
    assertEquals(1, validation.subtaskId)
    assertNull(validation.evidence)
    assertTrue(
      validation.integrityProblem?.contains("no runtime-owned validation evidence") == true,
    )
  }

  @Test
  fun `completed subtask status reports integrity problem when required evidence is red`() {
    val workflowId = "wfl-red-evidence"
    val recorder = goalRunnerDefaultPhaseRecorder()
    recorder.ensureWorkflowOpen(workflowId, "goal-red-evidence")
    recorder.recordPhaseState(
      FeatureTaskRuntimePhaseStateRequest(
        workflowId = workflowId,
        phaseId = FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_VALIDATE,
        status = "completed",
        attemptCount = 1,
        resolvedAgentId = "claude",
        finished = true,
        outputArtifact = JsonCodec.mapToJsonString(validValidateOutput(exitCode = 1)),
      ),
    )
    val manifest = completedManifest(workflowId)
    val service = testGoalRunnerStatusService(
      manifestStore = InMemoryGoalManifestStore(manifest),
      outcomeStore = RecordingOutcomeStore(),
      phaseRecorder = recorder,
    )

    val validation = requireNotNull(
      service.status(GoalRunnerStatusRequest(issueKey = manifest.issueKey, invokedAgentId = "codex")),
    ).completedSubtaskValidation.single()

    assertNull(validation.evidence)
    assertTrue(validation.integrityProblem?.contains("exited with 1") == true)
  }

  private fun completedManifest(workflowId: String): DecompositionManifest = manifest(1).copy(
    status = "complete",
    subtasks = listOf(
      DecompositionSubtask(
        id = 1,
        name = "Subtask 1",
        specPath = ".feature-specs/SKILL-56-goal/spec_subtask_1.md",
        dependencies = emptyList(),
        status = "complete",
        workflowId = workflowId,
        commitSha = "sha-1",
      ),
    ),
  )

  private fun validValidateOutput(exitCode: Int = 0): Map<String, Any?> = mapOf(
    SharedPayloadKeys.CONTRACT_VERSION to FEATURE_TASK_RUNTIME_CONTRACT_VERSION,
    SharedPayloadKeys.PHASE_ID to FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_VALIDATE,
    SharedPayloadKeys.STATUS to "completed",
    SharedPayloadKeys.SUMMARY to "Validate output.",
    SharedPayloadKeys.PRODUCED_OUTPUTS to mapOf(
      ValidationEvidencePayloadKeys.VALIDATION_RESULT to mapOf(
        "validation_status" to "passed",
        "checks" to emptyList<Any?>(),
        "repository_checkpoint" to mapOf("fingerprint" to "fixture-checkpoint-1"),
        ValidationEvidencePayloadKeys.VALIDATION_EVIDENCE to mapOf(
          ValidationEvidencePayloadKeys.CONTRACT_VERSION to "0.1",
          ValidationEvidencePayloadKeys.RESULTS to listOf(
            mapOf(
              ValidationEvidencePayloadKeys.COMMAND to "./gradlew check",
              ValidationEvidencePayloadKeys.EXIT_CODE to exitCode,
            ),
          ),
        ),
      ),
    ),
  )
}
