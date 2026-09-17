package skillbill.engine.featuretask

import me.tatarka.inject.annotations.Inject

@Inject
class FeatureTaskRuntimePhaseGates(
  branch: FeatureTaskRuntimePhaseGateBranchBoundaries,
  validation: FeatureTaskRuntimePhaseGateValidationBoundaries,
) {
  val branchSetupRunner: FeatureTaskRuntimeBranchSetupRunner = branch.branchSetupRunner
  val planningStopper: FeatureTaskRuntimePlanningStopper = branch.planningStopper
  val lifecycleTelemetry: FeatureTaskRuntimeLifecycleTelemetry = branch.lifecycleTelemetry
  val gitOperations = branch.gitOperations
  val specGate: FeatureTaskRuntimeSpecGate = branch.specGate
  val planningProjectionValidator = validation.planningProjectionValidator
  val buildReceiptValidator = validation.buildReceiptValidator
  val validationGateResolver = validation.validationGateResolver
  val validationGateRunner = validation.validationGateRunner
  val validationGateCoordinator = validation.validationGateCoordinator
  val buildGateCoordinator = validation.buildGateCoordinator
  val sharedEvidenceResolver = validation.sharedEvidenceResolver
  val diffResolver = validation.diffResolver
  val reviewDriver = validation.reviewDriver
  val specIntentProjectionResolver = validation.specIntentProjectionResolver
  val findingVerificationBoundaryMemory = validation.findingVerificationBoundaryMemory
}
