package skillbill.engine.featuretask.model.core

sealed interface FeatureTaskRuntimeCheckpointDecision {
  /**
   * Stage [ownedPaths] and commit. [adoptedPaths] is the subset that was already staged or modified
   * outside the remembered inventory; it is reported, never refused.
   */
  data class Stage(
    val ownedPaths: List<String>,
    val adoptedPaths: List<String> = emptyList(),
  ) : FeatureTaskRuntimeCheckpointDecision

  /** The dirty tree is empty, so there is nothing to checkpoint. */
  data object Skip : FeatureTaskRuntimeCheckpointDecision

  /** Refuse: a git read or preservation step failed before staging could proceed safely. */
  data class Block(val reason: String) : FeatureTaskRuntimeCheckpointDecision
}

data class FeatureTaskRuntimeCheckpointScopeInput(
  val issueKey: String,
  val ownedPaths: List<String>,
  val phaseIntroducedPaths: List<String>,
  val worktreeDeltaPaths: List<String>,
  val foreignStagedPaths: List<String> = emptyList(),
  val concurrentlyModifiedOwnedPaths: List<String> = emptyList(),
  val deletedPaths: List<String> = emptyList(),
  val workflowId: String? = null,
)
