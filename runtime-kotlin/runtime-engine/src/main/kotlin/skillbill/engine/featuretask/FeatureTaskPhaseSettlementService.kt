package skillbill.engine.featuretask

import me.tatarka.inject.annotations.Inject
import skillbill.boundary.OpenBoundaryMap
import skillbill.contracts.JsonCodec
import skillbill.contracts.SharedPayloadKeys
import skillbill.contracts.workflow.ValidationEvidencePayloadKeys
import skillbill.engine.featuretask.model.FeatureTaskPhaseSettlementBlockRequest
import skillbill.engine.featuretask.model.FeatureTaskPhaseSettlementCompleteRequest
import skillbill.ports.featuretask.FeatureTaskPhaseSettlementRepository
import skillbill.ports.featuretask.model.FeatureTaskPhaseSettlement
import skillbill.ports.featuretask.model.FeatureTaskPhaseSettlementKind
import skillbill.workflow.taskruntime.FeatureTaskRuntimePhaseWorkflowDefinition
import skillbill.workflow.taskruntime.ProsePhaseOutputSynthesizer
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeValidationEvidence
import skillbill.workflow.taskruntime.model.SettlementEnvelopeRequest
import java.time.Clock

@Inject
class FeatureTaskPhaseSettlementService(
  private val repository: FeatureTaskPhaseSettlementRepository,
  private val clock: Clock,
) {
  @OpenBoundaryMap("MCP feature_task_phase_complete acknowledgement wire map")
  fun complete(request: FeatureTaskPhaseSettlementCompleteRequest): Map<String, Any?> {
    require(ProsePhaseOutputSynthesizer.isProsePhase(request.phaseId)) {
      "phase_id must be a prose phase (preplan|plan|implement|audit)."
    }
    val verdict = when (request.phaseId) {
      FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_AUDIT ->
        requireNotNull(request.verdict?.takeIf { it == "satisfied" }) {
          "feature_task_phase_complete requires verdict=satisfied when phase_id=audit."
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
    )
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

  @OpenBoundaryMap("MCP feature_task_phase_block acknowledgement wire map")
  fun block(request: FeatureTaskPhaseSettlementBlockRequest): Map<String, Any?> {
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
    )
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

  @OpenBoundaryMap("Durable MCP phase-settlement envelope wire map for gate consumption")
  fun findEnvelope(workflowId: String, phaseId: String, attempt: Int): Map<String, Any?>? {
    val settlement = repository.find(workflowId, phaseId, attempt) ?: return null
    val envelope = JsonCodec.parseObjectOrNull(settlement.envelopeJson)
      ?.let { JsonCodec.anyToStringAnyMap(JsonCodec.jsonElementToValue(it)) }
    val evidence = envelope
      ?.get(SharedPayloadKeys.PRODUCED_OUTPUTS)
      ?.let(JsonCodec::anyToStringAnyMap)
      ?.get(ValidationEvidencePayloadKeys.VALIDATION_RESULT)
      ?.let(JsonCodec::anyToStringAnyMap)
      ?.get(ValidationEvidencePayloadKeys.VALIDATION_EVIDENCE)
      ?.let(JsonCodec::anyToStringAnyMap)
    if (evidence != null) {
      FeatureTaskRuntimeValidationEvidence.fromArtifactMap(evidence, "$phaseId settlement")
    }
    return envelope
  }

  fun clear(workflowId: String, phaseId: String, attempt: Int): Boolean =
    repository.delete(workflowId, phaseId, attempt)

  private fun persist(request: PersistRequest): Map<String, Any?> {
    val envelopeJson = JsonCodec.mapToJsonString(request.envelope)
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
    return linkedMapOf(
      SharedPayloadKeys.STATUS to "ok",
      SharedPayloadKeys.WORKFLOW_ID to request.workflowId,
      SharedPayloadKeys.PHASE_ID to request.phaseId,
      "attempt" to request.attempt,
      "kind" to request.kind.wireValue,
      "envelope" to request.envelope,
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
    val envelope: Map<String, Any?>,
  )

  companion object {
    val KIND_COMPLETE: FeatureTaskPhaseSettlementKind = FeatureTaskPhaseSettlementKind.Complete
    val KIND_BLOCK: FeatureTaskPhaseSettlementKind = FeatureTaskPhaseSettlementKind.Block
    private const val SUMMARY_MAX_CHARS: Int = 240
    private const val SUMMARY_ELLIPSIS_PREFIX: Int = 237
  }
}
