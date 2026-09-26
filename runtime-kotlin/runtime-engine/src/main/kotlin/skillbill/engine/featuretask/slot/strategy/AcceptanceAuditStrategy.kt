package skillbill.engine.featuretask.slot.strategy

import skillbill.engine.featuretask.phase.core.FeatureTaskRuntimeCurrentPhaseExecutionContext
import skillbill.engine.featuretask.runloop.core.FeatureTaskRuntimeRunLoopContext
import skillbill.engine.featuretask.runloop.core.PhaseOutcome
import skillbill.engine.featuretask.runloop.core.PhaseRun
import skillbill.engine.featuretask.slot.PhaseRunState
import skillbill.engine.featuretask.slot.PhaseRunner
import skillbill.engine.featuretask.slot.PhaseStrategyStatusProjection
import skillbill.engine.work.model.IdeStatusCurrentPhaseExecution
import skillbill.engine.work.model.IdeStatusCurrentPhaseExecutionKind
import skillbill.workflow.taskruntime.model.core.PhaseSlot
import skillbill.workflow.taskruntime.model.core.PhaseStepPolicy
import skillbill.workflow.taskruntime.phase.task.FeatureTaskRuntimePhaseWorkflowDefinition

class AcceptanceAuditStrategy(override val runner: PhaseRunner) : PhaseStrategyStatusProjection() {
  private val policies =
    mapOf(
      FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_AUDIT to
        PhaseStepPolicy(
          mutating = false,
          relaunchOnInvalidOutput = false,
          singleAgentSession = true,
          readOnlyIdle = false,
          fileMutating = true,
          generationScoped = false,
        ),
    )

  override val slot: PhaseSlot = PhaseSlot.AUDIT
  override val strategyId: String = ID
  override val steps: List<String> = policies.keys.toList()
  override val entryStep: String = FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_AUDIT

  override fun policyFor(stepId: String): PhaseStepPolicy = policies.policyOf(stepId)

  override fun directiveFor(stepId: String): String = policies.directiveOf(stepId)

  override fun runStep(
    run: PhaseRun,
    context: FeatureTaskRuntimeRunLoopContext,
    state: PhaseRunState,
  ): PhaseOutcome = runAgentStep(run, context, state)

  override fun currentExecution(
    stepId: String,
    context: FeatureTaskRuntimeCurrentPhaseExecutionContext,
  ): IdeStatusCurrentPhaseExecution? {
    val attempts = context.phases.firstOrNull { it.phaseId == stepId }?.attemptCount ?: 0
    return if (attempts >= 1 || context.records[stepId] != null) {
      IdeStatusCurrentPhaseExecution(
        phaseId = stepId,
        kind = IdeStatusCurrentPhaseExecutionKind.PASS,
        count = 1,
      )
    } else {
      null
    }
  }

  companion object {
    const val ID = "acceptance-audit"
  }
}
