package skillbill.infrastructure.fs

import me.tatarka.inject.annotations.Inject
import skillbill.infrastructure.fs.contracts.workflow.WorkflowStateSchemaValidator
import skillbill.workflow.engine.WorkflowSnapshotValidator
import skillbill.workflow.engine.model.WorkflowStateSnapshot

@Inject
class WorkflowSnapshotValidatorInfraAdapter : WorkflowSnapshotValidator {
  private val delegate = WorkflowStateSchemaValidator()

  override fun validate(snapshot: WorkflowStateSnapshot, slug: String) {
    delegate.validate(WorkflowStateSnapshotWireMapper.wireMap(snapshot), slug)
  }
}
