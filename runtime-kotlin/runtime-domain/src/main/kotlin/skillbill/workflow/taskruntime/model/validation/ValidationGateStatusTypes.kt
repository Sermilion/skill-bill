package skillbill.workflow.taskruntime.model.validation
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

enum class ValidationGateCacheMode(val wireValue: String) {
  CACHE_ELIGIBLE("cache_eligible"),
  FORCED_FULL("forced_full"),
  WARM("warm"),
  ;

  companion object {
    fun fromWire(value: String): ValidationGateCacheMode? = entries.firstOrNull { it.wireValue == value }
  }
}

enum class ValidationGateRunOutcome(val wireValue: String) {
  PASSED("passed"),
  FAILED("failed"),
  REJECTED_ZERO_WORK("rejected_zero_work"),
  ;

  companion object {
    fun fromWire(value: String): ValidationGateRunOutcome? = entries.firstOrNull { it.wireValue == value }
  }
}
