package skillbill.di.workflow

import me.tatarka.inject.annotations.Provides
import skillbill.infrastructure.contracts.workflow.WorkflowStateSchemaValidator
import skillbill.infrastructure.contracts.workflow.decomposition.DecompositionManifestSchemaValidator
import skillbill.infrastructure.contracts.workflow.goal.IdeStatusSchemaValidator
import skillbill.ports.idestatus.IdeStatusValidator
import skillbill.ports.workflow.WorkflowSnapshotValidator
import skillbill.ports.workflow.decomposition.DecompositionManifestValidator

internal interface RuntimeWorkflowValidatorProvides {
  @Provides
  fun decompositionManifestValidator(): DecompositionManifestValidator = DecompositionManifestSchemaValidator()

  @Provides
  fun workflowSnapshotValidator(): WorkflowSnapshotValidator = WorkflowStateSchemaValidator()

  @Provides
  fun ideStatusValidator(): IdeStatusValidator = IdeStatusSchemaValidator()
}
