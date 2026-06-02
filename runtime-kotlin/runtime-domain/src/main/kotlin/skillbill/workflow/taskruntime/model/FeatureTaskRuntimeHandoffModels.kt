package skillbill.workflow.taskruntime.model

/**
 * SKILL-65 Subtask 1: domain models for the feature-task-runtime inter-phase
 * handoff contract.
 *
 * The handoff is an accumulating, schema-validated artifact store over the
 * phase DAG — not a baton passed from phase N-1 to phase N. A phase pulls its
 * statically-declared upstream dependency set (often more than the immediately
 * previous phase), always receives the run-invariants, and optionally receives
 * statically-declared derived context. Nothing here lets the executing agent
 * choose its own inputs at runtime; dependency and context declarations are
 * design-time properties of the phase definition.
 */

/**
 * Layer 1 of the handoff: run-invariants injected by the runtime into EVERY
 * phase regardless of what the phase "needs".
 *
 * AC4/AC5: every field is a required non-null primitive. There is no nullable
 * or default-valued member and no factory that can omit one, so the absence of
 * any run-invariant is a compile-time/construction-time loud failure rather
 * than a silently-dropped optional input.
 */
data class FeatureTaskRuntimeRunInvariants(
  /** Reference (path) to the governed spec the run implements. */
  val specReference: String,
  /** Ordered acceptance criteria the run must satisfy. Must be non-empty. */
  val acceptanceCriteria: List<String>,
  /** Mandates and overrides that must reach every phase verbatim. */
  val mandatesAndOverrides: List<String>,
) {
  init {
    require(specReference.isNotBlank()) {
      "FeatureTaskRuntimeRunInvariants.specReference must be a non-blank spec reference; " +
        "run-invariants cannot be partially specified."
    }
    require(acceptanceCriteria.isNotEmpty()) {
      "FeatureTaskRuntimeRunInvariants.acceptanceCriteria must list at least one criterion; " +
        "a run with no acceptance criteria has no contract to satisfy."
    }
    require(acceptanceCriteria.none(String::isBlank)) {
      "FeatureTaskRuntimeRunInvariants.acceptanceCriteria must not contain blank entries."
    }
  }
}

/**
 * One recorded output of a phase. A phase may produce multiple outputs over a
 * run when fix loops re-run it; each carries a monotonically increasing
 * [iteration] so the latest can be selected deterministically.
 */
data class FeatureTaskRuntimePhaseOutput(
  /** Id of the phase that produced this output. */
  val phaseId: String,
  /** 1-based attempt/iteration index; higher means more recent. */
  val iteration: Int,
  /** The validated phase output payload (JSON/YAML text), forwarded downstream. */
  val payload: String,
) {
  init {
    require(phaseId.isNotBlank()) { "FeatureTaskRuntimePhaseOutput.phaseId must be non-blank." }
    require(iteration >= 1) { "FeatureTaskRuntimePhaseOutput.iteration must be >= 1, was $iteration." }
  }
}

/**
 * Layer 2 of the handoff, resolved for one consuming phase: the latest
 * iteration of each statically-declared upstream dependency, keyed by producing
 * phase id.
 */
data class FeatureTaskRuntimeResolvedUpstreamOutputs(
  val outputsByPhaseId: Map<String, FeatureTaskRuntimePhaseOutput>,
)

/**
 * The fully-assembled three-layer briefing handed to a single phase: the
 * always-present run-invariants (layer 1), the resolved latest upstream outputs
 * (layer 2), and the statically-declared derived-context keys (layer 3).
 */
data class FeatureTaskRuntimePhaseHandoff(
  val phaseId: String,
  val runInvariants: FeatureTaskRuntimeRunInvariants,
  val upstreamOutputs: FeatureTaskRuntimeResolvedUpstreamOutputs,
  val derivedContextKeys: List<String>,
)

/**
 * Static, design-time declaration for a single phase. Both the consumed
 * upstream outputs and the derived context are fixed in the definition; there
 * is intentionally no API that lets a running agent add to or choose these.
 */
data class FeatureTaskRuntimePhaseDeclaration(
  val phaseId: String,
  /** Producing-phase ids whose latest output this phase consumes. */
  val consumedUpstreamPhaseIds: List<String>,
  /** Statically-declared derived-context keys (e.g. "diff" for `review`). */
  val derivedContextKeys: List<String>,
) {
  init {
    require(phaseId.isNotBlank()) { "FeatureTaskRuntimePhaseDeclaration.phaseId must be non-blank." }
  }
}
