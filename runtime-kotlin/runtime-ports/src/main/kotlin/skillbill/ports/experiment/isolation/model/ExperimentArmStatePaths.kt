package skillbill.ports.experiment.isolation.model

import java.nio.file.Path

data class ExperimentArmStatePaths(
  val runtimeDatabase: Path? = null,
  val learningStore: Path? = null,
  val graphIndex: Path? = null,
  val buildOutput: Path? = null,
  val writableCache: Path? = null,
  val worktreeEditJournal: Path? = null,
  val sharedHostCaches: List<Path> = emptyList(),
)
