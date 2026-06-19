package skillbill.workflow.taskruntime.model

import skillbill.error.InvalidWorkflowStateSchemaError

/**
 * Effect-free typed verdict a verifying phase's structured output yields, which the bounded-cyclic
 * transition function reads to decide whether to re-enter an upstream phase or progress forward. The
 * abstraction is intentionally generic: this subtask defines only the type and a default
 * [ADVANCE]-only verdict for phases with no verifying output; concrete review/audit verdict schemas
 * are added in later subtasks.
 *
 * [wireValue] is stable for durable persistence and [fromWire] is a strict decode mirroring
 * [FeatureTaskRuntimePhaseLedgerAction]'s wire pattern, so a persisted verdict round-trips and an
 * unknown wire value loud-fails rather than being coerced.
 */
data class FeatureTaskRuntimeVerdict(
  val wireValue: String,
) {
  init {
    require(wireValue.isNotBlank()) { "FeatureTaskRuntimeVerdict.wireValue must be non-blank." }
  }

  companion object {
    /** The default verdict for a phase with no verifying output: always progresses forward. */
    val ADVANCE: FeatureTaskRuntimeVerdict = FeatureTaskRuntimeVerdict("advance")

    /** A review verdict with no unresolved Blocker/Major findings: the run advances past review. */
    val APPROVED: FeatureTaskRuntimeVerdict = FeatureTaskRuntimeVerdict("approved")

    /**
     * A review verdict carrying unresolved Blocker/Major findings: the run takes the `review_fix`
     * backward edge to the `implement_fix` phase to reconcile them on the current tree.
     */
    val CHANGES_REQUESTED: FeatureTaskRuntimeVerdict = FeatureTaskRuntimeVerdict("changes_requested")

    fun fromWire(value: String): FeatureTaskRuntimeVerdict =
      value.takeIf(String::isNotBlank)?.let(::FeatureTaskRuntimeVerdict)
        ?: throw InvalidWorkflowStateSchemaError(
          "Feature-task-runtime verdict wire value must be a non-blank string, was '$value'.",
        )
  }
}
