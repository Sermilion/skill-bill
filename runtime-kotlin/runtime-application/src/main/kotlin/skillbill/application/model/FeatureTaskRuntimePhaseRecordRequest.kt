package skillbill.application.model

import skillbill.workflow.taskruntime.model.FeatureTaskRuntimePhaseLedgerAction

/**
 * SKILL-65 Subtask 2 (AC3, AC5): application-layer request to persist one
 * feature-task-runtime per-phase record. Carries only runtime-owned facts plus
 * the validated phase output artifact; the runtime (the recorder) mints the
 * start/finish timestamps and computes the duration, so NO timestamp or
 * duration ever crosses this boundary from an agent.
 */
data class FeatureTaskRuntimePhaseStateRequest(
  val workflowId: String,
  val phaseId: String,
  val status: String,
  val attemptCount: Int,
  val resolvedAgentId: String,
  val finished: Boolean,
  val outputArtifact: String? = null,
)

/**
 * SKILL-65 Subtask 2 (AC4, AC5): application-layer request to append one phase
 * attempt/event ledger entry. The recorder mints the timestamp and assigns the
 * monotonic sequence number from the persisted watermark, so the caller never
 * supplies time or ordering.
 */
data class FeatureTaskRuntimePhaseLedgerRequest(
  val workflowId: String,
  val action: FeatureTaskRuntimePhaseLedgerAction,
  val phaseId: String,
  val attemptCount: Int,
  val resolvedAgentId: String? = null,
  val fixLoopIteration: Int? = null,
  val blockedReason: String? = null,
)
