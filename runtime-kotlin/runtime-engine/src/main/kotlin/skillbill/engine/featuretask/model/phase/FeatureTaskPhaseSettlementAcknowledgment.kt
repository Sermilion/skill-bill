package skillbill.engine.featuretask.model.phase
import skillbill.ports.featuretask.model.FeatureTaskPhaseSettlementKind
import skillbill.workflow.taskruntime.artifact.FeatureTaskRuntimeWorkflowArtifactMap
data class FeatureTaskPhaseSettlementAcknowledgment(
  val status: String,
  val workflowId: String,
  val phaseId: String,
  val attempt: Int,
  val kind: FeatureTaskPhaseSettlementKind,
  val envelope: FeatureTaskRuntimeWorkflowArtifactMap,
)

data class FeatureTaskPhaseSettlementEnvelope(
  val envelope: FeatureTaskRuntimeWorkflowArtifactMap,
)
