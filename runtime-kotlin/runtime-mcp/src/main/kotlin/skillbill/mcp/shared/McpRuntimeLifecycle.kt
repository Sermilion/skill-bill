package skillbill.mcp.shared

import skillbill.application.telemetry.model.FeatureVerifyFinishedRequest
import skillbill.application.telemetry.model.FeatureVerifyStartedRequest
import skillbill.application.telemetry.model.PrDescriptionGeneratedRequest
import skillbill.application.telemetry.model.QualityCheckFinishedRequest
import skillbill.application.telemetry.model.QualityCheckStartedRequest
import skillbill.mcp.telemetry.toMcpMap
import skillbill.ports.diagnostics.RuntimeDiagnostics
import skillbill.telemetry.model.RemoteStatsRequest

internal object McpRuntimeLifecycle {
  fun qualityCheckStarted(
    request: QualityCheckStartedRequest,
    context: Any,
  ): Map<String, Any?> = qualityCheckStarted(request, componentForLegacyContext(context))

  fun qualityCheckFinished(
    request: QualityCheckFinishedRequest,
    context: Any,
  ): Map<String, Any?> = qualityCheckFinished(request, componentForLegacyContext(context))

  fun featureVerifyStarted(
    request: FeatureVerifyStartedRequest,
    context: Any,
  ): Map<String, Any?> = featureVerifyStarted(request, componentForLegacyContext(context))

  fun featureVerifyFinished(
    request: FeatureVerifyFinishedRequest,
    context: Any,
  ): Map<String, Any?> = featureVerifyFinished(request, componentForLegacyContext(context))

  fun prDescriptionGenerated(
    request: PrDescriptionGeneratedRequest,
    context: Any,
  ): Map<String, Any?> = prDescriptionGenerated(request, componentForLegacyContext(context))

  fun telemetryRemoteStats(
    request: RemoteStatsRequest,
    context: Any,
  ): Map<String, Any?> = telemetryRemoteStats(request, componentForLegacyContext(context))

  fun telemetryProxyCapabilities(context: Any): Map<String, Any?> =
    telemetryProxyCapabilities(componentForLegacyContext(context))

  fun qualityCheckStarted(
    request: QualityCheckStartedRequest,
    component: McpComponent,
  ): Map<String, Any?> =
    withAutoSync(component) {
      it.lifecycleTelemetryService.qualityCheckStarted(request).toPayload()
    }

  fun qualityCheckFinished(
    request: QualityCheckFinishedRequest,
    component: McpComponent,
  ): Map<String, Any?> =
    withAutoSync(component) {
      it.lifecycleTelemetryService.qualityCheckFinished(request).toPayload()
    }

  fun featureVerifyStarted(
    request: FeatureVerifyStartedRequest,
    component: McpComponent,
  ): Map<String, Any?> =
    withAutoSync(component) {
      it.lifecycleTelemetryService.featureVerifyStarted(request).toPayload()
    }

  fun featureVerifyFinished(
    request: FeatureVerifyFinishedRequest,
    component: McpComponent,
  ): Map<String, Any?> =
    withAutoSync(component) {
      it.lifecycleTelemetryService.featureVerifyFinished(request).toPayload()
    }

  fun prDescriptionGenerated(
    request: PrDescriptionGeneratedRequest,
    component: McpComponent,
  ): Map<String, Any?> =
    withAutoSync(component) {
      it.lifecycleTelemetryService.prDescriptionGenerated(request).toPayload()
    }

  fun telemetryRemoteStats(
    request: RemoteStatsRequest,
    component: McpComponent,
  ): Map<String, Any?> = component.telemetryService.remoteStats(request).toMcpMap()

  fun telemetryProxyCapabilities(component: McpComponent): Map<String, Any?> =
    component.telemetryService.capabilities().toMcpMap()

  fun captureException(
    workflowPhase: String,
    error: Exception,
    component: McpComponent,
  ) {
    recordCaptureFailure(
      workflowPhase = workflowPhase,
      capture = { component.telemetryService.captureException(workflowPhase, error) },
      diagnostics = component.runtimeDiagnostics,
    )
  }

  private fun withAutoSync(
    component: McpComponent,
    block: (McpComponent) -> Map<String, Any?>,
  ): Map<String, Any?> {
    val payload = block(component)
    component.telemetryService.autoSync()
    return payload
  }
}

internal fun recordCaptureFailure(
  workflowPhase: String,
  capture: () -> Unit,
  diagnostics: RuntimeDiagnostics,
) {
  runCatching { capture() }.onFailure { captureError ->
    val record = diagnostics::error
    record("MCP telemetry capture failed for tool '$workflowPhase'.", captureError)
  }
}
