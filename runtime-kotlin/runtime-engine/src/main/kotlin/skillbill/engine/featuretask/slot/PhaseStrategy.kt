package skillbill.engine.featuretask.slot

import skillbill.engine.featuretask.phase.core.FeatureTaskRuntimeCurrentPhaseExecutionContext
import skillbill.engine.featuretask.runloop.core.FeatureTaskRuntimeRunLoopContext
import skillbill.engine.featuretask.runloop.core.PhaseOutcome
import skillbill.engine.featuretask.runloop.core.PhaseRun
import skillbill.engine.work.model.IdeStatusCurrentPhaseExecution
import skillbill.workflow.taskruntime.model.core.PhaseSlot
import skillbill.workflow.taskruntime.model.core.PhaseStepPolicy

/**
 * One in-process way to run the steps of a [PhaseSlot]. A strategy declares its steps with their policy and task
 * directive, and runs each step through its own [PhaseRunner].
 *
 * An abstract class rather than an interface: [runStep] takes run-loop types that stay internal to this module until
 * subtask 7, and an interface cannot declare an internal member.
 */
abstract class PhaseStrategy {
  /** The slot whose steps this strategy runs. */
  abstract val slot: PhaseSlot

  /** The wire id that selects this strategy for its slot. */
  abstract val strategyId: String

  /** The steps this strategy runs, all owned by [slot]. */
  abstract val steps: List<String>

  /** The step this strategy starts from. */
  abstract val entryStep: String

  /** The runner this strategy starts every agent session with. */
  abstract val runner: PhaseRunner

  /** The launch policy of [stepId]. */
  abstract fun policyFor(stepId: String): PhaseStepPolicy

  /** The task directive of [stepId]. */
  abstract fun directiveFor(stepId: String): String

  /** Runs one step of [run] with the per-call [state]. */
  internal abstract fun runStep(
    run: PhaseRun,
    context: FeatureTaskRuntimeRunLoopContext,
    state: PhaseRunState,
  ): PhaseOutcome
}

/** A [PhaseStrategy] that projects the IDE status execution counter of the steps it runs. */
abstract class PhaseStrategyStatusProjection : PhaseStrategy() {
  /** The current execution of [stepId] in [context], or null when the step has not executed yet. */
  internal abstract fun currentExecution(
    stepId: String,
    context: FeatureTaskRuntimeCurrentPhaseExecutionContext,
  ): IdeStatusCurrentPhaseExecution?
}
