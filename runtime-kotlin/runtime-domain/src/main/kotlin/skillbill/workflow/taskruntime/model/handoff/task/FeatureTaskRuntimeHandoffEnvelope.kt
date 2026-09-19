package skillbill.workflow.taskruntime.model.handoff.task
import skillbill.contracts.SharedPayloadKeys
import skillbill.contracts.decomposition.DecompositionPlanningPayloadKeys
import skillbill.contracts.review.ReviewVerificationSignalKeys
import skillbill.contracts.workflow.featuretask.FEATURE_TASK_RUNTIME_HANDOFF_ENVELOPE_CONTRACT_VERSION
import skillbill.error.shellcontent.InvalidFeatureTaskRuntimePhaseHandoffSchemaError
import skillbill.workflow.taskruntime.model.audit.error
import skillbill.workflow.taskruntime.model.audit.fromWire
import skillbill.workflow.taskruntime.model.audit.map
import skillbill.workflow.taskruntime.model.audit.phaseId
import skillbill.workflow.taskruntime.model.audit.reason
import skillbill.workflow.taskruntime.model.core.FeatureTaskRuntimeRepositoryCheckpoint
import skillbill.workflow.taskruntime.model.core.Map
import skillbill.workflow.taskruntime.model.core.baseRef
import skillbill.workflow.taskruntime.model.core.fingerprint
import skillbill.workflow.taskruntime.model.core.fromWire
import skillbill.workflow.taskruntime.model.core.headRef
import skillbill.workflow.taskruntime.model.core.map
import skillbill.workflow.taskruntime.model.core.raw
import skillbill.workflow.taskruntime.model.core.reason
import skillbill.workflow.taskruntime.model.core.task
import skillbill.workflow.taskruntime.model.core.workingTreeOwnedPaths
import skillbill.workflow.taskruntime.model.feature.fields
import skillbill.workflow.taskruntime.model.feature.fingerprint
import skillbill.workflow.taskruntime.model.feature.map
import skillbill.workflow.taskruntime.model.handoff.Map
import skillbill.workflow.taskruntime.model.handoff.envelope.fromWire
import skillbill.workflow.taskruntime.model.handoff.envelope.phaseId
import skillbill.workflow.taskruntime.model.handoff.envelope.value
import skillbill.workflow.taskruntime.model.handoff.fields
import skillbill.workflow.taskruntime.model.handoff.producerIteration
import skillbill.workflow.taskruntime.model.handoff.projectionContractId
import skillbill.workflow.taskruntime.model.handoff.projectionContractVersion
import skillbill.workflow.taskruntime.model.handoff.projectionName
import skillbill.workflow.taskruntime.model.handoff.promptVisibility
import skillbill.workflow.taskruntime.model.handoff.sourceRef
import skillbill.workflow.taskruntime.model.persistence.artifact.DurableArtifactMapReader
import skillbill.workflow.taskruntime.model.persistence.artifact.map
import skillbill.workflow.taskruntime.model.persistence.artifact.optionalNestedObject
import skillbill.workflow.taskruntime.model.persistence.artifact.optionalString
import skillbill.workflow.taskruntime.model.persistence.artifact.optionalStringList
import skillbill.workflow.taskruntime.model.persistence.artifact.requiredInt
import skillbill.workflow.taskruntime.model.persistence.artifact.requiredList
import skillbill.workflow.taskruntime.model.persistence.artifact.requiredNestedObject
import skillbill.workflow.taskruntime.model.persistence.artifact.requiredString
import skillbill.workflow.taskruntime.model.persistence.artifact.toStringKeyedArtifactMap
import skillbill.workflow.taskruntime.model.persistence.artifact.value
import skillbill.workflow.taskruntime.model.persistence.task.runtime.checkpoint.map
import skillbill.workflow.taskruntime.model.persistence.task.runtime.checkpoint.phaseId
import skillbill.workflow.taskruntime.model.persistence.task.runtime.goal.Map
import skillbill.workflow.taskruntime.model.persistence.task.runtime.goal.reason
import skillbill.workflow.taskruntime.model.persistence.task.runtime.implementation.map
import skillbill.workflow.taskruntime.model.persistence.task.runtime.implementation.phaseId
import skillbill.workflow.taskruntime.model.persistence.task.runtime.implementation.value
import skillbill.workflow.taskruntime.model.persistence.task.runtime.persistence.map
import skillbill.workflow.taskruntime.model.persistence.task.runtime.run.FeatureTaskRuntimeHandoffPromptVisibility
import skillbill.workflow.taskruntime.model.persistence.task.runtime.run.Map
import skillbill.workflow.taskruntime.model.persistence.task.runtime.run.fromWire
import skillbill.workflow.taskruntime.model.persistence.task.runtime.run.value
import skillbill.workflow.taskruntime.model.phase.Map
import skillbill.workflow.taskruntime.model.phase.baseRef
import skillbill.workflow.taskruntime.model.phase.fromWire
import skillbill.workflow.taskruntime.model.phase.headRef
import skillbill.workflow.taskruntime.model.phase.iteration
import skillbill.workflow.taskruntime.model.phase.map
import skillbill.workflow.taskruntime.model.phase.phaseId
import skillbill.workflow.taskruntime.model.phase.raw
import skillbill.workflow.taskruntime.model.phase.reason
import skillbill.workflow.taskruntime.model.phase.sourceLabel
import skillbill.workflow.taskruntime.model.repair.fromWire
import skillbill.workflow.taskruntime.model.repair.task.error
import skillbill.workflow.taskruntime.model.repair.task.fromWire
import skillbill.workflow.taskruntime.model.repair.task.phaseId
import skillbill.workflow.taskruntime.model.repair.task.reason
import skillbill.workflow.taskruntime.model.repair.task.value
import skillbill.workflow.taskruntime.model.review.baseRef
import skillbill.workflow.taskruntime.model.review.fingerprint
import skillbill.workflow.taskruntime.model.review.fromWire
import skillbill.workflow.taskruntime.model.review.headRef
import skillbill.workflow.taskruntime.model.review.message
import skillbill.workflow.taskruntime.model.validation.Map
import skillbill.workflow.taskruntime.model.validation.fingerprint
import skillbill.workflow.taskruntime.model.validation.fromWire
import skillbill.workflow.taskruntime.model.validation.map
import skillbill.workflow.taskruntime.model.validation.raw
import skillbill.workflow.taskruntime.model.validation.reason

data class FeatureTaskRuntimeHandoffEnvelope(
  val consumerPhaseId: String,
  val projections: List<FeatureTaskRuntimeHandoffProjection> = emptyList(),
  val repositoryCheckpoint: FeatureTaskRuntimeRepositoryCheckpoint? = null,
  val contractVersion: String = FEATURE_TASK_RUNTIME_HANDOFF_ENVELOPE_CONTRACT_VERSION,
) {
  init {
    require(consumerPhaseId.isNotBlank()) { "FeatureTaskRuntimeHandoffEnvelope.consumerPhaseId must be non-blank." }
    require(contractVersion.isNotBlank()) { "FeatureTaskRuntimeHandoffEnvelope.contractVersion must be non-blank." }
    val names = projections.map { it.projectionName }
    require(names.distinct().size == names.size) {
      "FeatureTaskRuntimeHandoffEnvelope for '$consumerPhaseId' contains duplicate projection names."
    }
  }

  val promptVisibleProjections: List<FeatureTaskRuntimeHandoffProjection>
    get() = projections.filter { it.promptVisibility == FeatureTaskRuntimeHandoffPromptVisibility.PROMPT_VISIBLE }
  internal fun toEnvelopeMap(): Map<String, Any?> = linkedMapOf<String, Any?>(
    SharedPayloadKeys.CONTRACT_VERSION to contractVersion,
    "consumer_phase_id" to consumerPhaseId,
    "projections" to projections.map { it.toEnvelopeMap() },
  ).apply {
    repositoryCheckpoint?.let { put(ReviewVerificationSignalKeys.REPOSITORY_CHECKPOINT, it.toEnvelopeMap()) }
  }

  companion object {
    internal fun fromEnvelopeMap(raw: Map<String, Any?>): FeatureTaskRuntimeHandoffEnvelope {
      val reader = handoffReader(raw)
      return try {
        FeatureTaskRuntimeHandoffEnvelope(
          consumerPhaseId = reader.requiredString("consumer_phase_id"),
          projections = reader.requiredList("projections").map(::projectionFromWire),
          repositoryCheckpoint = reader.optionalNestedObject(ReviewVerificationSignalKeys.REPOSITORY_CHECKPOINT)?.let {
            val checkpointReader = handoffReader(it)
            FeatureTaskRuntimeRepositoryCheckpoint(
              fingerprint = checkpointReader.requiredString(
                ReviewVerificationSignalKeys.REPOSITORY_CHECKPOINT_FINGERPRINT,
              ),
              baseRef = checkpointReader.optionalString("base_ref"),
              headRef = checkpointReader.optionalString("head_ref"),
              workingTreeOwnedPaths = checkpointReader.optionalStringList("working_tree_owned_paths"),
            )
          },
          contractVersion = reader.requiredString(SharedPayloadKeys.CONTRACT_VERSION),
        )
      } catch (error: IllegalArgumentException) {
        throw InvalidFeatureTaskRuntimePhaseHandoffSchemaError(
          sourceLabel = "<wire>",
          reason = error.message ?: "handoff envelope is invalid.",
          cause = error,
        )
      }
    }

    private fun projectionFromWire(raw: Any?): FeatureTaskRuntimeHandoffProjection {
      val reader = handoffReader(raw.toStringKeyedArtifactMap(::decodeError))
      return FeatureTaskRuntimeHandoffProjection(
        projectionName = reader.requiredString("projection_name"),
        sourceRef = FeatureTaskRuntimeHandoffSourceRef.fromWire(reader.requiredString("source_ref")),
        projectionContractId = reader.requiredString("projection_contract_id"),
        projectionContractVersion = reader.requiredString("projection_contract_version"),
        promptVisibility = FeatureTaskRuntimeHandoffPromptVisibility
          .fromWire(reader.requiredString("prompt_visibility")),
        producerIteration = reader.requiredNestedObject("producer_iteration").let {
          val iterationReader = handoffReader(it)
          FeatureTaskRuntimeProducerIteration(
            phaseId = iterationReader.requiredString(SharedPayloadKeys.PHASE_ID),
            iteration = iterationReader.requiredInt("iteration"),
          )
        },
        fields = reader.requiredList("fields").map(::fieldFromWire),
      )
    }

    private fun fieldFromWire(raw: Any?): FeatureTaskRuntimeHandoffProjectionField {
      val reader = handoffReader(raw.toStringKeyedArtifactMap(::decodeError))
      val name = reader.requiredString(DecompositionPlanningPayloadKeys.NAME)
      return FeatureTaskRuntimeHandoffProjectionField(
        name = name,
        value = when (val kind = reader.requiredString("kind")) {
          "text" -> FeatureTaskRuntimeHandoffProjectionValue.Text(reader.requiredString("text"))
          "text_list" -> FeatureTaskRuntimeHandoffProjectionValue.TextList(
            reader.optionalStringList("items"),
          )
          "compact_reference" -> FeatureTaskRuntimeHandoffProjectionValue.CompactReference(
            kind = FeatureTaskRuntimeCompactReferenceKind.fromWire(reader.requiredString("reference_kind")),
            value = reader.requiredString("reference_value"),
          )
          else -> decodeError("projection field '$name' has unknown value kind '$kind'.")
        },
      )
    }

    private fun decodeError(detail: String): Nothing =
      throw InvalidFeatureTaskRuntimePhaseHandoffSchemaError(sourceLabel = "<wire>", reason = detail)

    private fun handoffReader(map: Map<String, Any?>): DurableArtifactMapReader =
      DurableArtifactMapReader(map) { message -> decodeError(message) }
  }
}
