package skillbill.application

import skillbill.application.model.FeatureTaskRuntimePhaseLaunchBriefing
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimePhaseHandoff

/**
 * SKILL-65 Subtask 3 (AC2, AC8): assembles the per-phase launch briefing from
 * the pure domain [FeatureTaskRuntimePhaseHandoff] (built by
 * `FeatureTaskRuntimeHandoffContract.assembleHandoff`).
 *
 * It serializes all three handoff layers UNCONDITIONALLY:
 *  - layer 1: run-invariants (spec reference, acceptance criteria, mandates and
 *    overrides) — injected into EVERY phase regardless of what the phase needs;
 *  - layer 2: the latest-iteration upstream outputs already resolved by the
 *    handoff contract, keyed by producing phase id;
 *  - layer 3: the statically-declared derived-context keys (e.g. `diff` for
 *    `review`).
 *
 * The launched agent never selects its own inputs: the assembler reads only the
 * already-resolved handoff, which itself is driven entirely by the static phase
 * declaration plus the run-invariants. The assembler is pure and deterministic.
 */
object FeatureTaskRuntimePhaseBriefingAssembler {
  fun assemble(handoff: FeatureTaskRuntimePhaseHandoff): FeatureTaskRuntimePhaseLaunchBriefing {
    val upstreamOutputs = handoff.upstreamOutputs.outputsByPhaseId
      .entries
      .associate { (producingPhaseId, output) -> producingPhaseId to output.payload }
    val briefingText = serialize(handoff, upstreamOutputs)
    return FeatureTaskRuntimePhaseLaunchBriefing(
      phaseId = handoff.phaseId,
      specReference = handoff.runInvariants.specReference,
      acceptanceCriteria = handoff.runInvariants.acceptanceCriteria,
      mandatesAndOverrides = handoff.runInvariants.mandatesAndOverrides,
      upstreamOutputsByPhaseId = upstreamOutputs,
      derivedContextKeys = handoff.derivedContextKeys,
      briefingText = briefingText,
    )
  }

  private fun serialize(handoff: FeatureTaskRuntimePhaseHandoff, upstreamOutputs: Map<String, String>): String =
    buildString {
      val invariants = handoff.runInvariants
      appendLine("# Feature-task-runtime phase briefing")
      appendLine("phase: ${handoff.phaseId}")
      appendLine()
      // Layer 1 — always present.
      appendLine("## Run invariants (layer 1, unconditional)")
      appendLine("spec_reference: ${invariants.specReference}")
      appendLine("acceptance_criteria:")
      invariants.acceptanceCriteria.forEachIndexed { index, criterion ->
        appendLine("  ${index + 1}. $criterion")
      }
      appendLine("mandates_and_overrides:")
      if (invariants.mandatesAndOverrides.isEmpty()) {
        appendLine("  (none)")
      } else {
        invariants.mandatesAndOverrides.forEach { mandate -> appendLine("  - $mandate") }
      }
      appendLine()
      // Layer 2 — resolved latest-iteration upstream outputs.
      appendLine("## Upstream outputs (layer 2, latest iteration)")
      if (upstreamOutputs.isEmpty()) {
        appendLine("(none)")
      } else {
        upstreamOutputs.forEach { (producingPhaseId, payload) ->
          appendLine("### from: $producingPhaseId")
          appendLine(payload)
        }
      }
      appendLine()
      // Layer 3 — statically-declared derived context.
      appendLine("## Derived context (layer 3, declared)")
      if (handoff.derivedContextKeys.isEmpty()) {
        append("(none)")
      } else {
        append(handoff.derivedContextKeys.joinToString(separator = "\n") { key -> "- $key" })
      }
    }
}
