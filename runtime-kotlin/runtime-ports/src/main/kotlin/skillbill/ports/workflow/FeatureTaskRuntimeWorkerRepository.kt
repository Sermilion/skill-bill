package skillbill.ports.workflow

import skillbill.ports.featuretask.model.FeatureTaskRuntimeCrashReconciliationCandidate
import skillbill.ports.featuretask.model.FeatureTaskRuntimeWorkerOwnership
import skillbill.ports.featuretask.model.parseFeatureTaskRuntimeWorkerLeaseInstant

interface FeatureTaskRuntimeWorkerRepository {
  fun getFeatureTaskRuntimeWorkerOwnership(workflowId: String): FeatureTaskRuntimeWorkerOwnership?

  fun acquireFeatureTaskRuntimeWorker(
    ownership: FeatureTaskRuntimeWorkerOwnership,
    expectedUpdatedAt: String?,
  ): Boolean

  fun reserveFeatureTaskRuntimeWorkerTakeover(
    workflowId: String,
    expectedOwnerToken: String,
    expectedGeneration: Long,
  ): Boolean

  fun transferFeatureTaskRuntimeWorker(
    ownership: FeatureTaskRuntimeWorkerOwnership,
    expectedOwnerToken: String,
    expectedGeneration: Long,
  ): Boolean

  fun heartbeatFeatureTaskRuntimeWorker(ownership: FeatureTaskRuntimeWorkerOwnership): Boolean

  fun releaseFeatureTaskRuntimeWorker(
    workflowId: String,
    ownerToken: String,
    generation: Long,
  ): Boolean

  fun releaseFeatureTaskRuntimeWorkerIfExpired(
    workflowId: String,
    ownerToken: String,
    generation: Long,
    nowInstant: String,
  ): Boolean {
    val current = getFeatureTaskRuntimeWorkerOwnership(workflowId) ?: return false
    if (current.ownerToken != ownerToken || current.generation != generation) return false
    val now = parseFeatureTaskRuntimeWorkerLeaseInstant(workflowId, "now_instant", nowInstant)
    if (current.expiresAtInstant.isAfter(now)) return false
    return releaseFeatureTaskRuntimeWorker(workflowId, ownerToken, generation)
  }

  /**
   * Non-terminal runtime rows whose worker lease has already expired as of [nowInstant] (an
   * RFC 3339 instant): the crash-reconciliation candidate set. Liveness is NOT decided here — the
   * caller confirms the process is dead before writing. Rows without a lease, with a live lease, or
   * in a terminal status are never returned.
   */
  fun findFeatureTaskRuntimeCrashReconciliationCandidates(
    nowInstant: String,
  ): List<FeatureTaskRuntimeCrashReconciliationCandidate>

  /**
   * Atomic fenced reconcile write: transition the still-`running` row to the resumable `pending`
   * state at its existing current_step_id, record [interruptionReason], and release the lease under
   * the existing owner_token/generation fencing, all only while the lease is still expired as of
   * [nowInstant]. Returns false on a lost fencing race (another pass reconciled first) so the caller
   * can skip rather than fail; the write never partially applies.
   */
  fun reconcileFeatureTaskRuntimeCrashedWorker(
    workflowId: String,
    ownerToken: String,
    generation: Long,
    interruptionReason: String,
    nowInstant: String,
  ): Boolean
}
