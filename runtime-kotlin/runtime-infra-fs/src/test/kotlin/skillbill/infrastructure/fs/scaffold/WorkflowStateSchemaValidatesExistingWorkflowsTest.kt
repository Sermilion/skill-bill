package skillbill.infrastructure.fs.scaffold

import skillbill.contracts.SharedPayloadKeys
import skillbill.contracts.workflow.WorkflowContracts
import skillbill.contracts.workflow.WorkflowWirePayloadKeys
import skillbill.infrastructure.fs.WorkflowSnapshotValidatorInfraAdapter
import skillbill.infrastructure.fs.contracts.workflow.WorkflowStateSchemaValidator
import skillbill.workflow.engine.WorkflowEngine
import skillbill.workflow.engine.model.WorkflowDefinition
import skillbill.workflow.engine.model.WorkflowSnapshotView
import skillbill.workflow.engine.model.WorkflowStepUpdates
import skillbill.workflow.engine.model.WorkflowUpdateInput
import skillbill.workflow.taskruntime.FeatureTaskRuntimePhaseWorkflowDefinition
import skillbill.workflow.verify.FeatureVerifyWorkflowDefinition
import kotlin.test.Test

class WorkflowStateSchemaValidatesExistingWorkflowsTest {

  private val validator = WorkflowStateSchemaValidator()
  private val engine: WorkflowEngine = WorkflowEngine(WorkflowSnapshotValidatorInfraAdapter())

  @Test
  fun `every feature-task step snapshot from the engine validates clean`() {
    validateEverySnapshotPerStep(FeatureTaskRuntimePhaseWorkflowDefinition.definition)
  }

  @Test
  fun `every feature-verify step snapshot from the engine validates clean`() {
    validateEverySnapshotPerStep(FeatureVerifyWorkflowDefinition.definition)
  }

  @Test
  fun `every feature-task workflow_status snapshot from the engine validates clean`() {
    validateEveryWorkflowStatus(FeatureTaskRuntimePhaseWorkflowDefinition.definition)
  }

  @Test
  fun `every feature-verify workflow_status snapshot from the engine validates clean`() {
    validateEveryWorkflowStatus(FeatureVerifyWorkflowDefinition.definition)
  }

  private fun validateEverySnapshotPerStep(definition: WorkflowDefinition) {
    definition.stepIds.forEach { activeStepId ->
      val record = engine.openRecord(
        definition = definition,
        workflowId = "wftr-19700101-000000-aaaa",
        sessionId = "",
        currentStepId = activeStepId,
      )

      val snapshotView = engine.snapshotView(definition, record)
      val full = snapshotMap(snapshotView)
      validator.validate(full, definition.workflowName)

      engine.summaryView(definition, record)

      val resumed = engine.resumeView(definition, record).let { resume ->
        snapshotMap(resume.snapshot)
      }
      validator.validate(resumed.filterKeys { it in SNAPSHOT_KEYS }, definition.workflowName)
    }
  }

  private fun validateEveryWorkflowStatus(definition: WorkflowDefinition) {
    definition.workflowStatuses.forEach { status ->
      val opened = engine.openRecord(
        definition = definition,
        workflowId = "wftr-19700101-000000-aaaa",
        sessionId = "",
        currentStepId = definition.defaultInitialStepId,
      )
      val terminal = status in definition.terminalStatuses
      val stepUpdates = if (terminal) {
        definition.stepIds.map { stepId ->
          linkedMapOf<String, Any?>(
            "step_id" to stepId,
            "status" to "completed",
            "attempt_count" to 1,
          )
        }
      } else {
        null
      }
      val updated = engine.updateRecord(
        definition = definition,
        existing = opened,
        input = WorkflowUpdateInput(
          workflowStatus = status,
          currentStepId = definition.defaultInitialStepId,
          stepUpdates = stepUpdates?.let(WorkflowStepUpdates::from),
          artifactsPatch = null,
          sessionId = "",
        ),
      )
      val withFinishedAt = if (terminal) {
        updated.copy(finishedAt = "1970-01-01T00:00:00Z")
      } else {
        updated
      }
      val payload = snapshotMap(engine.snapshotView(definition, withFinishedAt))
      validator.validate(payload, definition.workflowName)
    }
  }

  private companion object {

    private val SNAPSHOT_KEYS: Set<String> = setOf(
      "workflow_id",
      "session_id",
      "workflow_name",
      "mode",
      "contract_version",
      "workflow_status",
      "current_step_id",
      "steps",
      "artifacts",
      "started_at",
      "updated_at",
      "finished_at",
    )
  }

  private fun snapshotMap(view: WorkflowSnapshotView): Map<String, Any?> = WorkflowContracts.fullWorkflowPayload(
    linkedMapOf(
      SharedPayloadKeys.WORKFLOW_ID to view.workflowId,
      WorkflowWirePayloadKeys.SESSION_ID to view.sessionId,
      WorkflowWirePayloadKeys.WORKFLOW_NAME to view.workflowName,
      WorkflowWirePayloadKeys.MODE to view.mode,
      SharedPayloadKeys.CONTRACT_VERSION to view.contractVersion,
      WorkflowWirePayloadKeys.WORKFLOW_STATUS to view.workflowStatus,
      WorkflowWirePayloadKeys.CURRENT_STEP_ID to view.currentStepId,
      WorkflowWirePayloadKeys.STEPS to view.steps.map { step ->
        linkedMapOf(
          SharedPayloadKeys.STEP_ID to step.stepId,
          SharedPayloadKeys.STATUS to step.status,
          WorkflowWirePayloadKeys.ATTEMPT_COUNT to step.attemptCount,
        )
      },
      WorkflowWirePayloadKeys.ARTIFACTS to view.artifacts,
      WorkflowWirePayloadKeys.STARTED_AT to view.startedAt,
      WorkflowWirePayloadKeys.UPDATED_AT to view.updatedAt,
      WorkflowWirePayloadKeys.FINISHED_AT to view.finishedAt,
    ),
  )
}
