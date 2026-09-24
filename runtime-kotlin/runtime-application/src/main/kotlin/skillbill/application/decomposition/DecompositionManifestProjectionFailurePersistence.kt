package skillbill.application.decomposition

import skillbill.application.decomposition.model.RetryDecompositionManifestProjectionArgs
import skillbill.ports.persistence.UnitOfWork
import skillbill.ports.workflow.decomposition.DecompositionManifestProjectionFailurePersistence
import skillbill.ports.workflow.decomposition.clearDecompositionManifestProjectionFailure
import skillbill.ports.workflow.decomposition.persistDecompositionManifestProjectionFailure
import skillbill.ports.workflow.get
import skillbill.ports.workflow.model.WorkflowFamily
import skillbill.workflow.decomposition.runtime.model.DecompositionManifestProjectionOutcome

internal fun retryDecompositionManifestProjectionFromAuthoritativeState(
  args: RetryDecompositionManifestProjectionArgs,
): DecompositionManifestProjectionOutcome {
  val database = args.database
  val engine = args.engine
  val decompositionManifestWriter = args.decompositionManifestWriter
  val decompositionManifestValidator = args.decompositionManifestValidator
  val decompositionManifestStore = args.decompositionManifestStore
  val repoRoot = args.repoRoot
  val workflowId = args.workflowId
  val artifacts =
    database.read { unitOfWork ->
      WorkflowFamily.TASK_RUNTIME.get(unitOfWork.workflowStates, workflowId)?.artifacts
    } ?: return DecompositionManifestProjectionOutcome.Absent
  val outcome =
    decompositionManifestWriter.writeProjectionFromWorkflowState(
      repoRoot = repoRoot,
      artifacts = artifacts,
      validator = decompositionManifestValidator,
      fileStore = decompositionManifestStore,
    )
  if (outcome is DecompositionManifestProjectionOutcome.Written) {
    val cleared =
      database.transaction { unitOfWork ->
        clearDecompositionManifestProjectionFailure(engine, unitOfWork, workflowId)
      }
    if (cleared == DecompositionManifestProjectionFailurePersistence.OWNER_ABSENT) {
      return DecompositionManifestProjectionOutcome.Absent
    }
  }
  return outcome
}
