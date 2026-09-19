package skillbill.cli.featuretask

import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import me.tatarka.inject.annotations.Inject
import skillbill.application.diagnostics.RejectedOutputDiagnosticCliSession
import skillbill.application.diagnostics.model.RejectedOutputDiagnosticCliResult
import skillbill.cli.kernel.cli.CliRunState
import skillbill.cli.kernel.cli.DocumentedCliCommand
import skillbill.mcp.shared.int
import skillbill.ports.diagnostics.model.RejectedOutputDiagnosticSelector
@Inject
class RejectedOutputInspectCliCommand(
  private val session: RejectedOutputDiagnosticCliSession,
  private val state: CliRunState,
) : DocumentedCliCommand(
  "rejected-output",
  "Inspect rejected phase output metadata, or emit one exact stored body with --raw-output.",
) {
  private val workflowId by option("--workflow", help = "Workflow identifier.").required()
  private val phaseId by option("--phase", help = "Optional phase selector.")
  private val attempt by option("--attempt", help = "Optional attempt selector.").int()
  private val repairTurn by option(
    "--repair-turn",
    help = "Optional validation-gate repair-turn selector within one attempt; 0 is an ordinary attempt.",
  ).int()
  private val rawOutput by option(
    "--raw-output",
    help = "Write the exact stored response bytes; the selector must resolve to one record.",
  ).flag(default = false)

  override fun run() {
    val selector = RejectedOutputDiagnosticSelector(workflowId, phaseId, attempt, repairTurn)
    when (val result = session.inspect(selector, rawOutput)) {
      is RejectedOutputDiagnosticCliResult.RawBytes ->
        state.completeRaw(result.bytes)
      is RejectedOutputDiagnosticCliResult.MetadataLines -> {
        val text = result.lines.joinToString("\n", postfix = if (result.lines.isEmpty()) "" else "\n")
        state.completeText(text, emptyMap())
      }
    }
  }
}

@Inject
class RejectedOutputCleanupCliCommand(
  private val session: RejectedOutputDiagnosticCliSession,
  private val state: CliRunState,
) : DocumentedCliCommand(
  "rejected-output-cleanup",
  "Delete rejected-output diagnostics selected within one workflow.",
) {
  private val workflowId by option("--workflow", help = "Workflow identifier.").required()
  private val phaseId by option("--phase", help = "Optional phase selector.")
  private val attempt by option("--attempt", help = "Optional attempt selector.").int()
  private val repairTurn by option(
    "--repair-turn",
    help = "Optional validation-gate repair-turn selector within one attempt; 0 is an ordinary attempt.",
  ).int()

  override fun run() {
    val deleted = session.cleanup(
      RejectedOutputDiagnosticSelector(workflowId, phaseId, attempt, repairTurn),
    )
    state.completeText("deleted=$deleted\n", mapOf("deleted" to deleted))
  }
}
