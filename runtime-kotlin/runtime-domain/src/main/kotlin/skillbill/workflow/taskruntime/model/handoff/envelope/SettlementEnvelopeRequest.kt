package skillbill.workflow.taskruntime.model.handoff.envelope
import skillbill.workflow.taskruntime.model.audit.entries
import skillbill.workflow.taskruntime.model.feature.entries
import skillbill.workflow.taskruntime.model.handoff.task.envelope
import skillbill.workflow.taskruntime.model.handoff.task.wireValue
import skillbill.workflow.taskruntime.model.persistence.task.runtime.goal.entries
import skillbill.workflow.taskruntime.model.persistence.task.runtime.run.entries
import skillbill.workflow.taskruntime.model.persistence.task.runtime.run.wireValue
import skillbill.workflow.taskruntime.model.phase.envelope
import skillbill.workflow.taskruntime.model.repair.task.entries
import skillbill.workflow.taskruntime.model.validation.entries
import skillbill.workflow.taskruntime.model.validation.wireValue

enum class SettlementStatus(val wireValue: String) {
  COMPLETED("completed"),
  BLOCKED("blocked"),
  FAILED("failed"),
  ;

  companion object {
    fun fromWire(value: String): SettlementStatus? = when (value) {
      "complete" -> COMPLETED
      "block" -> BLOCKED
      "fail" -> FAILED
      else -> entries.firstOrNull { it.wireValue == value }
    }
  }
}

data class SettlementEnvelopeRequest(
  val phaseId: String,
  val status: SettlementStatus,
  val value: String,
  val summary: String,
  val prompt: String? = null,
  val verdict: String? = null,
  val failureDisposition: String? = null,
) {
  constructor(
    phaseId: String,
    status: String,
    value: String,
    summary: String,
    prompt: String? = null,
    verdict: String? = null,
    failureDisposition: String? = null,
  ) : this(
    phaseId = phaseId,
    status = requireNotNull(SettlementStatus.fromWire(status)) { "Unknown settlement status '$status'." },
    value = value,
    summary = summary,
    prompt = prompt,
    verdict = verdict,
    failureDisposition = failureDisposition,
  )
}
