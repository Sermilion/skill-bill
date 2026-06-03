package skillbill.application

import skillbill.application.model.FeatureTaskRuntimeFixLoopDecision
import skillbill.workflow.taskruntime.FeatureTaskRuntimePhaseWorkflowDefinition

/**
 * Pure bounded fix-loop policy. The `review` and `audit` phases re-run on a failed schema gate;
 * each re-run is a higher iteration so the latest output always wins. The loop is bounded by
 * [MAX_FIX_LOOP_ITERATIONS]. The first run is iteration 1; the fix-loop index is `iteration - 1`.
 */
object FeatureTaskRuntimeFixLoopPolicy {
  /** A phase runs at most this many times total (1 initial attempt + 2 re-runs) before blocking. */
  const val MAX_FIX_LOOP_ITERATIONS: Int = 3

  private val FIX_LOOP_PHASES: Set<String> = setOf(
    FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_REVIEW,
    FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_AUDIT,
  )

  fun participatesInFixLoop(phaseId: String): Boolean = phaseId in FIX_LOOP_PHASES

  /** Decides what to do after a phase attempt at [currentIteration] (1-based) failed its schema gate. */
  fun decideAfterFailure(phaseId: String, currentIteration: Int): FeatureTaskRuntimeFixLoopDecision {
    require(currentIteration >= 1) { "currentIteration must be >= 1, was $currentIteration." }
    val canRetry = participatesInFixLoop(phaseId) && currentIteration < MAX_FIX_LOOP_ITERATIONS
    return if (canRetry) {
      FeatureTaskRuntimeFixLoopDecision.Retry(
        nextIteration = currentIteration + 1,
        fixLoopIteration = currentIteration,
      )
    } else {
      FeatureTaskRuntimeFixLoopDecision.Block(
        blockedReason = blockedReason(phaseId, currentIteration),
      )
    }
  }

  private fun blockedReason(phaseId: String, currentIteration: Int): String = if (participatesInFixLoop(phaseId)) {
    "Phase '$phaseId' exhausted the bounded fix loop after $currentIteration attempts " +
      "(cap=$MAX_FIX_LOOP_ITERATIONS); the run blocks rather than advancing on invalid output."
  } else {
    "Phase '$phaseId' produced schema-invalid output and does not participate in a fix loop; " +
      "the run blocks rather than advancing on invalid output."
  }
}
