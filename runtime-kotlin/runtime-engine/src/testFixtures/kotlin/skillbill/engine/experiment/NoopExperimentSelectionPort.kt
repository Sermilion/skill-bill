package skillbill.engine.experiment
import skillbill.experiment.model.ExperimentExecutionMode
import skillbill.ports.experiment.selection.ExperimentLaunchSelection
import skillbill.ports.experiment.selection.ExperimentSelectionPort
import java.nio.file.Path

object NoopExperimentSelectionPort : ExperimentSelectionPort {
  override fun resolveForLaunch(
    repoRoot: Path,
    parameter: String?,
    mode: ExperimentExecutionMode,
    savedSelection: List<String>?,
  ): ExperimentLaunchSelection = ExperimentLaunchSelection(
    normalizedNames = emptyList(),
    descriptors = emptyList(),
    availabilitySummary = "no experiments selected",
  )
}
