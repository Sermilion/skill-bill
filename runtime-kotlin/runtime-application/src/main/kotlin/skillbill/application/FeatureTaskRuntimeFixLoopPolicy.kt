package skillbill.application

import skillbill.application.model.FeatureTaskRuntimeFixLoopDecision
import skillbill.workflow.taskruntime.FeatureTaskRuntimePhaseWorkflowDefinition

/**
 * SKILL-65 Subtask 3 (AC5): pure bounded fix-loop policy for the
 * feature-task-runtime pipeline.
 *
 * Two fix loops are supported with latest-iteration artifact semantics:
 *  - `review -> fix -> review`: a failing review re-runs the `review` phase;
 *  - `audit -> fix`: a failing audit re-runs the `audit` phase.
 *
 * Each re-run appends a NEW `FeatureTaskRuntimePhaseOutput` at a higher
 * `iteration`, so `FeatureTaskRuntimeHandoffContract.selectLatestOutputsByPhase`
 * always resolves the newest result. The loop is bounded by
 * [MAX_FIX_LOOP_ITERATIONS], documented to be consistent with the existing
 * max-3 convention used elsewhere in the runtime. The first run of a phase is
 * iteration 1; a fix-loop iteration index is `iteration - 1` (>= 1) and is what
 * the ledger records under the `FIX_LOOP_ITERATION` action.
 *
 * The policy is pure and deterministic so it is unit-testable without any
 * runtime/process dependency.
 */
object FeatureTaskRuntimeFixLoopPolicy {
  /**
   * Documented iteration cap consistent with the existing max-3 convention: a
   * phase runs at most 3 times total (1 initial attempt + up to 2 fix-loop
   * re-runs) before the run blocks loudly.
   */
  const val MAX_FIX_LOOP_ITERATIONS: Int = 3

  /** Phases that participate in a bounded fix loop on schema-gate failure. */
  private val FIX_LOOP_PHASES: Set<String> = setOf(
    FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_REVIEW,
    FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_AUDIT,
  )

  /** True when [phaseId] participates in a bounded fix loop. */
  fun participatesInFixLoop(phaseId: String): Boolean = phaseId in FIX_LOOP_PHASES

  /**
   * Decides what to do after a phase attempt at [currentIteration] (1-based)
   * failed its schema gate.
   */
  fun decideAfterFailure(phaseId: String, currentIteration: Int): FeatureTaskRuntimeFixLoopDecision {
    require(currentIteration >= 1) { "currentIteration must be >= 1, was $currentIteration." }
    val canRetry = participatesInFixLoop(phaseId) && currentIteration < MAX_FIX_LOOP_ITERATIONS
    return if (canRetry) {
      FeatureTaskRuntimeFixLoopDecision.Retry(
        nextIteration = currentIteration + 1,
        // The fix-loop iteration index recorded in the ledger is 1-based over
        // the re-runs (the first re-run is fix-loop iteration 1).
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
