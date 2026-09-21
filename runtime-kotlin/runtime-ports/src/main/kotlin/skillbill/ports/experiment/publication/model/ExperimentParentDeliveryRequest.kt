package skillbill.ports.experiment.publication.model

import java.nio.file.Path

data class ExperimentParentDeliveryRequest(
  val issueKey: String,
  val controlRepoRoot: Path,
)
