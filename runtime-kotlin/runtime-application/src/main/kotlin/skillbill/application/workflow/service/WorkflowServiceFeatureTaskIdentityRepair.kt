package skillbill.application.workflow.service
import skillbill.application.workflow.decomposition.issueKey
import skillbill.application.workflow.decomposition.workflowStatus
import skillbill.application.workflow.model.FeatureTaskIdentityRepairArgs
import skillbill.application.workflow.model.WorkflowUpdateResult
import skillbill.application.workflow.persist.buildUpdateOk
import skillbill.application.workflow.workflow.definition
import skillbill.contracts.issuekey.normalizeIssueKey
import skillbill.ports.persistence.UnitOfWork
import skillbill.ports.workflow.get
import skillbill.ports.workflow.model.WorkflowFamily
import skillbill.ports.workflow.save
import skillbill.workflow.engine.WorkflowEngine
import skillbill.workflow.engine.model.WorkflowArtifactPatch
import skillbill.workflow.engine.model.WorkflowStateSnapshot
import skillbill.workflow.engine.model.WorkflowUpdateInput
import skillbill.workflow.engine.model.isTerminalStatus
import skillbill.workflow.model.FeatureTaskExecutionIdentity
import skillbill.workflow.model.FeatureTaskExecutionIdentityPolicy
import skillbill.workflow.model.FeatureTaskRouteScope
import skillbill.workflow.model.FeatureTaskWorkflowMode
import skillbill.workflow.model.workflowStatus
import java.time.Clock
import java.time.ZoneOffset

class WorkflowServiceFeatureTaskIdentityRepair(
  private val engine: WorkflowEngine,
  private val clock: Clock,
) {
  fun repair(args: FeatureTaskIdentityRepairArgs): WorkflowUpdateResult {
    val unitOfWork = args.unitOfWork
    val workflowId = args.workflowId
    val normalizedIssueKey = args.normalizedIssueKey
    val family = WorkflowFamily.TASK_RUNTIME
    val workflowRow = unitOfWork.workflowStates.getFeatureTaskRuntimeWorkflow(workflowId)
      ?: return WorkflowUpdateResult.Error(
        workflowId,
        "Unknown runtime workflow_id '$workflowId'.",
        unitOfWork.dbPath.toString(),
      )
    val existing = requireNotNull(family.get(unitOfWork.workflowStates, workflowId))
    if (family.definition.isTerminalStatus(existing.workflowStatus)) {
      return WorkflowUpdateResult.Error(
        workflowId,
        "Runtime workflow '$workflowId' is already terminal with status '${existing.workflowStatus}'; " +
          "identity repair is only supported for nonterminal workflows.",
        unitOfWork.dbPath.toString(),
      )
    }
    val persistedIssueKey = workflowRow.issueKey?.let(::normalizeIssueKey)?.uppercase()
    if (persistedIssueKey != null && persistedIssueKey != normalizedIssueKey) {
      return WorkflowUpdateResult.Error(
        workflowId,
        "Runtime workflow '$workflowId' belongs to issue '$persistedIssueKey', not '$normalizedIssueKey'.",
        unitOfWork.dbPath.toString(),
      )
    }
    persistIdentity(unitOfWork, args)
    val input = repairInput(existing, args)
    val updated = engine.updateRecord(family.definition, existing, input)
    family.save(unitOfWork.workflowStates, updated)
    return buildUpdateOk(engine, family.definition, updated, input, unitOfWork.dbPath.toString())
  }

  private fun persistIdentity(unitOfWork: UnitOfWork, args: FeatureTaskIdentityRepairArgs) {
    val identity = FeatureTaskExecutionIdentity(
      workflowId = args.workflowId,
      normalizedIssueKey = args.normalizedIssueKey,
      repositoryIdentity = args.repositoryIdentity,
      governedSpecPath = args.governedSpecPath,
      mode = FeatureTaskWorkflowMode.RUNTIME,
      routeScope = FeatureTaskRouteScope.STANDALONE,
    )
    FeatureTaskExecutionIdentityPolicy.validate(identity)
    unitOfWork.workflowStates.saveFeatureTaskExecutionIdentity(identity)
  }

  private fun repairInput(existing: WorkflowStateSnapshot, args: FeatureTaskIdentityRepairArgs): WorkflowUpdateInput =
    WorkflowUpdateInput(
      workflowStatus = existing.workflowStatus,
      currentStepId = existing.currentStepId.orEmpty(),
      stepUpdates = null,
      artifactsPatch = WorkflowArtifactPatch.from(
        mapOf(
          FEATURE_TASK_RUNTIME_IDENTITY_REPAIR_ARTIFACT_KEY to mapOf(
            "reason" to args.normalizedReason,
            "repaired_at" to clock.instant().atOffset(ZoneOffset.UTC).toString(),
            "repository_identity" to args.repositoryIdentity,
            "governed_spec_path" to args.governedSpecPath,
          ),
        ),
      ),
      sessionId = "",
    )
}
