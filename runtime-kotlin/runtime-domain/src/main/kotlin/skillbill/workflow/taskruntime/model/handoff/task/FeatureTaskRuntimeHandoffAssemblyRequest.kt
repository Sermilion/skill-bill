package skillbill.workflow.taskruntime.model.handoff.task
import skillbill.workflow.goal.model.ValidationDepth
import skillbill.workflow.taskruntime.model.core.FeatureTaskRuntimeQualityGateSelection
import skillbill.workflow.taskruntime.model.core.FeatureTaskRuntimeRepositoryCheckpoint
import skillbill.workflow.taskruntime.model.repair.task.FeatureTaskRuntimeRepairLedger
import skillbill.workflow.taskruntime.model.validation.FeatureTaskRuntimeVerdict

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
