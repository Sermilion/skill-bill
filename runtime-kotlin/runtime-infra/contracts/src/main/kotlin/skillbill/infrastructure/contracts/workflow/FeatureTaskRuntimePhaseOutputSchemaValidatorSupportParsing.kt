package skillbill.infrastructure.contracts.workflow

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.JsonNode
import skillbill.contracts.SharedPayloadKeys
import skillbill.error.InvalidFeatureTaskRuntimePhaseOutputSchemaError
import skillbill.workflow.model.WorkflowStepStatus
import skillbill.workflow.model.workflowStepStatus
import skillbill.workflow.taskruntime.FeatureTaskRuntimePhaseWorkflowDefinition
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeVerdict
import java.util.logging.Level

internal fun readPhaseOutputObjectNode(phaseOutputText: String, sourceLabel: String): JsonNode {
  val parsedCandidates = phaseOutputObjectCandidates(phaseOutputText).mapNotNull(::tryParseObjectNode)
  val envelopeCandidates = parsedCandidates.filter { candidate ->
    candidate.path("phase_id").asText("") == sourceLabel
  }
  val distinctValidEnvelopes = envelopeCandidates
    .filter { candidate -> FeatureTaskRuntimePhaseOutputWireSchema.schema.validate(candidate).isEmpty() }
    .distinctBy(::canonicalCandidateKey)
  if (distinctValidEnvelopes.size > 1) {
    throw InvalidFeatureTaskRuntimePhaseOutputSchemaError(
      sourceLabel = sourceLabel,
      reason = "Phase output contains multiple conflicting schema-valid envelopes.",
      payloadFreeReason = "Phase output contains multiple conflicting schema-valid envelopes.",
    )
  }
  envelopeCandidates.firstOrNull()?.let { return it }
  parsedCandidates.firstOrNull()?.let { return it }
  return parseObjectNodeStrict(phaseOutputText.trim(), sourceLabel)
}

internal fun readPhaseOutputObjectNodeLenient(phaseOutputText: String, sourceLabel: String): JsonNode {
  val parsedCandidates = phaseOutputObjectCandidates(phaseOutputText).mapNotNull(::tryParseObjectNode)
  val envelopeCandidates = parsedCandidates.filter { candidate ->
    candidate.path("phase_id").asText("") == sourceLabel
  }
  val distinctEnvelopes = envelopeCandidates.distinctBy(::canonicalCandidateKey)
  if (distinctEnvelopes.size > 1) {
    throw InvalidFeatureTaskRuntimePhaseOutputSchemaError(
      sourceLabel = sourceLabel,
      reason = "Phase output contains multiple conflicting envelopes.",
      payloadFreeReason = "Phase output contains multiple conflicting envelopes.",
    )
  }
  envelopeCandidates.firstOrNull()?.let { return it }
  parsedCandidates.firstOrNull()?.let { return it }
  return parseObjectNodeStrict(phaseOutputText.trim(), sourceLabel)
}

internal fun validateVerifyingEnvelopeShell(parsed: Map<String, Any?>, sourceLabel: String) {
  val phaseId = parsed[SharedPayloadKeys.PHASE_ID] as? String
  if (phaseId != sourceLabel) {
    throw InvalidFeatureTaskRuntimePhaseOutputSchemaError(
      sourceLabel = sourceLabel,
      reason = "phase_id must match the executing phase '$sourceLabel' but was '${phaseId.orEmpty()}'.",
      payloadFreeReason = "phase_id must match the executing phase '$sourceLabel'.",
      failureCode = "phase_id_mismatch",
    )
  }
  val status = parsed[SharedPayloadKeys.STATUS] as? String
  if (status.isNullOrBlank()) {
    throw InvalidFeatureTaskRuntimePhaseOutputSchemaError(
      sourceLabel = sourceLabel,
      reason = "status is required.",
      payloadFreeReason = "status is required.",
    )
  }
}

private fun canonicalCandidateKey(candidate: JsonNode): String =
  FeatureTaskRuntimePhaseOutputWireSchema.mapper.writeValueAsString(canonicalizeCandidate(candidate))

private fun canonicalizeCandidate(node: JsonNode): JsonNode = when {
  node.isObject -> FeatureTaskRuntimePhaseOutputWireSchema.mapper.createObjectNode().apply {
    node.fieldNames().asSequence().sorted().forEach { field ->
      set<JsonNode>(field, canonicalizeCandidate(node.path(field)))
    }
  }
  node.isArray -> FeatureTaskRuntimePhaseOutputWireSchema.mapper.createArrayNode().apply {
    node.forEach { element -> add(canonicalizeCandidate(element)) }
  }
  else -> node
}

private fun tryParseObjectNode(candidate: String): JsonNode? = try {
  FeatureTaskRuntimePhaseOutputWireSchema.yamlMapper.readTree(candidate)?.takeIf(JsonNode::isObject)
} catch (error: JsonProcessingException) {
  featureTaskRuntimePhaseOutputLog.log(
    Level.FINE,
    "Phase-output candidate did not parse; trying the next one.",
    error,
  )
  null
}

private fun parseObjectNodeStrict(text: String, sourceLabel: String): JsonNode {
  val node =
    try {
      FeatureTaskRuntimePhaseOutputWireSchema.yamlMapper.readTree(text)
    } catch (error: JsonProcessingException) {
      throw InvalidFeatureTaskRuntimePhaseOutputSchemaError(
        sourceLabel = sourceLabel,
        reason = "Phase output is malformed: ${error.originalMessage.orEmpty()}",
        cause = error,
        payloadFreeReason = "Phase output is malformed: it is not parseable as a single JSON object.",
        failureCode = "malformed",
      )
    }
  if (node == null || !node.isObject) {
    throw InvalidFeatureTaskRuntimePhaseOutputSchemaError(
      sourceLabel = sourceLabel,
      reason = "<root> must be an object.",
      payloadFreeReason = "<root> must be an object.",
      failureCode = "root_not_object",
    )
  }
  return node
}

internal fun phaseOutputObjectNodeToMap(node: JsonNode, sourceLabel: String): Map<String, Any?> = try {
  FeatureTaskRuntimePhaseOutputWireSchema.mapper.convertValue(
    node,
    FeatureTaskRuntimePhaseOutputWireSchema.mapType,
  )
} catch (error: IllegalArgumentException) {
  throw InvalidFeatureTaskRuntimePhaseOutputSchemaError(
    sourceLabel = sourceLabel,
    reason = "Phase output root object cannot be converted to a string-keyed map: ${error.message.orEmpty()}",
    cause = error,
    payloadFreeReason = "Phase output root object cannot be converted to a string-keyed map.",
  )
}

internal fun dropSpuriousAuditCompletedVerdict(parsed: MutableMap<String, Any?>) {
  val isAuditPhase = parsed[SharedPayloadKeys.PHASE_ID] ==
    FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_AUDIT
  val isCompleted = (parsed[SharedPayloadKeys.STATUS] as? String).workflowStepStatus() ==
    WorkflowStepStatus.COMPLETED
  val verdict = parsed[SharedPayloadKeys.VERDICT] as? String
  val isSpurious = verdict != null &&
    verdict != FeatureTaskRuntimeVerdict.SATISFIED.wireValue &&
    verdict != FeatureTaskRuntimeVerdict.GAPS_FOUND.wireValue
  if (isAuditPhase && isCompleted && isSpurious) {
    parsed.remove(SharedPayloadKeys.VERDICT)
  }
}
