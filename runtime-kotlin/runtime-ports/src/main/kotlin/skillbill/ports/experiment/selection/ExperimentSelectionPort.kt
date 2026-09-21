package skillbill.ports.experiment.selection

import skillbill.experiment.model.ExperimentExecutionMode
import java.nio.file.Path
import skillbill.ports.experiment.selection.model.ExperimentLaunchSelection as ExperimentLaunchSelectionModel

typealias ExperimentLaunchSelection = ExperimentLaunchSelectionModel

interface ExperimentSelectionPort {
  fun resolveForLaunch(
    repoRoot: Path,
    parameter: String?,
    mode: ExperimentExecutionMode,
    savedSelection: List<String>?,
  ): ExperimentLaunchSelection
}
