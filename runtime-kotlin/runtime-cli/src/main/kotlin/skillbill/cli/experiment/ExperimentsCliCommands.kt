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
import skillbill.contracts.experiment.ExperimentReportPayloadKeys
import skillbill.contracts.experiment.ExperimentStatsPayloadKeys
import skillbill.engine.experiment.report.ExperimentReportProjector
import skillbill.engine.experiment.report.ExperimentStatsProjector
import skillbill.engine.goalrunner.experiment.ExperimentNavigationPairCoordinator
import skillbill.engine.goalrunner.experiment.ExperimentNavigationPairRequest
import skillbill.engine.goalrunner.experiment.ExperimentNavigationPairSource
import skillbill.engine.goalrunner.experiment.parseNavigationAcceptanceCriteria
import skillbill.error.shellcontent.ExperimentNavigationSpecError
import skillbill.ports.experiment.codegraph.CodeGraphToolInstallPort
import skillbill.ports.experiment.codegraph.model.CodeGraphToolInstallRequest
import skillbill.ports.experiment.pair.ExperimentPairOwnerPort
import kotlin.io.path.readBytes
import kotlin.io.path.readText

@Inject
class ExperimentsCommand(
  run: ExperimentsRunCommand,
  report: ExperimentsReportCommand,
  stats: ExperimentsStatsCommand,
  codegraph: ExperimentsCodeGraphCommand,
) : DocumentedNoOpCliCommand("experiments", "Experiment pair execution and reporting.") {
  init {
    subcommands(run, report, stats, codegraph)
  }
}

@Inject
class ExperimentsCodeGraphCommand(
  install: ExperimentsCodeGraphInstallCommand,
) : DocumentedNoOpCliCommand("codegraph", "Managed CodeGraph tools.") {
  init {
    subcommands(install)
  }
}

@Inject
class ExperimentsCodeGraphInstallCommand(
  private val installPort: CodeGraphToolInstallPort,
) : DocumentedCliCommand("install", "Install the pinned CodeGraph tool.") {
  private val userHome by option("--user-home").path(mustExist = true, mustBeReadable = true).required()

  override fun run() {
    val installed = installPort.install(
      CodeGraphToolInstallRequest(
        userHome = userHome,
        pairId = "manual-install",
      ),
    )
    echo("CodeGraph installed: ${installed.releaseTag} at ${installed.binaryPath}")
  }
}

@Inject
class ExperimentsRunCommand(
  private val navigationCoordinator: ExperimentNavigationPairCoordinator,
) : DocumentedCliCommand("run", "Run a navigation experiment pair.") {
  private val name by argument()
  private val repo by option("--repo").path(mustExist = true, mustBeReadable = true).required()
  private val spec by option("--spec").path(mustExist = true, mustBeReadable = true).required()
  private val revision by option("--revision")
    .default("HEAD")

  override fun run() {
    val specPath = spec
    val specText = specPath.readText()
    val criteria = parseNavigationAcceptanceCriteria(specText)
    if (criteria.isEmpty()) {
      throw ExperimentNavigationSpecError(
        path = specPath.toString(),
        reason = "the governed acceptance criteria section is missing or empty",
      )
    }
    val pairId = navigationCoordinator.run(
      ExperimentNavigationPairRequest(
        source = ExperimentNavigationPairSource(
          name = name,
          repoRoot = repo,
          revision = revision,
          specBytes = specPath.readBytes(),
          criteria = criteria,
        ),
      ),
    )
    echo("navigation pair started: $pairId revision=$revision")
  }
}

@Inject
class ExperimentsReportCommand(
  private val pairOwner: ExperimentPairOwnerPort,
) : DocumentedCliCommand("report", "Render an experiment report.") {
  private val pairId by argument()
  private val format by option("--format").default("text")

  override fun run() {
    val state = pairOwner.load(pairId) ?: error("unknown pair $pairId")
    val projection = ExperimentReportProjector.project(state.pairPayload, state.executionMode.wireValue)
      .also { pairOwner.saveReport(pairId, it) }
    echo(
      if (format == "json") {
        ExperimentReportProjector.renderJson(projection)
      } else {
        ExperimentReportProjector.renderText(projection)
      },
    )
  }
}

@Inject
class ExperimentsStatsCommand(
  private val pairOwner: ExperimentPairOwnerPort,
) : DocumentedCliCommand("stats", "Experiment cohort statistics.") {
  private val name by option("--name")

  override fun run() {
    val reports = pairOwner.listReports().filter { report ->
      name == null ||
        (report[ExperimentReportPayloadKeys.SELECTED_EXPERIMENT_NAMES] as? List<*>)
          ?.any { it?.toString() == name } == true
    }
    val stats = ExperimentStatsProjector.project(reports)
    val goal = stats[ExperimentStatsPayloadKeys.GOAL]
    val navigation = stats[ExperimentStatsPayloadKeys.NAVIGATION]
    echo("experiment stats: goal=$goal navigation=$navigation")
  }
}
