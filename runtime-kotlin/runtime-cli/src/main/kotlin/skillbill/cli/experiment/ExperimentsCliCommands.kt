package skillbill.cli.experiment
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.path
import me.tatarka.inject.annotations.Inject
import skillbill.cli.kernel.cli.DocumentedCliCommand
import skillbill.cli.kernel.cli.DocumentedNoOpCliCommand
import skillbill.error.shellcontent.ExperimentNavigationSpecError
import skillbill.ports.experiment.navigation.ExperimentNavigationRunPort
import skillbill.ports.experiment.navigation.model.ExperimentNavigationRunRequest
import skillbill.ports.experiment.pair.ExperimentPairReportPort
import kotlin.io.path.readBytes
import kotlin.io.path.readText

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
  private val navigationRunPort: ExperimentNavigationRunPort,
) : DocumentedCliCommand("run", "Run a navigation experiment pair.") {
  private val name by argument()
  private val repo by option("--repo").path(mustExist = true, mustBeReadable = true).required()
  private val spec by option("--spec").path(mustExist = true, mustBeReadable = true).required()
  private val revision by option("--revision")
    .default("HEAD")

  override fun run() {
    val specPath = spec
    val specText = specPath.readText()
    val criteria = navigationRunPort.acceptanceCriteria(specText)
    if (criteria.isEmpty()) {
      throw ExperimentNavigationSpecError(
        path = specPath.toString(),
        reason = "the governed acceptance criteria section is missing or empty",
      )
    }
    val pairId =
      navigationRunPort.run(
        ExperimentNavigationRunRequest(
          name = name,
          repoRoot = repo,
          revision = revision,
          specBytes = specPath.readBytes(),
          acceptanceCriteria = criteria,
        ),
      )
    echo("navigation pair started: $pairId revision=$revision")
  }
}

@Inject
class ExperimentsReportCommand(
  private val pairReportPort: ExperimentPairReportPort,
) : DocumentedCliCommand("report", "Render an experiment report.") {
  private val pairId by argument()
  private val format by option("--format").default("text")

  override fun run() {
    echo(pairReportPort.renderReport(pairId, format))
  }
}

@Inject
class ExperimentsStatsCommand(
  private val pairReportPort: ExperimentPairReportPort,
) : DocumentedCliCommand("stats", "Experiment cohort statistics.") {
  override fun run() {
    echo(pairReportPort.renderStatsLine())
  }
}
