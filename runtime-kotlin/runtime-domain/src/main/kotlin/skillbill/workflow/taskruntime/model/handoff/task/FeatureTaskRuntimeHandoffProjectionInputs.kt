package skillbill.workflow.taskruntime.model.handoff.task
import skillbill.review.model.ReviewFindingVerdict
import skillbill.workflow.goal.model.ValidationDepth
import skillbill.workflow.taskruntime.model.core.FeatureTaskRuntimeQualityGateSelection
import skillbill.workflow.taskruntime.model.core.FeatureTaskRuntimeRepositoryCheckpoint
import skillbill.workflow.taskruntime.model.core.FeatureTaskRuntimeWireArtifactValidator
import skillbill.workflow.taskruntime.model.core.Map
import skillbill.workflow.taskruntime.model.core.task
import skillbill.workflow.taskruntime.model.handoff.Map
import skillbill.workflow.taskruntime.model.handoff.PhaseHandoffProjectionDeclaration
import skillbill.workflow.taskruntime.model.persistence.task.runtime.goal.Map
import skillbill.workflow.taskruntime.model.persistence.task.runtime.run.Map
import skillbill.workflow.taskruntime.model.phase.FeatureTaskRuntimeSharedReviewEvidenceReference
import skillbill.workflow.taskruntime.model.phase.Map
import skillbill.workflow.taskruntime.model.repair.task.DEFAULT
import skillbill.workflow.taskruntime.model.repair.task.FeatureTaskRuntimeRepairLedger
import skillbill.workflow.taskruntime.model.validation.Map
data class FeatureTaskRuntimeHandoffProjectionInputs(
  val consumerPhaseId: String,
  val declarations: List<PhaseHandoffProjectionDeclaration>,
  val resolvedUpstream: FeatureTaskRuntimeResolvedUpstreamOutputs,
  val runInvariants: FeatureTaskRuntimeRunInvariants,

  val resolvedCheckpoint: FeatureTaskRuntimeRepositoryCheckpoint? = null,

  val expectedCheckpoint: FeatureTaskRuntimeRepositoryCheckpoint? = null,

  val sharedReviewEvidence: FeatureTaskRuntimeSharedReviewEvidenceReference? = null,
  val repairLedger: FeatureTaskRuntimeRepairLedger? = null,
  val recordedFindingVerdicts: List<ReviewFindingVerdict> = emptyList(),

  val branchIdentity: String? = null,
  val baseBranch: String = "main",
  val addonContentBySlug: Map<String, String> = emptyMap(),
  val workflowId: String? = null,

  val validationDepth: ValidationDepth = ValidationDepth.DEFAULT,
  val qualityGateSelection: FeatureTaskRuntimeQualityGateSelection = FeatureTaskRuntimeQualityGateSelection.VALIDATE,
  val planningProjectionValidator: FeatureTaskRuntimeWireArtifactValidator,
)

const val MAX_REPOSITORY_FINGERPRINT_LENGTH: Int = 256

const val MAX_BOUNDED_POINTER_LENGTH: Int = 256
