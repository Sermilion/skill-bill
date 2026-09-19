package skillbill.workflow.taskruntime.model.core
import skillbill.contracts.review.ReviewVerificationSignalKeys
import skillbill.workflow.taskruntime.model.audit.entries
import skillbill.workflow.taskruntime.model.feature.entries
import skillbill.workflow.taskruntime.model.handoff.Map
import skillbill.workflow.taskruntime.model.handoff.envelope.value
import skillbill.workflow.taskruntime.model.handoff.task.MAX_REPOSITORY_FINGERPRINT_LENGTH
import skillbill.workflow.taskruntime.model.handoff.task.unrecognizedHandoffWireValue
import skillbill.workflow.taskruntime.model.handoff.task.value
import skillbill.workflow.taskruntime.model.handoff.task.wireValue
import skillbill.workflow.taskruntime.model.persistence.artifact.value
import skillbill.workflow.taskruntime.model.persistence.task.runtime.goal.Map
import skillbill.workflow.taskruntime.model.persistence.task.runtime.goal.entries
import skillbill.workflow.taskruntime.model.persistence.task.runtime.implementation.value
import skillbill.workflow.taskruntime.model.persistence.task.runtime.run.Map
import skillbill.workflow.taskruntime.model.persistence.task.runtime.run.entries
import skillbill.workflow.taskruntime.model.persistence.task.runtime.run.value
import skillbill.workflow.taskruntime.model.persistence.task.runtime.run.wireValue
import skillbill.workflow.taskruntime.model.phase.Map
import skillbill.workflow.taskruntime.model.repair.task.entries
import skillbill.workflow.taskruntime.model.repair.task.value
import skillbill.workflow.taskruntime.model.validation.Map
import skillbill.workflow.taskruntime.model.validation.entries
import skillbill.workflow.taskruntime.model.validation.wireValue

enum class FeatureTaskRuntimeRepositoryCheckpointPolicy(val wireValue: String) {
  NOT_REQUIRED("not_required"),
  MUST_MATCH("must_match"),
  REFRESH_FROM_REPOSITORY("refresh_from_repository"),
  ;

  companion object {
    fun fromWire(value: String): FeatureTaskRuntimeRepositoryCheckpointPolicy =
      entries.firstOrNull { it.wireValue == value }
        ?: unrecognizedHandoffWireValue("repository checkpoint policy", value)
  }
}

data class FeatureTaskRuntimeRepositoryCheckpoint(
  val fingerprint: String,
  val baseRef: String? = null,
  val headRef: String? = null,
  val workingTreeOwnedPaths: List<String> = emptyList(),
) {
  init {
    require(fingerprint.isNotBlank()) {
      "FeatureTaskRuntimeRepositoryCheckpoint.fingerprint must be non-blank; an unidentified checkpoint " +
        "cannot satisfy must_match or refresh_from_repository."
    }
    require(fingerprint.length <= MAX_REPOSITORY_FINGERPRINT_LENGTH) {
      "FeatureTaskRuntimeRepositoryCheckpoint.fingerprint allows at most " +
        "$MAX_REPOSITORY_FINGERPRINT_LENGTH characters, had ${fingerprint.length}."
    }
    require(workingTreeOwnedPaths.none(String::isBlank)) {
      "FeatureTaskRuntimeRepositoryCheckpoint.workingTreeOwnedPaths must not contain blank entries."
    }
  }
  internal fun toEnvelopeMap(): Map<String, Any?> =
    linkedMapOf<String, Any?>(ReviewVerificationSignalKeys.REPOSITORY_CHECKPOINT_FINGERPRINT to fingerprint).apply {
      baseRef?.let { put("base_ref", it) }
      headRef?.let { put("head_ref", it) }
      if (workingTreeOwnedPaths.isNotEmpty()) put("working_tree_owned_paths", workingTreeOwnedPaths)
    }
}
