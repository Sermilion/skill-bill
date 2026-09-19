package skillbill.workflow.taskruntime.model.audit
import skillbill.contracts.SharedPayloadKeys
import skillbill.contracts.workflow.featuretask.FEATURE_TASK_RUNTIME_PERSISTENCE_CONTRACT_VERSION
import skillbill.error.shellcontent.InvalidWorkflowStateSchemaError
import skillbill.workflow.taskruntime.model.core.Map
import skillbill.workflow.taskruntime.model.core.raw
import skillbill.workflow.taskruntime.model.feature.entries
import skillbill.workflow.taskruntime.model.handoff.Map
import skillbill.workflow.taskruntime.model.handoff.envelope.value
import skillbill.workflow.taskruntime.model.handoff.task.FEATURE_TASK_RUNTIME_INCOMPATIBLE_RECORD_GUIDANCE
import skillbill.workflow.taskruntime.model.handoff.task.value
import skillbill.workflow.taskruntime.model.handoff.task.wireValue
import skillbill.workflow.taskruntime.model.persistence.artifact.durableArtifactMapReader
import skillbill.workflow.taskruntime.model.persistence.artifact.optionalBoolean
import skillbill.workflow.taskruntime.model.persistence.artifact.optionalString
import skillbill.workflow.taskruntime.model.persistence.artifact.optionalStringList
import skillbill.workflow.taskruntime.model.persistence.artifact.requiredInt
import skillbill.workflow.taskruntime.model.persistence.artifact.requiredString
import skillbill.workflow.taskruntime.model.persistence.artifact.value
import skillbill.workflow.taskruntime.model.persistence.task.runtime.goal.Map
import skillbill.workflow.taskruntime.model.persistence.task.runtime.goal.entries
import skillbill.workflow.taskruntime.model.persistence.task.runtime.implementation.value
import skillbill.workflow.taskruntime.model.persistence.task.runtime.run.Map
import skillbill.workflow.taskruntime.model.persistence.task.runtime.run.entries
import skillbill.workflow.taskruntime.model.persistence.task.runtime.run.keys
import skillbill.workflow.taskruntime.model.persistence.task.runtime.run.value
import skillbill.workflow.taskruntime.model.persistence.task.runtime.run.wireValue
import skillbill.workflow.taskruntime.model.phase.Map
import skillbill.workflow.taskruntime.model.phase.raw
import skillbill.workflow.taskruntime.model.repair.task.entries
import skillbill.workflow.taskruntime.model.repair.task.value
import skillbill.workflow.taskruntime.model.validation.Map
import skillbill.workflow.taskruntime.model.validation.entries
import skillbill.workflow.taskruntime.model.validation.raw
import skillbill.workflow.taskruntime.model.validation.wireValue

enum class FeatureTaskRuntimeAuditGapPauseKind(val wireValue: String) {
  NO_PROGRESS("no_progress"),
  WARN_THRESHOLD("warn_threshold"),
  ;

  companion object {
    fun fromWire(value: String): FeatureTaskRuntimeAuditGapPauseKind? = entries.firstOrNull { it.wireValue == value }
  }
}

data class FeatureTaskRuntimeAuditGapPause(
  val pauseKind: FeatureTaskRuntimeAuditGapPauseKind,
  val reason: String,
  val edgeIteration: Int,
  val operatorDecision: String? = null,
  val grantConsumed: Boolean = false,
) {
  constructor(
    pauseKind: String,
    reason: String,
    edgeIteration: Int,
    operatorDecision: String? = null,
    grantConsumed: Boolean = false,
  ) : this(
    pauseKind = requireNotNull(FeatureTaskRuntimeAuditGapPauseKind.fromWire(pauseKind)) {
      "FeatureTaskRuntimeAuditGapPause.pauseKind must be no_progress or warn_threshold, was '$pauseKind'."
    },
    reason = reason,
    edgeIteration = edgeIteration,
    operatorDecision = operatorDecision,
    grantConsumed = grantConsumed,
  )

  init {
    require(reason.isNotBlank()) { "FeatureTaskRuntimeAuditGapPause.reason must be non-blank." }
    require(edgeIteration >= 1) {
      "FeatureTaskRuntimeAuditGapPause.edgeIteration must be >= 1, was $edgeIteration."
    }
    require(
      operatorDecision == null ||
        operatorDecision in setOf(AUDIT_GAP_PAUSE_DECISION_RETRY_FIX, AUDIT_GAP_PAUSE_DECISION_ABANDON_SUBTASK),
    ) {
      "FeatureTaskRuntimeAuditGapPause.operatorDecision must be retry_fix or abandon_subtask, " +
        "was '$operatorDecision'."
    }
  }

  companion object {
    const val AUDIT_GAP_PAUSE_DECISION_RETRY_FIX: String = "retry_fix"
    const val AUDIT_GAP_PAUSE_DECISION_ABANDON_SUBTASK: String = "abandon_subtask"
    internal fun fromArtifactMap(raw: Map<String, Any?>): FeatureTaskRuntimeAuditGapPause {
      requireExactAuditGapPauseFields(raw)
      if (raw[SharedPayloadKeys.CONTRACT_VERSION] != FEATURE_TASK_RUNTIME_PERSISTENCE_CONTRACT_VERSION) {
        throw InvalidWorkflowStateSchemaError(
          "Feature-task-runtime audit-gap pause artifact uses unsupported persistence contract " +
            "version '${raw[SharedPayloadKeys.CONTRACT_VERSION]}'; $FEATURE_TASK_RUNTIME_INCOMPATIBLE_RECORD_GUIDANCE.",
        )
      }
      if (raw["record_kind"] != "audit_gap_pause") {
        throw InvalidWorkflowStateSchemaError(
          "Feature-task-runtime audit-gap pause artifact must have kind 'audit_gap_pause'.",
        )
      }
      val reader = durableArtifactMapReader(raw)
      return FeatureTaskRuntimeAuditGapPause(
        pauseKind = requireNotNull(FeatureTaskRuntimeAuditGapPauseKind.fromWire(reader.requiredString("pause_kind"))) {
          "Unknown FeatureTaskRuntimeAuditGapPause.pauseKind."
        },
        reason = reader.requiredString("reason"),
        edgeIteration = reader.requiredInt("edge_iteration"),
        operatorDecision = reader.optionalString("operator_decision"),
        grantConsumed = reader.optionalBoolean("grant_consumed") ?: false,
      )
    }

    private fun requireExactAuditGapPauseFields(raw: Map<String, Any?>) {
      val expected = setOf(
        SharedPayloadKeys.CONTRACT_VERSION,
        "record_kind",
        "pause_kind",
        "reason",
        "edge_iteration",
        "operator_decision",
        "grant_consumed",
      )
      val unexpected = raw.keys - expected
      if (unexpected.isNotEmpty()) {
        throw InvalidWorkflowStateSchemaError(
          "Feature-task-runtime audit-gap pause artifact contains unsupported fields; " +
            "$FEATURE_TASK_RUNTIME_INCOMPATIBLE_RECORD_GUIDANCE.",
        )
      }
    }
  }
}

data class FeatureTaskRuntimeAuditGapProgress(
  val criterionRefs: Set<String>,
  val repositoryFingerprint: String? = null,
) {
  init {
    require(criterionRefs.all(String::isNotBlank)) {
      "FeatureTaskRuntimeAuditGapProgress.criterionRefs must not contain blank refs."
    }
  }

  companion object {
    const val HAD_GAPS_MARKER: String = "gaps_found"
    internal fun fromArtifactMap(raw: Map<String, Any?>): FeatureTaskRuntimeAuditGapProgress {
      requireExactAuditGapProgressFields(raw)
      if (raw[SharedPayloadKeys.CONTRACT_VERSION] != FEATURE_TASK_RUNTIME_PERSISTENCE_CONTRACT_VERSION) {
        throw InvalidWorkflowStateSchemaError(
          "Feature-task-runtime audit-gap progress artifact uses unsupported persistence contract " +
            "version '${raw[SharedPayloadKeys.CONTRACT_VERSION]}'; $FEATURE_TASK_RUNTIME_INCOMPATIBLE_RECORD_GUIDANCE.",
        )
      }
      if (raw["record_kind"] != "audit_gap_progress") {
        throw InvalidWorkflowStateSchemaError(
          "Feature-task-runtime audit-gap progress artifact must have kind 'audit_gap_progress'.",
        )
      }
      val reader = durableArtifactMapReader(raw)
      return FeatureTaskRuntimeAuditGapProgress(
        criterionRefs = reader.optionalStringList("previous_criterion_refs").toSet(),
        repositoryFingerprint = reader.optionalString("previous_repository_fingerprint"),
      )
    }

    private fun requireExactAuditGapProgressFields(raw: Map<String, Any?>) {
      val expected = setOf(
        SharedPayloadKeys.CONTRACT_VERSION,
        "record_kind",
        "previous_criterion_refs",
        "previous_repository_fingerprint",
      )
      val unexpected = raw.keys - expected
      if (unexpected.isNotEmpty()) {
        throw InvalidWorkflowStateSchemaError(
          "Feature-task-runtime audit-gap progress artifact contains unsupported fields; " +
            "$FEATURE_TASK_RUNTIME_INCOMPATIBLE_RECORD_GUIDANCE.",
        )
      }
    }
  }
}
