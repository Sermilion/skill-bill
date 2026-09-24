package skillbill.engine

import skillbill.error.shellcontent.InvalidWorkflowStateSchemaError
import skillbill.infrastructure.contracts.workflow.workflow.WorkflowStateSchemaValidator
import skillbill.infrastructure.sqlite.SQLiteDatabaseSessionFactory
import skillbill.model.EnvironmentContext
import skillbill.ports.diagnostics.NoopRuntimeDiagnostics
import skillbill.ports.workflow.model.toSnapshot
import skillbill.ports.workflow.toRecord
import skillbill.workflow.engine.WorkflowEngine
import skillbill.workflow.engine.model.WorkflowStepUpdates
import skillbill.workflow.engine.model.WorkflowUpdateInput
import skillbill.workflow.model.WorkflowStatus
import skillbill.workflow.verify.FeatureVerifyWorkflowDefinition
import java.nio.file.Files
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class WorkflowStateValidationPersistenceTest {
  private val definition = FeatureVerifyWorkflowDefinition.definition
  private val engine = WorkflowEngine()

  @Test
  fun `sqlite updates reject definition-invalid input and direct schema-invalid saves without changing storage`() {
    val root = Files.createTempDirectory("workflow-strict-writes")
    val database =
      SQLiteDatabaseSessionFactory(
        EnvironmentContext(
          dbPathOverride = root.resolve("state.db").toString(),
          environment = emptyMap(),
          userHome = root,
        ),
        Clock.fixed(Instant.parse("2026-06-02T10:00:00Z"), ZoneOffset.UTC),
        NoopRuntimeDiagnostics,
        WorkflowStateSchemaValidator(),
        "test-runtime-version",
      )
    database.transaction { unit ->
      unit.workflowStates.saveFeatureVerifyWorkflow(
        engine.openRecord(definition, "wfv-strict", "", "gather_diff").toRecord(),
      )
    }
    val before = database.read { assertNotNull(it.workflowStates.getFeatureVerifyWorkflow("wfv-strict")) }
    val valid = WorkflowUpdateInput(WorkflowStatus.RUNNING, "gather_diff", null, null, "")
    val invalidInputs =
      listOf(
        valid.copy(currentStepId = "undeclared"),
        valid.copy(workflowStatus = WorkflowStatus.BLOCKED),
        valid.copy(
          stepUpdates =
            WorkflowStepUpdates.from(
              listOf(
                mapOf("step_id" to "undeclared", "status" to "running", "attempt_count" to 1),
              ),
            ),
        ),
      )
    invalidInputs.forEach { input ->
      assertFailsWith<InvalidWorkflowStateSchemaError> {
        database.transaction { unit ->
          val source = assertNotNull(unit.workflowStates.getFeatureVerifyWorkflow("wfv-strict"))
          val updated = engine.updateRecord(definition, source.toSnapshot(), input)
          unit.workflowStates.saveFeatureVerifyWorkflow(updated.toRecord(source))
        }
      }
      assertEquals(before, database.read { it.workflowStates.getFeatureVerifyWorkflow("wfv-strict") })
    }
    val invalidRows =
      listOf(
        before.copy(contractVersion = "999"),
        before.copy(currentStepId = "undeclared"),
        before.copy(workflowStatus = WorkflowStatus.BLOCKED.wireValue),
        before.copy(artifactsJson = "{"),
        before.copy(stepsJson = """[{"step_id":"gather_diff","status":"running","attempt_count":-1}]"""),
        before.copy(stepsJson = """[{"step_id":"gather_diff","status":"running","attempt_count":1,"rogue":true}]"""),
      )
    invalidRows.forEach { invalid ->
      assertFailsWith<InvalidWorkflowStateSchemaError> {
        database.transaction { it.workflowStates.saveFeatureVerifyWorkflow(invalid) }
      }
      assertEquals(before, database.read { it.workflowStates.getFeatureVerifyWorkflow("wfv-strict") })
    }
  }
}
