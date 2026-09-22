package skillbill.engine.featuretask.model.phase
import skillbill.workflow.taskruntime.model.core.FeatureTaskRuntimeWireArtifactValidator
import skillbill.workflow.taskruntime.model.handoff.PhaseHandoffProjectionDeclaration
import skillbill.workflow.taskruntime.model.handoff.task.FeatureTaskRuntimePhaseHandoff
import skillbill.workflow.taskruntime.model.phase.FeatureTaskRuntimeSharedReviewEvidenceReference

data class FeatureTaskRuntimeBriefingProjectionInputs(
  val handoff: FeatureTaskRuntimePhaseHandoff,
  val declarations: List<PhaseHandoffProjectionDeclaration>,
  val workflowId: String?,
  val planningProjectionValidator: FeatureTaskRuntimeWireArtifactValidator,
  val sharedReviewEvidence: FeatureTaskRuntimeSharedReviewEvidenceReference?,
  val addonContentBySlug: Map<String, String>,
)
