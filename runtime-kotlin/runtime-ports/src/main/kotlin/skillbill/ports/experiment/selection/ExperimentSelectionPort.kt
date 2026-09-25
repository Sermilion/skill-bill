package skillbill.ports.experiment.selection

import skillbill.experiment.model.ExperimentExecutionMode
import skillbill.ports.experiment.selection.model.ExperimentLaunchSelection
import java.nio.file.Path

interface ExperimentSelectionPort {
  fun resolveForLaunch(
    repoRoot: Path,
    parameter: String?,
    mode: ExperimentExecutionMode,
    savedSelection: List<String>?,
  ): ExperimentLaunchSelection
}
