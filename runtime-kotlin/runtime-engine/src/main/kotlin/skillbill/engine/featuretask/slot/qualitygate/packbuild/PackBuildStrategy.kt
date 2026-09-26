package skillbill.engine.featuretask.slot.qualitygate.packbuild

import skillbill.engine.featuretask.phase.core.FeatureTaskRuntimeCurrentPhaseExecutionContext
import skillbill.engine.featuretask.runloop.core.FeatureTaskRuntimeRunLoopContext
import skillbill.engine.featuretask.runloop.core.PhaseOutcome
import skillbill.engine.featuretask.runloop.core.PhaseRun
import skillbill.engine.featuretask.slot.PhaseRunState
import skillbill.engine.featuretask.slot.PhaseRunner
import skillbill.engine.featuretask.slot.PhaseStepHooks
import skillbill.engine.featuretask.slot.PhaseStrategyStatusProjection
import skillbill.engine.featuretask.slot.qualitygate.QUALITY_GATE_STEP_POLICY
import skillbill.engine.featuretask.slot.qualitygate.gateCurrentExecution
import skillbill.engine.featuretask.slot.strategy.directiveOf
import skillbill.engine.featuretask.slot.strategy.policyOf
import skillbill.engine.featuretask.slot.strategy.stepCall
import skillbill.engine.work.model.IdeStatusCurrentPhaseExecution
import skillbill.workflow.taskruntime.model.core.PhaseSlot
import skillbill.workflow.taskruntime.model.core.PhaseStepPolicy
import skillbill.workflow.taskruntime.phase.task.FeatureTaskRuntimePhaseWorkflowDefinition

/**
 * The quality_gate strategy that runs the pack-declared build gate: the runtime runs the pack build command,
 * triages an unparseable failure, and hands the parsed findings to bounded repair turns before it re-runs the gate.
 */
class PackBuildStrategy(override val runner: PhaseRunner) : PhaseStrategyStatusProjection() {
  private val policies: Map<String, PhaseStepPolicy> =
    mapOf(FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_BUILD to QUALITY_GATE_STEP_POLICY)

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
  ): PhaseOutcome = PackBuildGateCycle(context, stepCall(run, state)).run(run)

  override fun stepHooks(stepId: String): PhaseStepHooks =
    if (stepId in policies) PackBuildStepHooks else PhaseStepHooks.None

  override fun currentExecution(
    stepId: String,
    context: FeatureTaskRuntimeCurrentPhaseExecutionContext,
  ): IdeStatusCurrentPhaseExecution? = gateCurrentExecution(stepId, context)

  companion object {
    const val ID = "pack-build"
  }
}
