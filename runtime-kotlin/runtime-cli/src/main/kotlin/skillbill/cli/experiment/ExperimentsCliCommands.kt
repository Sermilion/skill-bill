package skillbill.cli.experiment
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.path
import me.tatarka.inject.annotations.Inject
import skillbill.cli.kernel.cli.CliRunState
import skillbill.cli.kernel.cli.DocumentedCliCommand
import skillbill.cli.kernel.cli.DocumentedNoOpCliCommand
import skillbill.cli.kernel.cli.formatOption
import skillbill.cli.model.CliFormat
import skillbill.ports.experiment.navigation.ExperimentNavigationRunPort
import skillbill.ports.experiment.navigation.model.ExperimentNavigationRunRequest
import skillbill.ports.experiment.pair.ExperimentPairReportPort

@Inject
class ExperimentsCommand(
  run: ExperimentsRunCommand,
  report: ExperimentsReportCommand,
  stats: ExperimentsStatsCommand,
) : DocumentedNoOpCliCommand("experiments", "Experiment pair execution and reporting.") {
  init {
    subcommands(run, report, stats)
  }
}

@Inject
class ExperimentsRunCommand(
  private val state: CliRunState,
  private val navigationRunPort: ExperimentNavigationRunPort,
) : DocumentedCliCommand("run", "Run a navigation experiment pair.") {
  private val name by argument()
  private val repo by option("--repo").path(mustExist = true, mustBeReadable = true).required()
  private val spec by option("--spec").path(mustExist = true, mustBeReadable = true).required()
  private val revision by option("--revision")
    .default("HEAD")

  override fun run() {
    val pairId =
      navigationRunPort.run(
        ExperimentNavigationRunRequest(
          name = name,
          repoRoot = repo,
          revision = revision,
          specPath = spec,
        ),
      )
    state.completeText("navigation pair started: $pairId revision=$revision\n", emptyMap())
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
    state.complete(pairReportPort.statsPayload(), CliFormat.TEXT)
  }
}
