package skillbill.infrastructure.fs.scaffold

import skillbill.error.InvalidWorkflowStateSchemaError
import skillbill.infrastructure.fs.WorkflowStateSnapshotWireMapper
import skillbill.infrastructure.fs.contracts.workflow.WorkflowStateSchemaValidator
import skillbill.workflow.engine.WorkflowEngine
import skillbill.workflow.engine.model.WorkflowDefinition
import skillbill.workflow.engine.model.WorkflowStepUpdates
import skillbill.workflow.engine.model.WorkflowUpdateInput
import skillbill.workflow.model.WorkflowStatus
import skillbill.workflow.taskruntime.FeatureTaskRuntimePhaseWorkflowDefinition
import skillbill.workflow.verify.FeatureVerifyWorkflowDefinition
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class WorkflowStateSchemaValidatesExistingWorkflowsTest {

  private val validator = WorkflowStateSchemaValidator()
  private val engine: WorkflowEngine = WorkflowEngine(WorkflowStateSchemaValidator())

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

  @Test
  fun `unknown durable workflow status token raises the typed schema error`() {
    val error = assertFailsWith<InvalidWorkflowStateSchemaError> {
      WorkflowStateSnapshotWireMapper.workflowStatusFromWire("unknown", "workflow_status")
    }

    assertEquals("Workflow state workflow_status has unsupported value 'unknown'.", error.message)
  }

  private fun validateEverySnapshotPerStep(definition: WorkflowDefinition) {
    definition.stepIds.forEach { activeStepId ->
      val record = engine.openRecord(
        definition = definition,
        workflowId = "wftr-19700101-000000-aaaa",
        sessionId = "",
        currentStepId = activeStepId,
      )

      validator.validate(record, definition.workflowName)
      engine.snapshotView(definition, record)
      engine.summaryView(definition, record)
      engine.resumeView(definition, record)
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
          workflowStatus = WorkflowStatus.fromWire(status)
            ?: error("Unsupported workflow status '$status'"),
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
      validator.validate(withFinishedAt, definition.workflowName)
      engine.snapshotView(definition, withFinishedAt)
    }
  }
}
