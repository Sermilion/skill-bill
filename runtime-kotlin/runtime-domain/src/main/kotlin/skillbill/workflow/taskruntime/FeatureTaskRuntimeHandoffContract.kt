package skillbill.workflow.taskruntime

import skillbill.workflow.taskruntime.model.FeatureTaskRuntimePhaseDeclaration
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimePhaseHandoff
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimePhaseOutput
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeResolvedUpstreamOutputs
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeRunInvariants

/**
 * SKILL-65 Subtask 1: pure-domain resolution of the three-layer feature-task-
 * runtime handoff.
 *
 * Layer 1 (run-invariants) is always injected; its type
 * ([FeatureTaskRuntimeRunInvariants]) makes absence a loud construction-time
 * failure. Layer 2 (declared upstream outputs) is resolved here as the LATEST
 * iteration of each statically-declared dependency, supporting fix loops that
 * re-run an upstream phase. Layer 3 (derived context) is taken verbatim from
 * the phase's static declaration.
 *
 * Every function is pure and deterministic so it is unit-testable without any
 * runtime/process/IO dependency. There is no entry point that lets a running
 * agent select its own inputs: a phase's dependency set and derived context are
 * read only from [FeatureTaskRuntimePhaseDeclaration].
 */
object FeatureTaskRuntimeHandoffContract {
  /**
   * Selects the latest-iteration output for each producing phase from a flat
   * list of recorded outputs (which may contain repeated outputs per phase from
   * fix loops). Ties on [FeatureTaskRuntimePhaseOutput.iteration] keep the
   * last-recorded entry, so callers that append in chronological order get the
   * most recent result.
   */
  fun selectLatestOutputsByPhase(
    recordedOutputs: List<FeatureTaskRuntimePhaseOutput>,
  ): Map<String, FeatureTaskRuntimePhaseOutput> {
    val latest = LinkedHashMap<String, FeatureTaskRuntimePhaseOutput>()
    recordedOutputs.forEach { output ->
      val existing = latest[output.phaseId]
      if (existing == null || output.iteration >= existing.iteration) {
        latest[output.phaseId] = output
      }
    }
    return latest
  }

  /**
   * Resolves the statically-declared upstream dependency set for [declaration]
   * against the [recordedOutputs] store, selecting the latest iteration of each
   * declared producing phase. A declared dependency with no recorded output is
   * omitted from the result; callers (the runtime loop, a later subtask) decide
   * whether a missing required upstream blocks the run.
   */
  fun resolveUpstreamOutputs(
    declaration: FeatureTaskRuntimePhaseDeclaration,
    recordedOutputs: List<FeatureTaskRuntimePhaseOutput>,
  ): FeatureTaskRuntimeResolvedUpstreamOutputs {
    val latestByPhase = selectLatestOutputsByPhase(recordedOutputs)
    val resolved = LinkedHashMap<String, FeatureTaskRuntimePhaseOutput>()
    declaration.consumedUpstreamPhaseIds.forEach { producingPhaseId ->
      latestByPhase[producingPhaseId]?.let { resolved[producingPhaseId] = it }
    }
    return FeatureTaskRuntimeResolvedUpstreamOutputs(resolved)
  }

  /**
   * Assembles the full three-layer handoff for one phase: always-present
   * run-invariants, the resolved latest upstream outputs, and the statically-
   * declared derived-context keys.
   */
  fun assembleHandoff(
    declaration: FeatureTaskRuntimePhaseDeclaration,
    runInvariants: FeatureTaskRuntimeRunInvariants,
    recordedOutputs: List<FeatureTaskRuntimePhaseOutput>,
  ): FeatureTaskRuntimePhaseHandoff = FeatureTaskRuntimePhaseHandoff(
    phaseId = declaration.phaseId,
    runInvariants = runInvariants,
    upstreamOutputs = resolveUpstreamOutputs(declaration, recordedOutputs),
    derivedContextKeys = declaration.derivedContextKeys,
  )
}
