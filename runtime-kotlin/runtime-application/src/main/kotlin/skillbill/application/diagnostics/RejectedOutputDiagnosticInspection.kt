package skillbill.application.diagnostics

import me.tatarka.inject.annotations.Inject
import skillbill.application.diagnostics.model.RejectedOutputDiagnosticAmbiguousSelectorError
import skillbill.application.diagnostics.model.RejectedOutputDiagnosticInspectionResult
import skillbill.application.diagnostics.model.RejectedOutputDiagnosticMetadata
import skillbill.error.core.RejectedOutputDiagnosticError
import skillbill.ports.db.DatabaseSessionFactory
import skillbill.ports.diagnostics.RejectedOutputDiagnosticMetadataValidator
import skillbill.ports.diagnostics.model.RejectedOutputDiagnostic
import skillbill.ports.diagnostics.model.RejectedOutputDiagnosticSelector
import skillbill.ports.persistence.UnitOfWork
import java.time.Clock

@Inject
class RejectedOutputDiagnosticInspection(
  private val database: DatabaseSessionFactory,
  private val metadataValidator: RejectedOutputDiagnosticMetadataValidator,
  private val clock: Clock,
) {
  fun inspect(
    selector: RejectedOutputDiagnosticSelector,
    rawOutput: Boolean,
  ): RejectedOutputDiagnosticInspectionResult =
    database.selfManagedWrite { unitOfWork ->
      val service = unitOfWork.diagnosticService()
      val matches = service.inspect(selector)
      if (matches.isEmpty()) throw RejectedOutputDiagnosticError.Absent(selector.workflowId)
      if (rawOutput) {
        if (matches.size != 1) throw RejectedOutputDiagnosticAmbiguousSelectorError(matches.size)
        RejectedOutputDiagnosticInspectionResult.RawBytes(service.readRaw(matches.single().identity))
      } else {
        RejectedOutputDiagnosticInspectionResult.Metadata(matches.map { it.toMetadata() })
      }
    }

  fun cleanup(selector: RejectedOutputDiagnosticSelector): Int =
    database.transaction { unitOfWork ->
      unitOfWork.diagnosticService().delete(selector)
    }

  private fun UnitOfWork.diagnosticService(): RejectedOutputDiagnosticService =
    RejectedOutputDiagnosticService(
      rejectedOutputDiagnostics ?: throw RejectedOutputDiagnosticError.Persistence("repository-unavailable"),
      rejectedOutputDiagnosticPermissions ?: throw RejectedOutputDiagnosticError.Permission("permissions-unavailable"),
      metadataValidator,
      clock = clock,
    )
}

private fun RejectedOutputDiagnostic.toMetadata(): RejectedOutputDiagnosticMetadata =
  RejectedOutputDiagnosticMetadata(
    identity = identity,
    workflowId = workflowId,
    phaseId = phaseId,
    attempt = attempt,
    repairTurn = repairTurn,
    rule = rule,
    path = path,
    reason = reason,
    agentId = agentId,
    model = model,
    recordedAt = recordedAt,
    byteSize = byteSize,
    sha256 = sha256,
    lifecycle = lifecycle,
  )
