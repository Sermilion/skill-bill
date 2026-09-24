package skillbill.workflow.taskruntime.phase

import skillbill.contracts.SharedPayloadKeys
import skillbill.contracts.workflow.featuretask.FEATURE_TASK_RUNTIME_CONTRACT_VERSION
import skillbill.workflow.taskruntime.feature.FeatureTaskRuntimeAuditRemainingAcInterpretation
import skillbill.workflow.taskruntime.model.audit.FeatureTaskRuntimeAuditRemainingAcResult
import skillbill.workflow.taskruntime.model.handoff.envelope.SettlementEnvelopeRequest
import skillbill.workflow.taskruntime.model.handoff.envelope.SettlementStatus
import skillbill.workflow.taskruntime.phase.task.FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_AUDIT
import skillbill.workflow.taskruntime.phase.task.FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_IMPLEMENT
import skillbill.workflow.taskruntime.phase.task.FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_PLAN
import skillbill.workflow.taskruntime.phase.task.FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_PREPLAN
import skillbill.workflow.taskruntime.phase.task.FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_SIMPLIFY

object ProsePhaseOutputSynthesizer {
  private val PROSE_PHASE_IDS: Set<String> =
    setOf(PHASE_PREPLAN, PHASE_PLAN, PHASE_IMPLEMENT, PHASE_SIMPLIFY, PHASE_AUDIT)
  private val AUDIT_VERDICTS: Set<String> = setOf("satisfied")

  fun isProsePhase(phaseId: String): Boolean = phaseId in PROSE_PHASE_IDS

  fun trySynthesize(
    phaseOutputText: String,
    phaseId: String,
  ): Any? {
    if (!isProsePhase(phaseId)) return null
    val request = synthesisRequest(phaseOutputText, phaseId) ?: return null
    return stampEnvelope(request)
  }

  fun envelopeFromSettlement(request: SettlementEnvelopeRequest): Any {
    require(isProsePhase(request.phaseId)) { "phaseId must be a prose phase, was '${request.phaseId}'." }
    require(request.value.any { !it.isWhitespace() }) { "value must be non-blank." }
    require(request.summary.any { !it.isWhitespace() }) { "summary must be non-blank." }
    return stampEnvelope(request)
  }

  private fun synthesisRequest(
    phaseOutputText: String,
    phaseId: String,
  ): SettlementEnvelopeRequest? {
    val parsed = ProsePhaseOutputParse.bestEffortParse(phaseOutputText)
    if (parsed == null || !ProsePhaseOutputParse.identityCompatible(parsed, phaseId)) return null
    val status = ProsePhaseOutputParse.recoverStatus(parsed) ?: return null
    val valueAndVerdict = recoverableValueAndVerdict(parsed, phaseOutputText, phaseId, status) ?: return null
    val settledAsFailure = status == SettlementStatus.BLOCKED.wireValue || status == SettlementStatus.FAILED.wireValue
    val failureDisposition = if (settledAsFailure) ProsePhaseOutputRecover.recoverFailureDisposition(parsed) else null
    return if (phaseId == PHASE_AUDIT && settledAsFailure && failureDisposition == null) {
      null
    } else {
      SettlementEnvelopeRequest(
        phaseId = phaseId,
        status = status,
        value = valueAndVerdict.first,
        summary = ProsePhaseOutputRecover.recoverSummary(parsed, valueAndVerdict.first),
        prompt = ProsePhaseOutputRecover.recoverPrompt(parsed),
        verdict = valueAndVerdict.second,
        failureDisposition = failureDisposition,
      )
    }
  }

  private fun recoverableValueAndVerdict(
    parsed: Map<String, Any?>,
    phaseOutputText: String,
    phaseId: String,
    status: String,
  ): Pair<String, String?>? {
    val existingValue = ProsePhaseOutputRecover.directValue(parsed)
    val value = existingValue ?: ProsePhaseOutputRecover.recoverLegacyValue(parsed) ?: return null
    if (existingValue != null && phaseId != PHASE_AUDIT) return null
    val verdict =
      if (phaseId == PHASE_AUDIT) {
        if (status == SettlementStatus.COMPLETED.wireValue) {
          ProsePhaseOutputRecover.recoverAuditVerdict(parsed, phaseOutputText)
            ?: when (FeatureTaskRuntimeAuditRemainingAcInterpretation.interpret(value)) {
              FeatureTaskRuntimeAuditRemainingAcResult.EmptyRemainingList -> "satisfied"
              else -> return null
            }
        } else {
          null
        }
      } else {
        null
      }
    return value to verdict
  }

  private fun stampEnvelope(request: SettlementEnvelopeRequest): Map<String, Any?> {
    val produced = linkedMapOf<String, Any?>(SharedPayloadKeys.VALUE to request.value)
    if (!request.prompt.isNullOrBlank()) {
      produced[SharedPayloadKeys.PROMPT] = request.prompt
    }
    val envelope =
      linkedMapOf<String, Any?>(
        SharedPayloadKeys.CONTRACT_VERSION to FEATURE_TASK_RUNTIME_CONTRACT_VERSION,
        SharedPayloadKeys.PHASE_ID to request.phaseId,
        SharedPayloadKeys.STATUS to request.status.wireValue,
        SharedPayloadKeys.SUMMARY to request.summary,
        SharedPayloadKeys.PRODUCED_OUTPUTS to produced,
      )
    if (request.phaseId == PHASE_AUDIT && request.status == SettlementStatus.COMPLETED) {
      val resolved =
        request.verdict?.takeIf { it in AUDIT_VERDICTS }
          ?: when (FeatureTaskRuntimeAuditRemainingAcInterpretation.interpret(request.value)) {
            FeatureTaskRuntimeAuditRemainingAcResult.EmptyRemainingList -> "satisfied"
            else -> null
          }
      requireNotNull(resolved) {
        "completed audit settlement requires verdict in $AUDIT_VERDICTS or an explicit empty remaining-criteria list."
      }
      envelope[SharedPayloadKeys.VERDICT] = resolved
    }
    val settledAsFailure = request.status == SettlementStatus.BLOCKED || request.status == SettlementStatus.FAILED
    if (
      settledAsFailure &&
      request.phaseId == PHASE_AUDIT &&
      request.failureDisposition.isNullOrBlank()
    ) {
      require(false) {
        "blocked or failed audit settlement requires failure_disposition."
      }
    }
    if (settledAsFailure && !request.failureDisposition.isNullOrBlank()) {
      envelope[SharedPayloadKeys.FAILURE_DISPOSITION] = request.failureDisposition
    }
    return envelope
  }
}
