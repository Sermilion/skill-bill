package skillbill.workflow.taskruntime.model

import skillbill.workflow.goal.model.ValidationDepth

data class FeatureTaskRuntimeHandoffAssemblyRequest(
  val declaration: FeatureTaskRuntimePhaseDeclaration,
  val runInvariants: FeatureTaskRuntimeRunInvariants,
  val recordedOutputs: List<FeatureTaskRuntimePhaseOutput>,
  val drivingVerdict: FeatureTaskRuntimeVerdict? = null,
  val repairLedger: FeatureTaskRuntimeRepairLedger? = null,
  val repositoryCheckpoint: FeatureTaskRuntimeRepositoryCheckpoint? = null,
  val expectedRepositoryCheckpoint: FeatureTaskRuntimeRepositoryCheckpoint? = null,
  val branchIdentity: String? = null,
  val baseBranch: String = "main",
  val validationDepth: ValidationDepth = ValidationDepth.DEFAULT,
  val qualityGateSelection: FeatureTaskRuntimeQualityGateSelection = FeatureTaskRuntimeQualityGateSelection.VALIDATE,
)
