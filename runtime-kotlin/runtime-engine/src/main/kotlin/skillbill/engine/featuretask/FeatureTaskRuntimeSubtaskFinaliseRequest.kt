package skillbill.engine.featuretask

import skillbill.engine.featuretask.model.FeatureTaskRuntimeCommitPushHandoff
import skillbill.engine.featuretask.model.FeatureTaskRuntimeSubtaskCommitIdentity

data class FeatureTaskRuntimeSubtaskFinaliseRequest(
  val identity: FeatureTaskRuntimeSubtaskCommitIdentity,
  val durableCommitSha: String?,
  val sequenceNumber: Int,
  val handoff: FeatureTaskRuntimeCommitPushHandoff,
  val metadata: FeatureTaskRuntimeCheckpointMetadata,
  val manifestCommitSha: String? = null,
)
