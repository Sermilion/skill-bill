package skillbill.workflow.taskruntime.model.repair
import skillbill.workflow.taskruntime.model.audit.entries
import skillbill.workflow.taskruntime.model.core.raw
import skillbill.workflow.taskruntime.model.feature.entries
import skillbill.workflow.taskruntime.model.handoff.task.wireValue
import skillbill.workflow.taskruntime.model.persistence.task.runtime.goal.entries
import skillbill.workflow.taskruntime.model.persistence.task.runtime.run.entries
import skillbill.workflow.taskruntime.model.persistence.task.runtime.run.wireValue
import skillbill.workflow.taskruntime.model.phase.raw
import skillbill.workflow.taskruntime.model.repair.task.entries
import skillbill.workflow.taskruntime.model.validation.entries
import skillbill.workflow.taskruntime.model.validation.raw
import skillbill.workflow.taskruntime.model.validation.wireValue

enum class CorrectiveRepairResponseAvailability(val wireValue: String) {
  EXACT_RESPONSE_INCLUDED("exact_response_included"),
  RESPONSE_ALREADY_TRUNCATED("response_already_truncated"),
  RESPONSE_EXCEEDS_REPAIR_BUDGET("response_exceeds_repair_budget"),
  RESPONSE_UNAVAILABLE("response_unavailable"),
  ;

  companion object {
    fun fromWire(raw: String): CorrectiveRepairResponseAvailability = entries.firstOrNull { it.wireValue == raw }
      ?: throw IllegalArgumentException(
        "CorrectiveRepairResponseAvailability '$raw' is not a declared availability state.",
      )
  }
}

enum class CorrectiveRepairInclusionReason(val wireValue: String) {
  EXACT_WITHIN_BUDGET("exact_within_budget"),
  CAPTURE_ALREADY_TRUNCATED("capture_already_truncated"),
  CAPTURE_EXCEEDS_RESPONSE_BUDGET("capture_exceeds_response_budget"),
  CAPTURE_UNAVAILABLE("capture_unavailable"),
  PROMPT_FRAMING_EXCEEDS_BUDGET("prompt_framing_exceeds_budget"),
  ;

  companion object {
    fun fromWire(raw: String): CorrectiveRepairInclusionReason = entries.firstOrNull { it.wireValue == raw }
      ?: throw IllegalArgumentException(
        "CorrectiveRepairInclusionReason '$raw' is not a declared inclusion reason.",
      )
  }
}
