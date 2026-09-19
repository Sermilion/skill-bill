package skillbill.engine.goalrunner.status
import skillbill.contracts.JsonCodec
import skillbill.contracts.SharedPayloadKeys
import skillbill.contracts.workflow.featuretask.FEATURE_TASK_RUNTIME_CONTRACT_VERSION
import skillbill.contracts.workflow.identity.evidence.ValidationEvidencePayloadKeys
import skillbill.engine.featuretask.model.phase.FeatureTaskRuntimePhaseStateRequest
import skillbill.engine.goalrunner.InMemoryGoalManifestStore
import skillbill.engine.goalrunner.RecordingOutcomeStore
import skillbill.engine.goalrunner.execution.core.goalRunnerDefaultPhaseRecorder
import skillbill.engine.goalrunner.execution.core.testGoalRunnerStatusService
import skillbill.engine.goalrunner.manifest
import skillbill.engine.goalrunner.model.GoalRunnerStatusRequest
import skillbill.workflow.decomposition.model.DecompositionManifest
import skillbill.workflow.decomposition.model.DecompositionSubtask
import skillbill.workflow.taskruntime.phase.task.FeatureTaskRuntimePhaseWorkflowDefinition
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
class GoalRunnerValidationEvidenceProjectionTest {
  @Test
  fun `completed subtask status reports the boolean signal and rejects missing or false results`() {
    listOf(true to "completed", false to "completed", null to "completed", "true" to "completed", true to "failed")
      .forEach { (signal, status) ->
        val workflowId = "wfl-validation-result"
        val recorder = goalRunnerDefaultPhaseRecorder()
        recorder.ensureWorkflowOpen(workflowId, "goal-validation-result")
        val produced = buildMap<String, Any?> {
          put(SharedPayloadKeys.VALUE, "Project validation result.")
          if (signal != null) put(ValidationEvidencePayloadKeys.VALIDATION_PASSED, signal)
        }
        recorder.recordPhaseState(
          FeatureTaskRuntimePhaseStateRequest(
            workflowId = workflowId,
            phaseId = FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_VALIDATE,
            status = "completed",
            attemptCount = 1,
            resolvedAgentId = "claude",
            finished = true,
            outputArtifact = JsonCodec.mapToJsonString(
              mapOf(
                SharedPayloadKeys.CONTRACT_VERSION to FEATURE_TASK_RUNTIME_CONTRACT_VERSION,
                SharedPayloadKeys.PHASE_ID to FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_VALIDATE,
                SharedPayloadKeys.STATUS to status,
                SharedPayloadKeys.SUMMARY to "Validate output.",
                SharedPayloadKeys.PRODUCED_OUTPUTS to produced,
              ),
            ),
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
        assertEquals(signal as? Boolean, validation.validationPassed)
        assertEquals(signal != true || status != "completed", validation.integrityProblem != null)
        assertNull(validation.evidence)
        assertNull(validation.gateExecutionEvidence)
        val wire = JsonCodec.anyToStringAnyMap(validation.toStatusWire())!!
        assertEquals(signal as? Boolean, wire[ValidationEvidencePayloadKeys.VALIDATION_PASSED])
      }
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
}
