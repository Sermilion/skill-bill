package skillbill.cli.experiment

import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.arguments.argument
import me.tatarka.inject.annotations.Inject
import skillbill.cli.kernel.cli.CliRunState
import skillbill.cli.kernel.cli.DocumentedCliCommand
import skillbill.cli.kernel.cli.DocumentedNoOpCliCommand
import skillbill.cli.kernel.cli.formatOption
import skillbill.cli.model.CliFormat
import skillbill.contracts.JsonCodec
import skillbill.ports.experiment.pair.ExperimentPairReportPort
import skillbill.ports.experiment.pair.model.ExperimentStatsPayload

@Inject
class ExperimentsCommand(
  report: ExperimentsReportCommand,
  stats: ExperimentsStatsCommand,
) : DocumentedNoOpCliCommand("experiments", "Experiment pair execution and reporting.") {
  init {
    subcommands(report, stats)
  }
}

@Inject
class ExperimentsReportCommand(
  private val state: CliRunState,
  private val pairReportPort: ExperimentPairReportPort,
) : DocumentedCliCommand("report", "Render an experiment report.") {
  private val pairId by argument()
  private val format by formatOption()

  override fun run() {
    state.completeText(
      pairReportPort.renderReport(pairId, format.wireName),
      emptyMap(),
    )
  }
}

@Inject
class ExperimentsStatsCommand(
  private val state: CliRunState,
  private val pairReportPort: ExperimentPairReportPort,
) : DocumentedCliCommand("stats", "Experiment cohort statistics.") {
  override fun run() {
    state.complete(pairReportPort.statsPayload().toCliPayload(), CliFormat.TEXT)
  }
}

private fun ExperimentStatsPayload.toCliPayload(): Map<String, Any?> =
  JsonCodec.anyToStringAnyMap(
    JsonCodec.jsonElementToValue(
      requireNotNull(JsonCodec.parseObjectOrNull(json)),
    ),
  ) ?: error("Experiment stats payload must decode to an object.")
