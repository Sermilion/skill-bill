package skillbill.application.diagnostics.model

sealed interface RejectedOutputDiagnosticCliResult {
  data class MetadataLines(val lines: List<String>) : RejectedOutputDiagnosticCliResult

  data class RawBytes(val bytes: ByteArray) : RejectedOutputDiagnosticCliResult
}
