package skillbill.engine.featuretask.slot.strategy

import skillbill.engine.featuretask.phase.core.FeatureTaskRuntimeCurrentPhaseExecutionContext
import skillbill.engine.featuretask.phase.core.attemptPhaseExecution
import skillbill.engine.featuretask.runloop.core.FeatureTaskRuntimeRunLoopContext
import skillbill.engine.featuretask.runloop.core.PhaseOutcome
import skillbill.engine.featuretask.runloop.core.PhaseRun
import skillbill.engine.featuretask.runloop.settlement.FeatureTaskRuntimeRunLoopValidationGate
import skillbill.engine.featuretask.slot.PhaseRunState
import skillbill.engine.featuretask.slot.PhaseRunner
import skillbill.engine.featuretask.slot.PhaseStrategyStatusProjection
import skillbill.engine.work.model.IdeStatusCurrentPhaseExecution
import skillbill.engine.work.model.IdeStatusCurrentPhaseExecutionKind
import skillbill.workflow.taskruntime.model.core.PhaseSlot
import skillbill.workflow.taskruntime.model.core.PhaseStepPolicy
import skillbill.workflow.taskruntime.phase.task.FeatureTaskRuntimePhaseWorkflowDefinition

class RoutedQualityGateStrategy(override val runner: PhaseRunner) : PhaseStrategyStatusProjection() {
  private val policies =
    mapOf(
      FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_BUILD to
        PhaseStepPolicy(
          mutating = false,
          relaunchOnInvalidOutput = true,
          singleAgentSession = false,
          readOnlyIdle = false,
          fileMutating = true,
          generationScoped = false,
        ),
      FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_VALIDATE to
        PhaseStepPolicy(
          mutating = false,
          relaunchOnInvalidOutput = true,
          singleAgentSession = false,
          readOnlyIdle = false,
          fileMutating = true,
          generationScoped = false,
        ),
    )

  override val slot: PhaseSlot = PhaseSlot.QUALITY_GATE
  override val strategyId: String = ID
  override val steps: List<String> = policies.keys.toList()
  override val entryStep: String = FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_BUILD

  override fun policyFor(stepId: String): PhaseStepPolicy = policies.policyOf(stepId)

  override fun directiveFor(stepId: String): String = policies.directiveOf(stepId)

  override fun runStep(
    run: PhaseRun,
    context: FeatureTaskRuntimeRunLoopContext,
    state: PhaseRunState,
  ): PhaseOutcome {
    val call = stepCall(run, state)
    return with(FeatureTaskRuntimeRunLoopValidationGate) {
      if (run.phaseId == FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_BUILD) {
        context.runDeclaredBuildGateCycle(run, call)
      } else {
        context.runDeclaredValidationGateCycle(run, call)
      }
    }
  }

  override fun currentExecution(
    stepId: String,
    context: FeatureTaskRuntimeCurrentPhaseExecutionContext,
  ): IdeStatusCurrentPhaseExecution? =
    context.gateRunCount?.takeIf { it >= 1 }?.let { count ->
      IdeStatusCurrentPhaseExecution(
        phaseId = stepId,
        kind = IdeStatusCurrentPhaseExecutionKind.GATE_RUN,
        count = count,
      )
    } ?: attemptPhaseExecution(stepId, context)

  companion object {
    const val ID = "routed-quality-gate"
  }
}
