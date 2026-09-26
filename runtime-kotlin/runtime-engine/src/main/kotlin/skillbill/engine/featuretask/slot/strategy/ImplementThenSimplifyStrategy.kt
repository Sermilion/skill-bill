package skillbill.engine.featuretask.slot.strategy

import skillbill.engine.featuretask.runloop.core.FeatureTaskRuntimeRunLoopContext
import skillbill.engine.featuretask.runloop.core.PhaseOutcome
import skillbill.engine.featuretask.runloop.core.PhaseRun
import skillbill.engine.featuretask.slot.PhaseRunState
import skillbill.engine.featuretask.slot.PhaseRunner
import skillbill.engine.featuretask.slot.PhaseStrategy
import skillbill.workflow.taskruntime.model.core.PhaseSlot
import skillbill.workflow.taskruntime.model.core.PhaseStepPolicy
import skillbill.workflow.taskruntime.phase.task.FeatureTaskRuntimePhaseWorkflowDefinition

class ImplementThenSimplifyStrategy(override val runner: PhaseRunner) : PhaseStrategy() {
  private val policies =
    mapOf(
      FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_IMPLEMENT to
        PhaseStepPolicy(
          mutating = true,
          relaunchOnInvalidOutput = true,
          singleAgentSession = false,
          readOnlyIdle = false,
          fileMutating = true,
          generationScoped = false,
        ),
      FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_SIMPLIFY to
        PhaseStepPolicy(
          mutating = true,
          relaunchOnInvalidOutput = true,
          singleAgentSession = true,
          readOnlyIdle = false,
          fileMutating = true,
          generationScoped = false,
        ),
    )

  override val slot: PhaseSlot = PhaseSlot.IMPLEMENTATION
  override val strategyId: String = ID
  override val steps: List<String> = policies.keys.toList()
  override val entryStep: String = FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_IMPLEMENT

  override fun policyFor(stepId: String): PhaseStepPolicy = policies.policyOf(stepId)

  override fun directiveFor(stepId: String): String = policies.directiveOf(stepId)

  override fun runStep(
    run: PhaseRun,
    context: FeatureTaskRuntimeRunLoopContext,
    state: PhaseRunState,
  ): PhaseOutcome = runAgentStep(run, context, state)

  companion object {
    const val ID = "implement-then-simplify"
  }
}
