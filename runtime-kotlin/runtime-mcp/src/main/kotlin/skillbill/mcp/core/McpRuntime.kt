package skillbill.mcp.core

import skillbill.application.learning.toLearningResolveContract
import skillbill.application.review.service.toReviewFinishedTelemetryPayload
import skillbill.contracts.mcp.McpLearningsSkippedContract
import skillbill.contracts.mcp.McpOrchestratedPayloadContract
import skillbill.contracts.mcp.McpReviewImportSkippedContract
import skillbill.contracts.mcp.McpTriageSkippedContract
import skillbill.contracts.system.UpdateCheckContract
import skillbill.mcp.scaffold.McpScaffoldRuntime
import skillbill.mcp.shared.McpComponent
import skillbill.mcp.shared.componentForLegacyContext
import skillbill.mcp.shared.toMcpMap
internal object McpRuntime {
  fun importReview(reviewText: String, orchestrated: Boolean = false, context: Any): Map<String, Any?> =
    importReview(reviewText, orchestrated, componentForLegacyContext(context))

  fun triageFindings(
    reviewRunId: String,
    decisions: List<String>,
    orchestrated: Boolean = false,
    context: Any,
  ): Map<String, Any?> = triageFindings(reviewRunId, decisions, orchestrated, componentForLegacyContext(context))

  fun resolveLearnings(
    repo: String? = null,
    skill: String? = null,
    reviewSessionId: String? = null,
    context: Any,
  ): Map<String, Any?> = resolveLearnings(repo, skill, reviewSessionId, componentForLegacyContext(context))

  fun reviewStats(reviewRunId: String? = null, context: Any): Map<String, Any?> =
    reviewStats(reviewRunId, componentForLegacyContext(context))

  fun featureVerifyStats(context: Any): Map<String, Any?> = featureVerifyStats(componentForLegacyContext(context))

  fun goalStats(context: Any): Map<String, Any?> = goalStats(componentForLegacyContext(context))

  fun version(context: Any): Map<String, Any?> = version(componentForLegacyContext(context))

  fun doctor(context: Any): Map<String, Any?> = doctor(componentForLegacyContext(context))

  fun updateCheck(context: Any): Map<String, Any?> = updateCheck(componentForLegacyContext(context))

  fun newSkillScaffold(
    payload: Map<String, Any?>,
    dryRun: Boolean = false,
    orchestrated: Boolean = false,
    context: Any,
  ): Map<String, Any?> = newSkillScaffold(payload, dryRun, orchestrated, componentForLegacyContext(context))

  fun importReview(reviewText: String, orchestrated: Boolean = false, component: McpComponent): Map<String, Any?> {
    if (!component.telemetryService.isEnabled()) {
      val preview = component.reviewService.previewImport("-", stdinText = reviewText)
      return McpReviewImportSkippedContract(
        reason = "telemetry is disabled",
        reviewRunId = preview.reviewRunId,
        findingCount = preview.findingCount,
      ).toPayload()
    }
    val importResult =
      component.reviewService
        .importReview(
          "-",
          finishZeroFindingTelemetry = !orchestrated,
          stdinText = reviewText,
        )
    val payload = importResult.toMcpMap().toMutableMap()
    val result = if (orchestrated) {
      val reviewRunId = importResult.preview.reviewRunId
      component.reviewService.markOrchestrated(reviewRunId)
      val telemetryPayload =
        if (importResult.preview.findingCount == 0) {
          component.reviewService.reviewFinishedTelemetryPayload(reviewRunId)
            ?.toReviewFinishedTelemetryPayload()
            ?.toPayload()
        } else {
          null
        }
      McpOrchestratedPayloadContract(basePayload = payload, telemetryPayload = telemetryPayload).toPayload()
    } else {
      payload
    }
    component.telemetryService.autoSync()
    return result
  }

  fun triageFindings(
    reviewRunId: String,
    decisions: List<String>,
    orchestrated: Boolean = false,
    component: McpComponent,
  ): Map<String, Any?> {
    if (!component.telemetryService.isEnabled()) {
      return McpTriageSkippedContract(reason = "telemetry is disabled", reviewRunId = reviewRunId).toPayload()
    }
    if (orchestrated) {
      component.reviewService.markOrchestrated(reviewRunId)
    }
    val result =
      component.reviewService.triage(
        reviewRunId,
        decisions,
        listOnly = false,
        listWhenNoDecisions = false,
      )
    val payload = if (orchestrated) {
      McpOrchestratedPayloadContract(
        basePayload = result.toMcpMap(),
        telemetryPayload = result.telemetry?.toReviewFinishedTelemetryPayload()?.toPayload(),
      ).toPayload()
    } else {
      result.toMcpMap()
    }
    component.telemetryService.autoSync()
    return payload
  }

  fun resolveLearnings(
    repo: String? = null,
    skill: String? = null,
    reviewSessionId: String? = null,
    component: McpComponent,
  ): Map<String, Any?> {
    if (!component.telemetryService.isEnabled()) {
      return McpLearningsSkippedContract(reason = "telemetry is disabled").toPayload()
    }
    return component.learningService.resolve(repo, skill, reviewSessionId).toLearningResolveContract().toPayload()
  }

  fun reviewStats(reviewRunId: String? = null, component: McpComponent): Map<String, Any?> =
    component.reviewService.reviewStats(reviewRunId).toMcpMap()

  fun featureVerifyStats(component: McpComponent): Map<String, Any?> =
    component.reviewService.featureVerifyStats().toMcpMap()

  fun goalStats(component: McpComponent): Map<String, Any?> = component.reviewService.goalStats().toMcpMap()

  fun version(component: McpComponent): Map<String, Any?> = component.systemService.version().toPayload()

  fun doctor(component: McpComponent): Map<String, Any?> = component.systemService.doctor().toPayload()

  fun updateCheck(component: McpComponent): Map<String, Any?> {
    val result = component.updateCheckService.check(includePrereleases = false)
    return UpdateCheckContract(
      status = result.status.wireName,
      installedVersion = result.installedVersion,
      latestVersion = result.latestVersion,
      recommendedInstallCommand = result.recommendedInstallCommand,
      reason = result.reason,
      releaseNotes = result.releaseNotes,
    ).toPayload()
  }

  fun newSkillScaffold(
    payload: Map<String, Any?>,
    dryRun: Boolean = false,
    orchestrated: Boolean = false,
    component: McpComponent,
  ): Map<String, Any?> = McpScaffoldRuntime.newSkillScaffold(
    payload = payload,
    dryRun = dryRun,
    orchestrated = orchestrated,
    component = component,
  )
}
