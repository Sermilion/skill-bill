package skillbill.ports.diagnostics

import skillbill.error.core.RejectedOutputDiagnosticError
import skillbill.ports.diagnostics.model.ProducerOutputEvidence
import java.time.Instant
abstract class RejectedOutputDiagnosticRepositoryDefaults : RejectedOutputDiagnosticRepository {
  open override fun retainProducerOutput(evidence: ProducerOutputEvidence) {
    throw RejectedOutputDiagnosticError.Persistence("producer-evidence-unavailable")
  }

  open override fun readProducerOutput(
    workflowId: String,
    phaseId: String,
    attempt: Int,
    agentId: String,
    generation: Int,
  ): ProducerOutputEvidence? = null

  open override fun deleteProducerOutputsBefore(before: Instant): Int = 0
}
