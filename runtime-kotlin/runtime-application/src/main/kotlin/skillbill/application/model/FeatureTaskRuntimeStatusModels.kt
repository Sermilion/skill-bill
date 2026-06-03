package skillbill.application.model

/**
 * SKILL-65 Subtask 4 (AC1): typed request for the read-only feature-task-runtime
 * status projection. Carries only inert values: the runtime workflow id whose
 * per-phase records to project, and an optional db path override. No file IO and
 * no orchestration cross this boundary — the status service only reads the
 * recorder seam and projects.
 */
data class FeatureTaskRuntimeStatusRequest(
  val workflowId: String,
  val dbPathOverride: String? = null,
) {
  init {
    require(workflowId.isNotBlank()) { "FeatureTaskRuntimeStatusRequest.workflowId is required." }
  }
}

/**
 * SKILL-65 Subtask 4 (AC1): one ordered phase's read-only status, projected from
 * the durable per-phase record. [finished] mirrors the record's terminal state
 * (a finished phase carries a runtime-minted finish timestamp); [resolvedAgentId]
 * is null when no record exists yet for the phase (the phase has not started).
 */
data class FeatureTaskRuntimePhaseStatus(
  val phaseId: String,
  val status: String,
  val attemptCount: Int,
  val resolvedAgentId: String?,
  val finished: Boolean,
)

/**
 * SKILL-65 Subtask 4 (AC1): the typed, read-only status projection for one
 * feature-task-runtime workflow. The ordered [phases] follow the runtime
 * definition's `stepIds` order; the count fields and [currentPhaseId] are derived
 * deterministically from those phases. This is a pure projection — it never
 * mutates durable state and never re-plans.
 */
data class FeatureTaskRuntimeStatusProjection(
  val workflowId: String,
  val phases: List<FeatureTaskRuntimePhaseStatus>,
  val completeCount: Int,
  val pendingCount: Int,
  val blockedCount: Int,
  /** First not-yet-complete phase in definition order, or null when all complete. */
  val currentPhaseId: String?,
)
