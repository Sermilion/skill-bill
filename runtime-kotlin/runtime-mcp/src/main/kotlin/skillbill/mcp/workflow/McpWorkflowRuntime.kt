package skillbill.mcp.workflow

import skillbill.application.workflow.model.WorkflowFamilyKind
import skillbill.application.workflow.model.WorkflowServiceOpenArgs
import skillbill.application.workflow.model.WorkflowServiceOpenFeatureTaskArgs
import skillbill.application.workflow.model.WorkflowUpdateRequest
import skillbill.application.workflow.openFeatureTask
import skillbill.error.InvalidMcpToolArgumentError
import skillbill.mcp.shared.McpComponent
import skillbill.mcp.shared.componentForLegacyContext
import skillbill.ports.workflow.model.FeatureTaskRouteScope

internal data class McpWorkflowOpenArgs(
  val kind: WorkflowFamilyKind,
  val sessionId: String = "",
  val currentStepId: String? = null,
  val component: McpComponent? = null,
  val context: Any? = null,
  val issueKey: String? = null,
  val repositoryIdentity: String? = null,
  val governedSpecPath: String? = null,
)

internal object McpWorkflowRuntime {
  fun update(kind: WorkflowFamilyKind, request: WorkflowUpdateRequest, context: Any): Map<String, Any?> =
    update(kind, request, componentForLegacyContext(context))

  fun get(kind: WorkflowFamilyKind, workflowId: String, context: Any): Map<String, Any?> =
    get(kind, workflowId, componentForLegacyContext(context))

  fun list(kind: WorkflowFamilyKind, limit: Int = 20, context: Any): Map<String, Any?> =
    list(kind, limit, componentForLegacyContext(context))

  fun latest(kind: WorkflowFamilyKind, context: Any): Map<String, Any?> =
    latest(kind, componentForLegacyContext(context))

  fun resume(kind: WorkflowFamilyKind, workflowId: String, context: Any): Map<String, Any?> =
    resume(kind, workflowId, componentForLegacyContext(context))

  fun continueWorkflow(
    kind: WorkflowFamilyKind,
    workflowId: String,
    context: Any,
    subtaskId: Int? = null,
  ): Map<String, Any?> = continueWorkflow(kind, workflowId, componentForLegacyContext(context), subtaskId)

  fun open(args: McpWorkflowOpenArgs): Map<String, Any?> {
    val runtimeServices = args.component ?: componentForLegacyContext(
      args.context ?: throw InvalidMcpToolArgumentError("workflow_open", "context", "is required"),
    )
    val open = if (args.kind != WorkflowFamilyKind.VERIFY && args.issueKey != null) {
      runtimeServices.workflowService.openFeatureTask(
        WorkflowServiceOpenFeatureTaskArgs(
          kind = args.kind,
          sessionId = args.sessionId,
          currentStepId = args.currentStepId,
          issueKey = args.issueKey,
          repositoryIdentity = args.repositoryIdentity
            ?: throw InvalidMcpToolArgumentError("feature_task_workflow_open", "repository_identity", "is required"),
          governedSpecPath = args.governedSpecPath
            ?: throw InvalidMcpToolArgumentError("feature_task_workflow_open", "governed_spec_path", "is required"),
          routeScope = FeatureTaskRouteScope.STANDALONE,
        ),
      )
    } else {
      runtimeServices.workflowService.open(
        WorkflowServiceOpenArgs(
          kind = args.kind,
          sessionId = args.sessionId,
          currentStepId = args.currentStepId,
          issueKey = args.issueKey,
          repositoryIdentity = args.repositoryIdentity,
          governedSpecPath = args.governedSpecPath,
          routeScope = FeatureTaskRouteScope.STANDALONE,
        ),
      )
    }
    return open.toMcpMap(runtimeServices.workflowService.goalObservabilityEventValidator)
  }

  fun update(
    kind: WorkflowFamilyKind,
    request: WorkflowUpdateRequest,
    component: McpComponent,
  ): Map<String, Any?> {
    val runtimeServices = component
    return runtimeServices.workflowService.update(
      kind,
      request,
    ).toMcpMap()
  }

  fun get(
    kind: WorkflowFamilyKind,
    workflowId: String,
    component: McpComponent,
  ): Map<String, Any?> {
    val runtimeServices = component
    return runtimeServices.workflowService.get(kind, workflowId)
      .toMcpMap(runtimeServices.workflowService.goalObservabilityEventValidator)
  }

  fun list(
    kind: WorkflowFamilyKind,
    limit: Int = 20,
    component: McpComponent,
  ): Map<String, Any?> = component.workflowService.list(kind, limit).toMcpMap()

  fun latest(kind: WorkflowFamilyKind, component: McpComponent): Map<String, Any?> =
    component.workflowService.latest(kind).toMcpMap()

  fun resume(
    kind: WorkflowFamilyKind,
    workflowId: String,
    component: McpComponent,
  ): Map<String, Any?> = component.workflowService.resume(kind, workflowId).toMcpMap()

  fun continueWorkflow(
    kind: WorkflowFamilyKind,
    workflowId: String,
    component: McpComponent,
    subtaskId: Int? = null,
  ): Map<String, Any?> = component.workflowService.continueWorkflow(
    kind,
    workflowId,
    subtaskId = subtaskId,
  ).toMcpMap()
}
