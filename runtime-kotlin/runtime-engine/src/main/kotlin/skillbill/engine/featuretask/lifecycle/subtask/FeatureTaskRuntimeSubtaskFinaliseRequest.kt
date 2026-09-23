package skillbill.engine.featuretask.lifecycle.subtask

import skillbill.engine.featuretask.lifecycle.checkpoint.FeatureTaskRuntimeCheckpointMetadata
import skillbill.engine.featuretask.model.subtask.FeatureTaskRuntimeCommitPushHandoff
import skillbill.engine.featuretask.model.subtask.FeatureTaskRuntimeSubtaskCommitIdentity

data class FeatureTaskRuntimeSubtaskFinaliseRequest(
  val identity: FeatureTaskRuntimeSubtaskCommitIdentity,
  val durableCommitSha: String?,
  val sequenceNumber: Int,
  val handoff: FeatureTaskRuntimeCommitPushHandoff,
  val metadata: FeatureTaskRuntimeCheckpointMetadata,
  val manifestCommitSha: String? = null,
)
