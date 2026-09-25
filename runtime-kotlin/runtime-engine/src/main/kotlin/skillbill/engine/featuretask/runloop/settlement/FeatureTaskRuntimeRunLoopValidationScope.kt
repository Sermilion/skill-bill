package skillbill.engine.featuretask.runloop.settlement

import skillbill.engine.featuretask.lifecycle.continuation.FeatureTaskRuntimeGoalContinuationRecorder
import skillbill.engine.featuretask.phase.core.FeatureTaskRuntimePhaseGates
import skillbill.engine.featuretask.phase.record.FeatureTaskRuntimePhaseRecorder
import skillbill.engine.featuretask.runloop.core.FeatureTaskRuntimeRunLoopSession
import skillbill.engine.featuretask.runloop.core.PhaseRun
import skillbill.engine.featuretask.runloop.core.RepositoryCheckpointResolutionArgs
import skillbill.engine.featuretask.runloop.output.FeatureTaskRuntimeRunLoopOutputVerification
import skillbill.engine.featuretask.validation.model.ValidationGateResolution
import skillbill.workflow.taskruntime.phase.task.FeatureTaskRuntimePhaseWorkflowDefinition

object FeatureTaskRuntimeRunLoopValidationScope {
  internal fun validationChangedPaths(
    phaseGates: FeatureTaskRuntimePhaseGates,
    recorder: FeatureTaskRuntimePhaseRecorder,
    goalContinuationRecorder: FeatureTaskRuntimeGoalContinuationRecorder,
    session: FeatureTaskRuntimeRunLoopSession,
    run: PhaseRun,
  ): List<String>? =
    with(FeatureTaskRuntimeRunLoopOutputVerification) {
      resolveRepositoryCheckpoint(
        RepositoryCheckpointResolutionArgs(
          recorder = recorder,
          goalContinuationRecorder = goalContinuationRecorder,
          phaseGates = phaseGates,
          session = session,
          run = run,
        ),
      )
        ?.workingTreeOwnedPaths
        ?.distinct()
        ?.sorted()
    }

  internal fun packBuildCommand(
    phaseGates: FeatureTaskRuntimePhaseGates,
    recorder: FeatureTaskRuntimePhaseRecorder,
    goalContinuationRecorder: FeatureTaskRuntimeGoalContinuationRecorder,
    session: FeatureTaskRuntimeRunLoopSession,
    run: PhaseRun,
  ): String? {
    if (run.phaseId != FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_BUILD) {
      return null
    }
    val validationChangedPaths =
      validationChangedPaths(
        phaseGates,
        recorder,
        goalContinuationRecorder,
        session,
        run,
      )
    return when (
      val resolution = phaseGates.validationGateResolver.resolve(validationChangedPaths.orEmpty())
    ) {
      is ValidationGateResolution.Declared -> resolution.declaration.buildCommand?.joinToString(" ")
      is ValidationGateResolution.Absent -> null
      is ValidationGateResolution.Incompatible -> null
    }
  }
}
