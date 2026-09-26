package skillbill.engine.featuretask.slot

import skillbill.engine.featuretask.runloop.core.FeatureTaskRuntimeRunLoopContext
import skillbill.workflow.model.validation.FeatureTaskRuntimeVerdict

/**
 * The run-loop decisions a strategy owns over the loops, re-entries, and settled steps of its slot. The run loop
 * asks the strategy selected for the step, or for the destination step of the loop, so it names no step or loop of
 * that slot itself. Every decision reads and writes run state through the per-call [PhaseRunState].
 */
internal interface PhaseLoopRules {
  /** Reopens settled steps whose judged repository delta changed since they settled, before the run loop starts. */
  fun reopenStaleSettledSteps(
    context: FeatureTaskRuntimeRunLoopContext,
    state: PhaseRunState,
  )

  /** Invalidates the durable step evidence the run state marked stale, before the first step dispatches. */
  fun invalidateStaleEvidence(
    context: FeatureTaskRuntimeRunLoopContext,
    state: PhaseRunState,
  )

  /** Whether a resumed in-flight re-entry of [loopId] is stale and must be discarded instead of resumed. */
  fun discardsResumedReentry(
    loopId: String,
    state: PhaseRunState,
  ): Boolean

  /** Whether a [loopId] re-entry its destination step left in flight resumes at that destination. */
  fun resumesInFlightReentry(loopId: String): Boolean

  /** The repository checkpoint the destination step of a [loopId] re-entry launches against, or null. */
  fun reentryCheckpoint(
    loopId: String,
    state: PhaseRunState,
  ): String?

  /** Why [stepId] cannot be entered, or null when it can. */
  fun entryBlockReason(
    stepId: String,
    context: FeatureTaskRuntimeRunLoopContext,
    state: PhaseRunState,
  ): String?

  /** Settles [stepId] from durable state without launching it, or null when the step launches. */
  fun settleWithoutLaunch(
    stepId: String,
    context: FeatureTaskRuntimeRunLoopContext,
    state: PhaseRunState,
  ): PhaseEntrySettlement?

  /** The verdict the run loop routes on after [stepId] completed with [verdict]. */
  fun routedVerdict(
    stepId: String,
    verdict: FeatureTaskRuntimeVerdict,
    state: PhaseRunState,
  ): FeatureTaskRuntimeVerdict
}

/** How a step settled from durable state without a launch. */
internal sealed interface PhaseEntrySettlement {
  data class Completed(val verdict: FeatureTaskRuntimeVerdict) : PhaseEntrySettlement

  data class Blocked(val reason: String) : PhaseEntrySettlement
}
