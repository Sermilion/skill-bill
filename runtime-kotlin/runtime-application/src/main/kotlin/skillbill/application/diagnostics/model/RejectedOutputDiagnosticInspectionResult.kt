package skillbill.application.diagnostics.model

import skillbill.ports.diagnostics.model.RejectedOutputLifecycle
import java.time.Instant

sealed interface RejectedOutputDiagnosticInspectionResult {
  data class Metadata(
    val records: List<RejectedOutputDiagnosticMetadata>,
  ) : RejectedOutputDiagnosticInspectionResult

  data class RawBytes(val bytes: ByteArray) : RejectedOutputDiagnosticInspectionResult
}

data class RejectedOutputDiagnosticMetadata(
  val identity: String,
  val workflowId: String,
  val phaseId: String,
  val attempt: Int,
  val repairTurn: Int,
  val rule: String,
  val path: String,
  val reason: String,
  val agentId: String,
  val model: String,
  val recordedAt: Instant,
  val byteSize: Long,
  val sha256: String,
  val lifecycle: RejectedOutputLifecycle,
)
