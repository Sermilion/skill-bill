package skillbill.goalrunner.model

import skillbill.workflow.decomposition.model.DecompositionManifest
import skillbill.workflow.model.DecompositionStatus
import skillbill.workflow.model.decompositionStatus

data class GoalRunnerExecutionLease(
  val generation: Long,
  val ownerToken: String,
  val hostIdentity: String,
  val bootIdentity: String,
  val pid: Long,
  val processBirthToken: String,
  val heartbeatAt: String,
  val expiresAt: String,
) {
  init {
    require(generation > 0) { "execution lease generation must be positive." }
    require(ownerToken.isNotBlank()) { "execution lease ownerToken must not be blank." }
    require(hostIdentity.isNotBlank()) { "execution lease hostIdentity must not be blank." }
    require(bootIdentity.isNotBlank()) { "execution lease bootIdentity must not be blank." }
    require(pid > 0) { "execution lease pid must be positive." }
    require(processBirthToken.isNotBlank()) { "execution lease processBirthToken must not be blank." }
    require(heartbeatAt.isNotBlank()) { "execution lease heartbeatAt must not be blank." }
    require(expiresAt.isNotBlank()) { "execution lease expiresAt must not be blank." }
  }
}

const val GOAL_PAUSE_REASON_OPERATOR_REQUEST: String = "operator_request"

const val GOAL_PAUSE_REASON_STOP_AFTER_SUBTASK: String = "stop_after_subtask"

const val GOAL_PAUSE_REASON_OPERATOR_STOP: String = "operator_stop"

const val GOAL_PAUSE_REASON_RUNNER_INTERRUPTED: String = "runner_interrupted"

const val GOAL_ACTIVE_HEARTBEAT_GAP_LIMIT_MS: Long = 20_000

data class GoalRunnerControlState(
  val stopAfterSubtaskId: Int? = null,
  val pauseRequested: Boolean = false,
  val pauseConsumed: Boolean = false,
  val paused: Boolean = false,
  val pauseReason: String? = null,
  val pausedAt: String? = null,
  val stopAfterConsumed: Boolean = false,
  val repositoryIdentity: String? = null,
  val executionLease: GoalRunnerExecutionLease? = null,

  val activeDurationMs: Long = 0,
  val activeDurationAsOf: String? = null,
  val currentSubtaskId: Int? = null,
  val subtaskActiveDurationMs: Long = 0,
  val subtaskActiveDurationAsOf: String? = null,
  val validationQualityRetriesBySubtask: Map<Int, Int> = emptyMap(),
  val pendingReAttemptCauseBySubtask: Map<Int, String> = emptyMap(),
  val pendingCausingLoopEntryBySubtask: Map<Int, String> = emptyMap(),
) {
  init {
    stopAfterSubtaskId?.let { require(it > 0) { "stopAfterSubtaskId must be positive when provided." } }
    require(activeDurationMs >= 0) { "activeDurationMs must not be negative." }
    activeDurationAsOf?.let { require(it.isNotBlank()) { "activeDurationAsOf must not be blank when provided." } }
    currentSubtaskId?.let { require(it > 0) { "currentSubtaskId must be positive when provided." } }
    require(subtaskActiveDurationMs >= 0) { "subtaskActiveDurationMs must not be negative." }
    subtaskActiveDurationAsOf?.let {
      require(it.isNotBlank()) { "subtaskActiveDurationAsOf must not be blank when provided." }
    }
    require(!pauseConsumed || pauseRequested) {
      "pauseConsumed cannot be true when pauseRequested is false."
    }
    require(!stopAfterConsumed || stopAfterSubtaskId != null) {
      "stopAfterConsumed requires stopAfterSubtaskId."
    }
    pauseReason?.let { require(it.isNotBlank()) { "pauseReason must not be blank when provided." } }
    require(!paused || pauseReason != null) { "paused control state requires pauseReason." }
    pausedAt?.let { require(it.isNotBlank()) { "pausedAt must not be blank when provided." } }
    require(!paused || pausedAt != null) { "paused control state requires pausedAt." }
    repositoryIdentity?.let {
      require(it.isNotBlank()) { "repositoryIdentity must not be blank when provided." }
    }
  }

  fun reconciledForCurrentSubtask(manifestSubtaskId: Int): GoalRunnerControlState {
    if (manifestSubtaskId <= 0) return this
    if (currentSubtaskId == manifestSubtaskId) return this
    return copy(
      currentSubtaskId = manifestSubtaskId,
      subtaskActiveDurationMs = 0,
      subtaskActiveDurationAsOf = null,
    )
  }

  fun requiresPauseBoundary(manifest: DecompositionManifest): Boolean = pauseRequested || paused || (
    stopAfterSubtaskId != null &&
      !stopAfterConsumed &&
      manifest.subtasks.any {
        it.id == stopAfterSubtaskId && it.status.decompositionStatus() == DecompositionStatus.COMPLETE
      }
    )
}
