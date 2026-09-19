package skillbill.engine.featuretask.phase.core

import me.tatarka.inject.annotations.Inject
import skillbill.contracts.JsonCodec
import skillbill.contracts.SharedPayloadKeys
import skillbill.contracts.workflow.identity.evidence.ValidationEvidencePayloadKeys
import skillbill.engine.featuretask.model.phase.FeatureTaskPhaseSettlementAcknowledgment
import skillbill.engine.featuretask.model.phase.FeatureTaskPhaseSettlementBlockRequest
import skillbill.engine.featuretask.model.phase.FeatureTaskPhaseSettlementCompleteRequest
import skillbill.engine.featuretask.model.phase.FeatureTaskPhaseSettlementEnvelope
import skillbill.ports.featuretask.FeatureTaskPhaseSettlementRepository
import skillbill.ports.featuretask.model.FeatureTaskPhaseSettlement
import skillbill.ports.featuretask.model.FeatureTaskPhaseSettlementKind
import skillbill.workflow.taskruntime.artifact.FeatureTaskRuntimeWorkflowArtifactMap
import skillbill.workflow.taskruntime.artifact.decodeValidationEvidenceFromArtifact
import skillbill.workflow.taskruntime.artifact.toWorkflowArtifactMap
import skillbill.workflow.taskruntime.feature.FeatureTaskRuntimeAuditRemainingAcInterpretation
import skillbill.workflow.taskruntime.model.audit.FeatureTaskRuntimeAuditRemainingAcResult
import skillbill.workflow.taskruntime.model.handoff.envelope.SettlementEnvelopeRequest
import skillbill.workflow.taskruntime.phase.ProsePhaseOutputSynthesizer
import skillbill.workflow.taskruntime.phase.task.FeatureTaskRuntimePhaseWorkflowDefinition
import java.time.Clock
@Inject
class FeatureTaskPhaseSettlementService(
  private val repository: FeatureTaskPhaseSettlementRepository,
  private val clock: Clock,
) {
  fun complete(request: FeatureTaskPhaseSettlementCompleteRequest): FeatureTaskPhaseSettlementAcknowledgment {
    require(ProsePhaseOutputSynthesizer.isProsePhase(request.phaseId)) {
      "phase_id must be a prose phase (preplan|plan|implement|audit)."
    }
    val verdict = when (request.phaseId) {
      FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_AUDIT ->
        request.verdict?.takeIf { it == "satisfied" }
          ?: when (FeatureTaskRuntimeAuditRemainingAcInterpretation.interpret(request.value)) {
            FeatureTaskRuntimeAuditRemainingAcResult.EmptyRemainingList -> "satisfied"
            else -> null
          }.let { resolved ->
            requireNotNull(resolved) {
              "feature_task_phase_complete requires an explicit empty remaining-criteria list " +
                "or verdict=satisfied when phase_id=audit."
            }
          }
      else -> request.verdict
    }
    val envelope = ProsePhaseOutputSynthesizer.envelopeFromSettlement(
      SettlementEnvelopeRequest(
        phaseId = request.phaseId,
        status = "completed",
        value = request.value,
        summary = request.summary?.takeIf { it.any { ch -> !ch.isWhitespace() } } ?: truncateSummary(request.value),
        prompt = request.prompt,
        verdict = verdict,
      ),
    ).toWorkflowArtifactMap()
    return persist(
      PersistRequest(
        workflowId = request.workflowId,
        phaseId = request.phaseId,
        attempt = request.attempt,
        kind = KIND_COMPLETE,
        envelope = envelope,
      ),
    )
  }

  fun block(request: FeatureTaskPhaseSettlementBlockRequest): FeatureTaskPhaseSettlementAcknowledgment {
    require(ProsePhaseOutputSynthesizer.isProsePhase(request.phaseId)) {
      "phase_id must be a prose phase (preplan|plan|implement|audit)."
    }
    require(request.failureDisposition.any { !it.isWhitespace() }) {
      "feature_task_phase_block requires a non-blank failure_disposition."
    }
    val envelope = ProsePhaseOutputSynthesizer.envelopeFromSettlement(
      SettlementEnvelopeRequest(
        phaseId = request.phaseId,
        status = "blocked",
        value = request.reason,
        summary = truncateSummary(request.reason),
        failureDisposition = request.failureDisposition,
      ),
    ).toWorkflowArtifactMap()
    return persist(
      PersistRequest(
        workflowId = request.workflowId,
        phaseId = request.phaseId,
        attempt = request.attempt,
        kind = KIND_BLOCK,
        envelope = envelope,
      ),
    )
  }

  fun findEnvelope(workflowId: String, phaseId: String, attempt: Int): FeatureTaskPhaseSettlementEnvelope? {
    val settlement = repository.find(workflowId, phaseId, attempt) ?: return null
    val envelope = JsonCodec.parseObjectOrNull(settlement.envelopeJson)
      ?.let { JsonCodec.anyToStringAnyMap(JsonCodec.jsonElementToValue(it)) }
      ?: return null
    val wire = envelope.toWorkflowArtifactMap()
    val evidence = wire[SharedPayloadKeys.PRODUCED_OUTPUTS]
      ?.let(JsonCodec::anyToStringAnyMap)
      ?.get(ValidationEvidencePayloadKeys.VALIDATION_RESULT)
      ?.let(JsonCodec::anyToStringAnyMap)
      ?.get(ValidationEvidencePayloadKeys.VALIDATION_EVIDENCE)
      ?.let(JsonCodec::anyToStringAnyMap)
    if (evidence != null) {
      decodeValidationEvidenceFromArtifact(evidence, "$phaseId settlement")
    }
    return FeatureTaskPhaseSettlementEnvelope(envelope = wire)
  }

  fun clear(workflowId: String, phaseId: String, attempt: Int): Boolean =
    repository.delete(workflowId, phaseId, attempt)

  private fun persist(request: PersistRequest): FeatureTaskPhaseSettlementAcknowledgment {
    val envelopeJson = JsonCodec.mapToJsonString(request.envelopeAsMap())
    repository.upsert(
      FeatureTaskPhaseSettlement(
        workflowId = request.workflowId,
        phaseId = request.phaseId,
        attempt = request.attempt,
        kind = request.kind,
        envelopeJson = envelopeJson,
        recordedAt = clock.instant().toString(),
      ),
    )
    return FeatureTaskPhaseSettlementAcknowledgment(
      status = "ok",
      workflowId = request.workflowId,
      phaseId = request.phaseId,
      attempt = request.attempt,
      kind = request.kind,
      envelope = request.envelope,
    )
  }

  private fun truncateSummary(value: String): String {
    val compact = value.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() }.orEmpty()
    return when {
      compact.isBlank() -> "Phase settlement recorded."
      compact.length <= SUMMARY_MAX_CHARS -> compact
      else -> compact.take(SUMMARY_ELLIPSIS_PREFIX) + "..."
    }
  }

  private data class PersistRequest(
    val workflowId: String,
    val phaseId: String,
    val attempt: Int,
    val kind: FeatureTaskPhaseSettlementKind,
    val envelope: FeatureTaskRuntimeWorkflowArtifactMap,
  ) {
    fun envelopeAsMap(): Map<String, Any?> = envelope
  }

  companion object {
    val KIND_COMPLETE: FeatureTaskPhaseSettlementKind = FeatureTaskPhaseSettlementKind.Complete
    val KIND_BLOCK: FeatureTaskPhaseSettlementKind = FeatureTaskPhaseSettlementKind.Block
    private const val SUMMARY_MAX_CHARS: Int = 240
    private const val SUMMARY_ELLIPSIS_PREFIX: Int = 237
  }
}
