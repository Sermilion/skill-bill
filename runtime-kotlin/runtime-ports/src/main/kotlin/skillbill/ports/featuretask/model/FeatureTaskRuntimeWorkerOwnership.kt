package skillbill.ports.featuretask.model

import skillbill.contracts.workflow.FEATURE_TASK_RUNTIME_WORKER_OWNERSHIP_CONTRACT_VERSION
import skillbill.error.InvalidFeatureTaskRuntimeWorkerOwnershipSchemaError
import java.time.Instant
import java.time.format.DateTimeParseException

data class FeatureTaskRuntimeWorkerOwnership(
  val workflowId: String,
  val generation: Long,
  val ownerToken: String,
  val hostIdentity: String,
  val bootIdentity: String,
  val pid: Long,
  val processBirthToken: String,
  val leaseState: FeatureTaskRuntimeWorkerLeaseState,
  val heartbeatAt: String,
  val expiresAt: String,
  val phaseId: String,
  val phaseAttempt: Int,
  val contractVersion: String = FEATURE_TASK_RUNTIME_WORKER_OWNERSHIP_CONTRACT_VERSION,
) {
  val heartbeatAtInstant: Instant = parseLeaseInstant(workflowId, "heartbeat_at", heartbeatAt)
  val expiresAtInstant: Instant = parseLeaseInstant(workflowId, "expires_at", expiresAt)
}

fun parseFeatureTaskRuntimeWorkerLeaseInstant(workflowId: String, field: String, value: String): Instant = try {
  Instant.parse(value)
} catch (_: DateTimeParseException) {
  throw InvalidFeatureTaskRuntimeWorkerOwnershipSchemaError(
    workflowId,
    "$field must be an RFC 3339 instant",
  )
}

private fun parseLeaseInstant(workflowId: String, field: String, value: String): Instant =
  parseFeatureTaskRuntimeWorkerLeaseInstant(workflowId, field, value)

enum class FeatureTaskRuntimeWorkerLeaseState(val wireValue: String) {
  ACTIVE("active"),
  TAKEOVER_RESERVED("takeover_reserved"),
  ;

  companion object {
    fun fromWire(value: String?): FeatureTaskRuntimeWorkerLeaseState? =
      value?.trim()?.let { candidate -> entries.firstOrNull { it.wireValue == candidate } }
  }
}

data class FeatureTaskRuntimeCrashReconciliationCandidate(
  val ownership: FeatureTaskRuntimeWorkerOwnership,
  val currentStepId: String,
  val workflowStatus: String,
)

sealed interface FeatureTaskRuntimeWorkerAcquisition {
  data class Acquired(val ownership: FeatureTaskRuntimeWorkerOwnership) : FeatureTaskRuntimeWorkerAcquisition
  data class OrphanReclaimed(val ownership: FeatureTaskRuntimeWorkerOwnership) : FeatureTaskRuntimeWorkerAcquisition
  data class ExactLiveOwner(val ownership: FeatureTaskRuntimeWorkerOwnership) : FeatureTaskRuntimeWorkerAcquisition
  data object Contended : FeatureTaskRuntimeWorkerAcquisition
  data class OwnershipMismatch(val reason: String) : FeatureTaskRuntimeWorkerAcquisition
  data class UnsupportedProcessEvidence(val reason: String) : FeatureTaskRuntimeWorkerAcquisition
  data class StaleSelector(val workflowId: String) : FeatureTaskRuntimeWorkerAcquisition
}
