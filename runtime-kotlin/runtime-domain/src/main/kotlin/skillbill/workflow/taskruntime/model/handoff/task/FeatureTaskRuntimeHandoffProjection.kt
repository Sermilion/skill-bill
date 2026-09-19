package skillbill.workflow.taskruntime.model.handoff.task
import skillbill.contracts.JsonCodec
import skillbill.contracts.SharedPayloadKeys
import skillbill.contracts.decomposition.DecompositionPlanningPayloadKeys
import skillbill.workflow.taskruntime.model.audit.map
import skillbill.workflow.taskruntime.model.audit.phaseId
import skillbill.workflow.taskruntime.model.audit.producingPhaseId
import skillbill.workflow.taskruntime.model.core.Map
import skillbill.workflow.taskruntime.model.core.map
import skillbill.workflow.taskruntime.model.core.task
import skillbill.workflow.taskruntime.model.feature.map
import skillbill.workflow.taskruntime.model.handoff.Map
import skillbill.workflow.taskruntime.model.handoff.envelope.phaseId
import skillbill.workflow.taskruntime.model.handoff.envelope.value
import skillbill.workflow.taskruntime.model.handoff.name
import skillbill.workflow.taskruntime.model.handoff.producingPhaseId
import skillbill.workflow.taskruntime.model.persistence.artifact.map
import skillbill.workflow.taskruntime.model.persistence.artifact.value
import skillbill.workflow.taskruntime.model.persistence.task.runtime.checkpoint.map
import skillbill.workflow.taskruntime.model.persistence.task.runtime.checkpoint.phaseId
import skillbill.workflow.taskruntime.model.persistence.task.runtime.goal.Map
import skillbill.workflow.taskruntime.model.persistence.task.runtime.goal.field
import skillbill.workflow.taskruntime.model.persistence.task.runtime.implementation.map
import skillbill.workflow.taskruntime.model.persistence.task.runtime.implementation.phaseId
import skillbill.workflow.taskruntime.model.persistence.task.runtime.implementation.value
import skillbill.workflow.taskruntime.model.persistence.task.runtime.persistence.map
import skillbill.workflow.taskruntime.model.persistence.task.runtime.run.FeatureTaskRuntimeHandoffPromptVisibility
import skillbill.workflow.taskruntime.model.persistence.task.runtime.run.Map
import skillbill.workflow.taskruntime.model.persistence.task.runtime.run.value
import skillbill.workflow.taskruntime.model.persistence.task.runtime.run.wireValue
import skillbill.workflow.taskruntime.model.phase.Map
import skillbill.workflow.taskruntime.model.phase.iteration
import skillbill.workflow.taskruntime.model.phase.map
import skillbill.workflow.taskruntime.model.phase.name
import skillbill.workflow.taskruntime.model.phase.phaseId
import skillbill.workflow.taskruntime.model.repair.task.phaseId
import skillbill.workflow.taskruntime.model.repair.task.value
import skillbill.workflow.taskruntime.model.validation.Map
import skillbill.workflow.taskruntime.model.validation.map
import skillbill.workflow.taskruntime.model.validation.wireValue

data class FeatureTaskRuntimeHandoffProjection(
  val projectionName: String,
  val sourceRef: FeatureTaskRuntimeHandoffSourceRef,
  val projectionContractId: String,
  val projectionContractVersion: String,
  val promptVisibility: FeatureTaskRuntimeHandoffPromptVisibility,
  val fields: List<FeatureTaskRuntimeHandoffProjectionField>,
  val producerIteration: FeatureTaskRuntimeProducerIteration =
    FeatureTaskRuntimeProducerIteration(
      phaseId = (sourceRef as? FeatureTaskRuntimeHandoffSourceRef.UpstreamPhaseOutput)?.producingPhaseId
        ?: "runtime",
      iteration = 1,
    ),
) {

  val utf8ByteSize: Int
    get() = canonicalDeliveredRendering.toByteArray(Charsets.UTF_8).size
  val itemCount: Int get() = fields.sumOf { it.value.itemCount }
  val canonicalDeliveredRendering: String
    get() = if (promptVisibility == FeatureTaskRuntimeHandoffPromptVisibility.PROMPT_VISIBLE) {
      buildString {
        if (sourceRef is FeatureTaskRuntimeHandoffSourceRef.UpstreamPhaseOutput) {
          appendLine("### from: ${sourceRef.producingPhaseId}")
        } else {
          appendLine("### $projectionName (${sourceRef.wireValue})")
        }
        fields.forEach { field ->
          val singleReceipt = fields.size == 1 &&
            field.name == "phase_output_receipt"
          when (val value = field.value) {
            is FeatureTaskRuntimeHandoffProjectionValue.Text -> {
              val text = value.text.escapeProjectionLineBreaks()
              if (singleReceipt) appendLine(text) else appendLine("${field.name}: $text")
            }
            is FeatureTaskRuntimeHandoffProjectionValue.TextList -> {
              appendLine("${field.name}:")
              value.items.forEach { item -> appendLine("  - ${item.escapeProjectionLineBreaks()}") }
            }
            is FeatureTaskRuntimeHandoffProjectionValue.CompactReference ->
              appendLine("${field.name}: ${value.kind.wireValue}=${value.value}")
          }
        }
      }
    } else {
      JsonCodec.mapToJsonString(toEnvelopeMap())
    }
  internal fun toEnvelopeMap(): Map<String, Any?> = linkedMapOf(
    "projection_name" to projectionName,
    "source_ref" to sourceRef.wireValue,
    "projection_contract_id" to projectionContractId,
    "projection_contract_version" to projectionContractVersion,
    "prompt_visibility" to promptVisibility.wireValue,
    "producer_iteration" to mapOf(
      SharedPayloadKeys.PHASE_ID to producerIteration.phaseId,
      "iteration" to producerIteration.iteration,
    ),
    "fields" to fields.map { field ->
      linkedMapOf<String, Any?>(DecompositionPlanningPayloadKeys.NAME to field.name).apply {
        when (val value = field.value) {
          is FeatureTaskRuntimeHandoffProjectionValue.Text -> {
            put("kind", "text")
            put("text", value.text)
          }
          is FeatureTaskRuntimeHandoffProjectionValue.TextList -> {
            put("kind", "text_list")
            put("items", value.items)
          }
          is FeatureTaskRuntimeHandoffProjectionValue.CompactReference -> {
            put("kind", "compact_reference")
            put("reference_kind", value.kind.wireValue)
            put("reference_value", value.value)
          }
        }
      }
    },
  )
}

private fun String.escapeProjectionLineBreaks(): String = replace("\r", "\\r").replace("\n", "\\n")
