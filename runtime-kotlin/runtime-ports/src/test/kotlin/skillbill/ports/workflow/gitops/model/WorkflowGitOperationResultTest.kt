package skillbill.ports.workflow.gitops.model

import kotlin.test.Test
import kotlin.test.assertEquals

class WorkflowGitOperationResultTest {
  @Test
  fun `structured result status owns canonical wire mapping`() {
    assertEquals("ok", WorkflowGitOperationStatus.OK.wireValue)
    assertEquals("error", WorkflowGitOperationStatus.ERROR.wireValue)
    assertEquals(WorkflowGitOperationStatus.OK.wireValue, WorkflowGitOperationResult.Ok().wireValue)
    assertEquals(WorkflowGitOperationStatus.ERROR.wireValue, WorkflowGitOperationResult.Failed().wireValue)
    assertEquals(WorkflowGitOperationStatus.OK, WorkflowGitOperationStatus.fromWire("ok"))
    assertEquals(WorkflowGitOperationStatus.ERROR, WorkflowGitOperationStatus.fromWire("error"))
    assertEquals(null, WorkflowGitOperationStatus.fromWire("unknown"))
  }
}
