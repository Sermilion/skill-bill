package skillbill.workflow.taskruntime.model

import skillbill.review.model.ReviewFindingVerdict
import skillbill.workflow.goal.model.ValidationDepth
import skillbill.workflow.taskruntime.FeatureTaskRuntimePlanningProjectionValidator

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
  val planningProjectionValidator: FeatureTaskRuntimePlanningProjectionValidator,
)

const val MAX_REPOSITORY_FINGERPRINT_LENGTH: Int = 256

const val MAX_BOUNDED_POINTER_LENGTH: Int = 256
