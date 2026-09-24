package skillbill.application.workflow.decomposition
import skillbill.error.shellcontent.LegacyProseWorkflowError
import skillbill.ports.workflow.WorkflowStateRepository
import skillbill.ports.workflow.model.WorkflowStateRecord
import skillbill.ports.workflow.model.toSnapshot
import skillbill.ports.workflow.decomposition.listFeatureTaskWorkflowsForParentDiscovery
import skillbill.ports.workflow.decomposition.DecompositionManifestValidator
import skillbill.workflow.decomposition.model.DecompositionManifest
import skillbill.workflow.decomposition.runtime.isActiveGoalRuntime
import skillbill.workflow.decomposition.runtime.decompositionRuntime
import skillbill.workflow.decomposition.runtime.hasDecompositionPlan
import skillbill.workflow.decomposition.runtime.isGoalContinuationChildWorkflow
import skillbill.workflow.model.FeatureTaskWorkflowMode
import skillbill.workflow.model.WorkflowStatus
import skillbill.workflow.model.workflowStatus

val IMPLEMENT_TERMINAL_STATUSES: Set<WorkflowStatus> = WorkflowStatus.terminalStatuses

fun WorkflowStateRecord.requireRuntimeModeForEngineWrite() {
  if (mode != FeatureTaskWorkflowMode.RUNTIME) {
    throw LegacyProseWorkflowError(workflowId, issueKey)
  }
}


