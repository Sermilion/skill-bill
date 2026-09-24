package skillbill.ports.workflow

import skillbill.workflow.engine.model.WorkflowStateSnapshot

interface WorkflowSnapshotValidator {
  fun validate(snapshot: WorkflowStateSnapshot, slug: String)
}
