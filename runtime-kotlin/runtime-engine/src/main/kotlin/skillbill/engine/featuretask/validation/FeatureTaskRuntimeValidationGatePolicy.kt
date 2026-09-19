package skillbill.engine.featuretask.validation
import skillbill.config.model.applyValidationGateGradleWrapper
import skillbill.engine.featuretask.phase.record.FeatureTaskRuntimePhaseRecorder
import skillbill.engine.featuretask.validation.model.ValidationGateCyclePhase
import skillbill.scaffold.model.ValidationGateDeclaration
import skillbill.workflow.taskruntime.phase.task.FeatureTaskRuntimePhaseWorkflowDefinition
internal fun validationGateArgv(
  declaration: ValidationGateDeclaration,
  cyclePhase: ValidationGateCyclePhase,
): List<String> = when (cyclePhase) {
  ValidationGateCyclePhase.INITIAL_DISCOVERY -> declaration.collectAllFullGateCommand
  ValidationGateCyclePhase.POST_REPAIR_VERIFY -> declaration.cacheBypassingCollectAllFullGateCommand
}

internal fun validationGateCommand(
  declaration: ValidationGateDeclaration,
  cyclePhase: ValidationGateCyclePhase,
  gradleWrapper: String?,
): String = applyValidationGateGradleWrapper(
  validationGateArgv(declaration, cyclePhase),
  gradleWrapper,
).joinToString(" ")

internal fun durableValidationChangedPaths(
  recorder: FeatureTaskRuntimePhaseRecorder,
  workflowId: String,
): List<String>? {
  val checkpointPaths = recorder.loadPhaseBriefings(workflowId)
    ?.get(FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_VALIDATE)
    ?.handoffEnvelope
    ?.repositoryCheckpoint
    ?.workingTreeOwnedPaths
  if (checkpointPaths != null) {
    return checkpointPaths.filter(String::isNotBlank).distinct().sorted()
  }
  return recorder.loadResolvedBranch(workflowId)
    ?.workflowOwnedPaths
    ?.filter(String::isNotBlank)
    ?.distinct()
    ?.sorted()
}
