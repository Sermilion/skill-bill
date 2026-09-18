package skillbill.mcp.shared

import skillbill.application.learning.LearningService
import skillbill.application.review.ReviewService
import skillbill.application.system.SystemService
import skillbill.application.telemetry.LifecycleTelemetryService
import skillbill.application.telemetry.TelemetryService
import skillbill.application.updatecheck.UpdateCheckService
import skillbill.application.workflow.WorkflowService
import skillbill.engine.featuretask.FeatureTaskPhaseSettlementService

internal class McpRuntimeServices(private val component: McpComponent) {
  val learningService: LearningService get() = component.learningService
  val lifecycleTelemetryService: LifecycleTelemetryService get() = component.lifecycleTelemetryService
  val telemetryService: TelemetryService get() = component.telemetryService
  val reviewService: ReviewService get() = component.reviewService
  val systemService: SystemService get() = component.systemService
  val workflowService: WorkflowService get() = component.workflowService
  val updateCheckService: UpdateCheckService get() = component.updateCheckService
  val featureTaskPhaseSettlementService: FeatureTaskPhaseSettlementService
    get() = component.featureTaskPhaseSettlementService
}

internal fun services(context: McpRuntimeContext): McpRuntimeServices = McpRuntimeServices(context.mcpComponent())
