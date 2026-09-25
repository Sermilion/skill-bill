package skillbill.ports.experiment.selection

import skillbill.experiment.model.ExperimentExecutionMode
import skillbill.ports.experiment.selection.model.ExperimentLaunchSelection
import java.nio.file.Path

object NoExperimentSelection : ExperimentSelectionPort {
  override fun resolveForLaunch(
    repoRoot: Path,
    parameter: String?,
    mode: ExperimentExecutionMode,
    savedSelection: List<String>?,
  ): ExperimentLaunchSelection =
    ExperimentLaunchSelection(
      normalizedNames = emptyList(),
      descriptors = emptyList(),
      availabilitySummary = "no experiments selected",
    )
}
