package skillbill.engine.featuretask.slot.strategy

import skillbill.engine.featuretask.runloop.core.FeatureTaskRuntimeRunLoopContext
import skillbill.engine.featuretask.runloop.core.PhaseOutcome
import skillbill.engine.featuretask.runloop.core.PhaseRun
import skillbill.engine.featuretask.slot.PhaseRunState
import skillbill.engine.featuretask.slot.PhaseRunner
import skillbill.engine.featuretask.slot.PhaseStrategy
import skillbill.engine.featuretask.validation.ReadinessCommitPushSettleResult
import skillbill.workflow.taskruntime.model.core.PhaseSlot
import skillbill.workflow.taskruntime.model.core.PhaseStepPolicy
import skillbill.workflow.taskruntime.phase.task.FeatureTaskRuntimePhaseWorkflowDefinition

class PrDescriptionStrategy(override val runner: PhaseRunner) : PhaseStrategy() {
  private val policies =
    mapOf(
      FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_PR to
        PhaseStepPolicy(
          mutating = false,
          relaunchOnInvalidOutput = false,
          singleAgentSession = false,
          readOnlyIdle = false,
          fileMutating = true,
          generationScoped = false,
        ),
    )

  override val slot: PhaseSlot = PhaseSlot.PULL_REQUEST
  override val strategyId: String = ID
  override val steps: List<String> = policies.keys.toList()
  override val entryStep: String = FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_PR

  override fun policyFor(stepId: String): PhaseStepPolicy = policies.policyOf(stepId)

  override fun directiveFor(stepId: String): String = policies.directiveOf(stepId)

  override fun runStep(
    run: PhaseRun,
    context: FeatureTaskRuntimeRunLoopContext,
    state: PhaseRunState,
  ): PhaseOutcome {
    val readiness =
      context.phaseGates.readinessGateCoordinator.verifyPrEntryIdentity(
        workflowId = context.request.workflowId,
        repoRoot = context.request.repoRoot,
        baseBranch = context.recorder.loadResolvedBranch(context.request.workflowId)?.baseBranch ?: "main",
        gitOperations = context.phaseGates.gitOperations,
      )
    return if (readiness is ReadinessCommitPushSettleResult.Blocked) {
      PhaseOutcome.blocked(readiness.reason)
    } else {
      runAgentStep(run, context, state)
    }
  }

  companion object {
    const val ID = "pr-description"
  }
}
