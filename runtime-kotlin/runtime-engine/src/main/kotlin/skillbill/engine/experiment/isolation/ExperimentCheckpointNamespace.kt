package skillbill.engine.experiment.isolation

import skillbill.experiment.model.ExperimentArmId
import skillbill.workflow.taskruntime.model.persistence.task.runtime.checkpoint.FEATURE_TASK_RUNTIME_CHECKPOINT_REF_NAMESPACE
import java.nio.file.Path

object ExperimentCheckpointNamespace {
  fun prefix(
    pairId: String,
    armId: ExperimentArmId,
  ): String = "experiment/$pairId/${armId.wireValue}"

  fun forRepositoryRoot(repoRoot: Path): String {
    val segments = repoRoot.toAbsolutePath().normalize().map(Path::toString)
    val marker = segments.indexOf(".skill-bill-experiments")
    val pairId = segments.getOrNull(marker + 1)
    val armId = segments.getOrNull(marker + 2)?.let(ExperimentArmId::fromWire)
    return if (marker >= 0 && pairId != null && armId != null) {
      prefix(pairId, armId)
    } else {
      FEATURE_TASK_RUNTIME_CHECKPOINT_REF_NAMESPACE
    }
  }
}
