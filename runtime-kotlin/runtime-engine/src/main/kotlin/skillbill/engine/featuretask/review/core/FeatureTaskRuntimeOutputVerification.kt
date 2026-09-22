package skillbill.engine.featuretask.review.core

import skillbill.contracts.JsonCodec
import skillbill.contracts.SharedPayloadKeys
import skillbill.contracts.review.ReviewFindingPayloadKeys
import skillbill.goalrunner.subtaskreview.FeatureTaskRuntimeVerificationSignalKeys
import skillbill.review.finding.ReviewFindingActionability
import skillbill.review.model.ReviewClaimVerdict
import skillbill.review.model.ReviewScopeDisposition
import skillbill.workflow.taskruntime.artifact.FeatureTaskRuntimeWorkflowArtifactMap
import skillbill.workflow.taskruntime.feature.FeatureTaskRuntimeAuditRemainingAcInterpretation
import skillbill.workflow.taskruntime.model.audit.FeatureTaskRuntimeAuditRemainingAcResult
import skillbill.workflow.taskruntime.model.review.FeatureTaskRuntimeReviewFinding
import skillbill.workflow.taskruntime.model.review.FeatureTaskRuntimeReviewSeverity
import skillbill.workflow.taskruntime.model.review.FeatureTaskRuntimeReviewVerdict
import skillbill.workflow.taskruntime.model.validation.FeatureTaskRuntimeFindingVerificationDisposition
import skillbill.workflow.taskruntime.model.validation.FeatureTaskRuntimeFindingVerificationVerdict
import skillbill.workflow.taskruntime.model.validation.FeatureTaskRuntimeVerdict
import skillbill.workflow.taskruntime.phase.task.FeatureTaskRuntimePhaseWorkflowDefinition

object FeatureTaskRuntimeOutputVerification {
  internal fun verdictFor(
    phaseId: String,
    outputObject: FeatureTaskRuntimeWorkflowArtifactMap?,
  ): FeatureTaskRuntimeVerdict {
    val wireVerdict =
      (outputObject?.get(SharedPayloadKeys.VERDICT) as? String)
        ?.takeIf(String::isNotBlank)
        ?.let { value -> FeatureTaskRuntimeVerdict.rejectRemovedVerdict(value, "phase output verdict") }
    return when (phaseId) {
      FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_REVIEW -> reviewVerdict(outputObject, wireVerdict)
      FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_VERIFY_FINDINGS ->
        findingVerificationVerdict(wireVerdict)
      FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_AUDIT -> auditVerdict(wireVerdict, outputObject)
      else -> wireVerdict ?: FeatureTaskRuntimeVerdict.ADVANCE
    }
  }

  internal fun dispositionsFrom(
    outputObject: FeatureTaskRuntimeWorkflowArtifactMap?,
  ): List<FeatureTaskRuntimeFindingVerificationDisposition> =
    findingVerificationVerdictFrom(outputObject)?.dispositions.orEmpty()

  internal fun verifiedFindingDispositions(
    outputObject: FeatureTaskRuntimeWorkflowArtifactMap?,
  ): List<FeatureTaskRuntimeFindingVerificationDisposition> =
    findingVerificationVerdictFrom(outputObject)?.verifiedDispositions.orEmpty()

  internal fun rejectedFindingDispositions(
    outputObject: FeatureTaskRuntimeWorkflowArtifactMap?,
  ): List<FeatureTaskRuntimeFindingVerificationDisposition> =
    findingVerificationVerdictFrom(outputObject)?.rejectedDispositions.orEmpty()

  internal fun unresolvedReviewFindings(
    outputObject: FeatureTaskRuntimeWorkflowArtifactMap?,
  ): List<FeatureTaskRuntimeReviewFinding> = reviewVerdictFrom(outputObject)?.unresolvedFindings.orEmpty()

  internal fun auditProseValue(outputObject: FeatureTaskRuntimeWorkflowArtifactMap?): String? =
    outputObject?.get(SharedPayloadKeys.PRODUCED_OUTPUTS)
      ?.let(JsonCodec::anyToStringAnyMap)
      ?.get(SharedPayloadKeys.VALUE)
      ?.toString()
      ?.takeIf(String::isNotBlank)
}

private fun findingVerificationVerdict(wireVerdict: FeatureTaskRuntimeVerdict?): FeatureTaskRuntimeVerdict =
  requireNotNull(wireVerdict) {
    "verify_findings phase output is missing verdict."
  }

private fun findingVerificationVerdictFrom(
  outputObject: FeatureTaskRuntimeWorkflowArtifactMap?,
): FeatureTaskRuntimeFindingVerificationVerdict? {
  val dispositionsRaw =
    outputObject?.get(SharedPayloadKeys.PRODUCED_OUTPUTS)
      ?.let(JsonCodec::anyToStringAnyMap)
      ?.get(FeatureTaskRuntimeVerificationSignalKeys.FINDINGS_VERIFICATION_DISPOSITIONS) as? List<*>
      ?: return null
  val dispositions =
    FeatureTaskRuntimeFindingVerificationDisposition.parseList(
      dispositionsRaw,
      "produced_outputs.${FeatureTaskRuntimeVerificationSignalKeys.FINDINGS_VERIFICATION_DISPOSITIONS}",
    )
  return FeatureTaskRuntimeFindingVerificationVerdict(dispositions)
}

private fun reviewVerdict(
  outputObject: FeatureTaskRuntimeWorkflowArtifactMap?,
  wireVerdict: FeatureTaskRuntimeVerdict?,
): FeatureTaskRuntimeVerdict {
  val reviewVerdict = reviewVerdictFrom(outputObject)
  return reviewVerdict?.verdict ?: wireVerdict ?: FeatureTaskRuntimeVerdict.ADVANCE
}

private fun auditVerdict(
  wireVerdict: FeatureTaskRuntimeVerdict?,
  outputObject: FeatureTaskRuntimeWorkflowArtifactMap?,
): FeatureTaskRuntimeVerdict {
  val status = (outputObject?.get(SharedPayloadKeys.STATUS) as? String)?.trim()?.lowercase()
  if (status == "blocked" || status == "failed") {
    require(wireVerdict == null) {
      "blocked or failed audit phase output must omit verdict."
    }
    return FeatureTaskRuntimeVerdict.ADVANCE
  }
  if (wireVerdict == FeatureTaskRuntimeVerdict.SATISFIED) {
    return FeatureTaskRuntimeVerdict.SATISFIED
  }
  if (
    FeatureTaskRuntimeAuditRemainingAcInterpretation.interpret(
      FeatureTaskRuntimeOutputVerification.auditProseValue(outputObject),
    ) is FeatureTaskRuntimeAuditRemainingAcResult.EmptyRemainingList
  ) {
    return FeatureTaskRuntimeVerdict.SATISFIED
  }
  return requireNotNull(wireVerdict?.takeIf { it in FeatureTaskRuntimeVerdict.AUDIT_VERDICTS }) {
    "audit phase output is missing verdict or carries a removed audit verdict."
  }
}

private fun reviewVerdictFrom(outputObject: FeatureTaskRuntimeWorkflowArtifactMap?): FeatureTaskRuntimeReviewVerdict? {
  val findingsRaw =
    outputObject?.get(SharedPayloadKeys.PRODUCED_OUTPUTS)
      ?.let(JsonCodec::anyToStringAnyMap)
      ?.get(FeatureTaskRuntimeVerificationSignalKeys.REVIEW_FINDINGS) as? List<*>
      ?: return null
  val findings = findingsRaw.mapNotNull(::actionableReviewFinding)
  return FeatureTaskRuntimeReviewVerdict(findings)
}

private fun actionableReviewFinding(entry: Any?): FeatureTaskRuntimeReviewFinding? {
  val map = JsonCodec.anyToStringAnyMap(entry) ?: return null
  val severity = (map["severity"] as? String)?.takeIf(String::isNotBlank)
  val message = (map["message"] as? String)?.takeIf(String::isNotBlank)
  if (severity == null || message == null) return null
  val claimVerdict = optionalClaimVerdict(map[ReviewFindingPayloadKeys.CLAIM_VERDICT])
  val scopeDisposition = optionalScopeDisposition(map[ReviewFindingPayloadKeys.SCOPE_DISPOSITION])
  if (!ReviewFindingActionability.isActionable(claimVerdict, scopeDisposition)) {
    return null
  }
  return FeatureTaskRuntimeReviewFinding(FeatureTaskRuntimeReviewSeverity.fromWire(severity), message)
}

private fun optionalClaimVerdict(raw: Any?): ReviewClaimVerdict? {
  val value = (raw as? String)?.trim()?.takeIf(String::isNotBlank) ?: return null
  return ReviewClaimVerdict.entries.firstOrNull { it.wireValue == value }
}

private fun optionalScopeDisposition(raw: Any?): ReviewScopeDisposition? {
  val value = (raw as? String)?.trim()?.takeIf(String::isNotBlank) ?: return null
  return ReviewScopeDisposition.entries.firstOrNull { it.wireValue == value }
}
