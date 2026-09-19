package skillbill.workflow.taskruntime.model.review
import skillbill.workflow.taskruntime.model.audit.entries
import skillbill.workflow.taskruntime.model.feature.entries
import skillbill.workflow.taskruntime.model.handoff.envelope.value
import skillbill.workflow.taskruntime.model.handoff.task.value
import skillbill.workflow.taskruntime.model.handoff.task.wireValue
import skillbill.workflow.taskruntime.model.persistence.artifact.value
import skillbill.workflow.taskruntime.model.persistence.task.runtime.goal.entries
import skillbill.workflow.taskruntime.model.persistence.task.runtime.implementation.value
import skillbill.workflow.taskruntime.model.persistence.task.runtime.run.entries
import skillbill.workflow.taskruntime.model.persistence.task.runtime.run.value
import skillbill.workflow.taskruntime.model.persistence.task.runtime.run.wireValue
import skillbill.workflow.taskruntime.model.repair.task.entries
import skillbill.workflow.taskruntime.model.repair.task.value
import skillbill.workflow.taskruntime.model.validation.APPROVED
import skillbill.workflow.taskruntime.model.validation.CHANGES_REQUESTED
import skillbill.workflow.taskruntime.model.validation.FeatureTaskRuntimeVerdict
import skillbill.workflow.taskruntime.model.validation.entries
import skillbill.workflow.taskruntime.model.validation.wireValue
enum class FeatureTaskRuntimeReviewSeverity(val wireValue: String) {
  BLOCKER("blocker"),
  MAJOR("major"),
  MINOR("minor"),
  NIT("nit"),
  ;

  val blocksAdvance: Boolean
    get() = this == BLOCKER || this == MAJOR

  val requiresRemediation: Boolean
    get() = this == BLOCKER || this == MAJOR

  companion object {
    fun fromWire(value: String): FeatureTaskRuntimeReviewSeverity =
      entries.firstOrNull { it.wireValue == value.trim().lowercase() }
        ?: throw IllegalArgumentException(
          "Unknown feature-task-runtime review severity '$value'. " +
            "Allowed: ${entries.joinToString { it.wireValue }}.",
        )
  }
}

data class FeatureTaskRuntimeReviewFinding(
  val severity: FeatureTaskRuntimeReviewSeverity,
  val message: String,
) {
  init {
    require(message.isNotBlank()) { "FeatureTaskRuntimeReviewFinding.message must be non-blank." }
  }
}

data class FeatureTaskRuntimeReviewVerdict(
  val findings: List<FeatureTaskRuntimeReviewFinding>,
) {
  val verdict: FeatureTaskRuntimeVerdict
    get() = if (findings.any { it.severity.requiresRemediation }) {
      FeatureTaskRuntimeVerdict.CHANGES_REQUESTED
    } else {
      FeatureTaskRuntimeVerdict.APPROVED
    }

  val remediationFindings: List<FeatureTaskRuntimeReviewFinding>
    get() = findings.filter { it.severity.requiresRemediation }

  val unresolvedFindings: List<FeatureTaskRuntimeReviewFinding>
    get() = findings.filter { it.severity.blocksAdvance }
}
