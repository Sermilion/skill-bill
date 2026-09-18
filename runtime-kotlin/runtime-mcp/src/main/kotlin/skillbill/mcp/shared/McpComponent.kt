package skillbill.mcp.shared

import me.tatarka.inject.annotations.Component
import skillbill.application.learning.LearningService
import skillbill.application.review.ReviewService
import skillbill.application.system.SystemService
import skillbill.application.telemetry.LifecycleTelemetryService
import skillbill.application.telemetry.TelemetryService
import skillbill.application.updatecheck.UpdateCheckService
import skillbill.application.workflow.WorkflowService
import skillbill.di.RuntimeComponent
import skillbill.engine.featuretask.FeatureTaskPhaseSettlementService
import skillbill.ports.db.DatabaseSessionFactory
import skillbill.ports.diagnostics.RuntimeDiagnostics
import skillbill.ports.time.RuntimeClock

@Component
internal abstract class McpComponent(
  @Component val runtimeComponent: RuntimeComponent,
) {
  abstract val learningService: LearningService
  abstract val lifecycleTelemetryService: LifecycleTelemetryService
  abstract val telemetryService: TelemetryService
  abstract val reviewService: ReviewService
  abstract val systemService: SystemService
  abstract val workflowService: WorkflowService
  abstract val updateCheckService: UpdateCheckService
  abstract val featureTaskPhaseSettlementService: FeatureTaskPhaseSettlementService
  abstract val clock: RuntimeClock
  abstract val runtimeDiagnostics: RuntimeDiagnostics
  abstract val databaseSessionFactory: DatabaseSessionFactory
}
