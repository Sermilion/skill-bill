package skillbill.application.diagnostics

import me.tatarka.inject.annotations.Inject
import skillbill.application.diagnostics.model.RejectedOutputDiagnosticCliResult
import skillbill.error.core.RejectedOutputDiagnosticError
import skillbill.ports.db.DatabaseSessionFactory
import skillbill.ports.diagnostics.RejectedOutputDiagnosticMetadataValidator
import skillbill.ports.diagnostics.model.RejectedOutputDiagnostic
import skillbill.ports.diagnostics.model.RejectedOutputDiagnosticSelector
import skillbill.ports.persistence.UnitOfWork
import java.time.Clock

@Inject
class RejectedOutputDiagnosticCliSession(
  private val database: DatabaseSessionFactory,
  private val metadataValidator: RejectedOutputDiagnosticMetadataValidator,
  private val clock: Clock,
) {
  fun inspect(
    selector: RejectedOutputDiagnosticSelector,
    rawOutput: Boolean,
  ): RejectedOutputDiagnosticCliResult =
    database.selfManagedWrite { unitOfWork ->
      val service = unitOfWork.diagnosticService(metadataValidator, clock)
      val matches = service.inspect(selector)
      if (matches.isEmpty()) throw RejectedOutputDiagnosticError.Absent(selector.workflowId)
      if (rawOutput) {
        if (matches.size != 1) {
          throw RejectedOutputDiagnosticError.Retrieval(
            "raw output requires a selector resolving to exactly one diagnostic; " +
              "an attempt that ran a validation-gate repair cycle holds one per repair turn, " +
              "so add --repair-turn (the metadata listing prints each turn)",
          )
        }
        RejectedOutputDiagnosticCliResult.RawBytes(service.readRaw(matches.single().identity))
      } else {
        RejectedOutputDiagnosticCliResult.MetadataLines(matches.map { it.safeLine() })
      }
    }

  fun cleanup(selector: RejectedOutputDiagnosticSelector): Int =
    database.transaction { unitOfWork ->
      unitOfWork.diagnosticService(metadataValidator, clock).delete(selector)
    }

  private fun UnitOfWork.diagnosticService(
    metadataValidator: RejectedOutputDiagnosticMetadataValidator,
    clock: Clock,
  ): RejectedOutputDiagnosticService =
    RejectedOutputDiagnosticService(
      rejectedOutputDiagnostics ?: throw RejectedOutputDiagnosticError.Persistence("repository-unavailable"),
      rejectedOutputDiagnosticPermissions ?: throw RejectedOutputDiagnosticError.Permission("permissions-unavailable"),
      metadataValidator,
      clock = clock,
    )
}

private fun RejectedOutputDiagnostic.safeLine(): String =
  listOf(
    "identity=${identity.safeField()}",
    "workflow=${workflowId.safeField()}",
    "phase=${phaseId.safeField()}",
    "attempt=$attempt",
    "repair_turn=$repairTurn",
    "rule=${rule.safeField()}",
    "path=${path.safeField()}",
    "reason=${reason.safeField()}",
    "agent=${agentId.safeField()}",
    "model=${model.safeField()}",
    "recorded_at=$recordedAt",
    "byte_size=$byteSize",
    "sha256=$sha256",
    "lifecycle=${lifecycle.name.lowercase()}",
  ).joinToString(" ")

private const val CONTROL_CHARACTER_LIMIT: Int = 0x20
private const val DELETE_CHARACTER_CODE: Int = 0x7f
private const val HEX_RADIX: Int = 16
private const val UNICODE_ESCAPE_WIDTH: Int = 4

private fun String.safeField(): String =
  buildString {
    append('"')
    this@safeField.forEach { character ->
      when (character) {
        '\\' -> append("\\\\")
        '"' -> append("\\\"")
        '\n' -> append("\\n")
        '\r' -> append("\\r")
        '\t' -> append("\\t")
        else ->
          if (character.code < CONTROL_CHARACTER_LIMIT || character.code == DELETE_CHARACTER_CODE) {
            append("\\u")
            append(character.code.toString(HEX_RADIX).padStart(UNICODE_ESCAPE_WIDTH, '0'))
          } else {
            append(character)
          }
      }
    }
    append('"')
  }
