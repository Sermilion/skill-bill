package skillbill.workflow.taskruntime.model.repair.task
import skillbill.workflow.taskruntime.model.audit.FeatureTaskRuntimeDiagnosticFailureClass
import skillbill.workflow.taskruntime.model.phase.FeatureTaskRuntimePhaseOutputRepairEvidence
import skillbill.workflow.taskruntime.model.repair.CorrectiveRepairCapturedResponse
import skillbill.workflow.taskruntime.model.repair.CorrectiveRepairDiagnosticLocator
import skillbill.workflow.taskruntime.model.repair.CorrectiveRepairPromptProjection

const val FEATURE_TASK_RUNTIME_CORRECTIVE_REPAIR_CONTEXT_CONTRACT_VERSION: String = "0.1"

data class FeatureTaskRuntimeCorrectiveRepairContext(
  val phaseId: String,
  val attempt: Int,
  val rejectionRule: String,
  val rejectionPath: String,
  val payloadFreeConstraint: String,
  val diagnosticLocator: CorrectiveRepairDiagnosticLocator?,
  val captured: CorrectiveRepairCapturedResponse,
  val repairTurn: Int? = null,
  val budget: FeatureTaskRuntimeCorrectiveRepairBudget = FeatureTaskRuntimeCorrectiveRepairBudget.DEFAULT,
  val acceptedAfterStructuralRepair: Boolean = false,
  val structuralRepairEvidence: FeatureTaskRuntimePhaseOutputRepairEvidence? = null,
  val diagnosticDegradationClass: FeatureTaskRuntimeDiagnosticFailureClass? = null,
  val contractVersion: String = FEATURE_TASK_RUNTIME_CORRECTIVE_REPAIR_CONTEXT_CONTRACT_VERSION,
) {
  init {
    require(contractVersion == FEATURE_TASK_RUNTIME_CORRECTIVE_REPAIR_CONTEXT_CONTRACT_VERSION) {
      "FeatureTaskRuntimeCorrectiveRepairContext.contractVersion must be " +
        "'$FEATURE_TASK_RUNTIME_CORRECTIVE_REPAIR_CONTEXT_CONTRACT_VERSION', was '$contractVersion'."
    }
    require(phaseId.isNotBlank()) { "FeatureTaskRuntimeCorrectiveRepairContext.phaseId must be non-blank." }
    require(attempt >= 0) { "FeatureTaskRuntimeCorrectiveRepairContext.attempt must be >= 0, was $attempt." }
    require(repairTurn == null || repairTurn >= 0) {
      "FeatureTaskRuntimeCorrectiveRepairContext.repairTurn must be >= 0 when present, was $repairTurn."
    }
    require(rejectionRule.isNotBlank()) {
      "FeatureTaskRuntimeCorrectiveRepairContext.rejectionRule must be non-blank."
    }
    require(rejectionPath.isNotBlank()) {
      "FeatureTaskRuntimeCorrectiveRepairContext.rejectionPath must be non-blank."
    }
    require((diagnosticLocator != null) xor (diagnosticDegradationClass != null)) {
      "FeatureTaskRuntimeCorrectiveRepairContext must carry a diagnostic locator xor a typed " +
        "diagnostic degradation class."
    }
    require(structuralRepairEvidence == null || acceptedAfterStructuralRepair) {
      "FeatureTaskRuntimeCorrectiveRepairContext.structuralRepairEvidence requires " +
        "acceptedAfterStructuralRepair=true so syntax-repair correlation cannot disagree with the flag."
    }

    val exact = captured as? CorrectiveRepairCapturedResponse.Exact
    require(exact == null || exact.utf8ByteCount <= budget.maxResponseUtf8Bytes) {
      "Exact captured response is ${exact?.utf8ByteCount} UTF-8 bytes against the " +
        "${budget.maxResponseUtf8Bytes}-byte response budget."
    }
  }

  fun promptProjection(): CorrectiveRepairPromptProjection = CorrectiveRepairPromptProjection.from(this)
}
