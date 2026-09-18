package skillbill.ports.goalrunner.runner.model
import skillbill.goalrunner.model.GoalAttemptLedgerEntry
import skillbill.goalrunner.model.GoalObservabilityProgressEvent
import skillbill.goalrunner.model.GoalRunnerControlState
import skillbill.goalrunner.model.GoalRunnerProgressEvent
import skillbill.ports.agentrun.model.AgentRunSpawnAuthorization
import skillbill.ports.agentrun.model.SkillRunRequest
import skillbill.ports.goalrunner.model.GoalPlanningIdentity
import skillbill.workflow.decomposition.model.DecompositionManifest
import skillbill.workflow.goal.model.GoalProgressEvent
import skillbill.workflow.model.WorkflowStatus
import java.nio.file.Path

data class GoalRunnerManifestState(
  val parentWorkflowId: String,
  val dbPath: String,
  val manifest: DecompositionManifest,
  val controlState: GoalRunnerControlState = GoalRunnerControlState(),

  val repoRoot: Path? = null,
)

data class GoalRunnerCompletionPersistenceResult(
  val state: GoalRunnerManifestState,
  val paused: Boolean,
)

data class GoalRunnerScopedReplanWriteResult(
  val state: GoalRunnerManifestState,
  val deletedPlanCount: Int,
  val plannedSubtaskIdsBefore: List<Int>,
  val plannedSubtaskIdsAfter: List<Int>,
  val sharedPreplanPrepared: Boolean,
  val sharedPreplanPreparedBefore: Boolean = sharedPreplanPrepared,
  val discardedSharedPreplan: Boolean = false,
  val cascadedPlanSubtaskIds: List<Int> = emptyList(),

  val clearedChildSubtaskIds: List<Int> = emptyList(),
)

data class GoalRunnerScopedReplanOptions(
  val includeSharedPreplan: Boolean = false,
  val expectedSharedPayloadSha256: String? = null,
  val planningIdentity: GoalPlanningIdentity? = null,
)

data class GoalRunnerPausePersistenceResult(
  val parentWorkflowId: String,
  val controlState: GoalRunnerControlState,
)

data class GoalRunnerLaunchAuthorization(
  val authorized: Boolean,
  val controlState: GoalRunnerControlState,
  val spawnAuthorization: AgentRunSpawnAuthorization? = null,
)

data class GoalRunnerReconcileGate(
  val allowInactiveReconciliation: Boolean = true,
  val requireStalenessEvidence: Boolean = false,
)

data class GoalRunnerSubtaskLaunchRequest(
  val invokedAgentId: String,
  val configuredAgentOverrideId: String?,
  val skillRunRequest: SkillRunRequest,
)

data class GoalRunnerWorkflowProgress(
  val workflowId: String,
  val workflowStatus: WorkflowStatus,
  val currentStepId: String,
  val progressToken: String,
  val latestDurableProgressEvent: GoalRunnerProgressEvent? = null,
  val latestGoalObservabilityEvent: GoalObservabilityProgressEvent? = null,

  val latestDeclaredProgressEvent: GoalProgressEvent? = null,
  val latestLivenessSignal: String? = null,
  val lastSnapshotUpdatedAt: String? = null,
) {
  constructor(
    workflowId: String,
    workflowStatus: String,
    currentStepId: String,
    progressToken: String,
    latestDurableProgressEvent: GoalRunnerProgressEvent? = null,
    latestGoalObservabilityEvent: GoalObservabilityProgressEvent? = null,
    latestDeclaredProgressEvent: GoalProgressEvent? = null,
    latestLivenessSignal: String? = null,
    lastSnapshotUpdatedAt: String? = null,
  ) : this(
    workflowId = workflowId,
    workflowStatus = requireNotNull(WorkflowStatus.fromWire(workflowStatus)) {
      "Unknown workflow status '$workflowStatus'."
    },
    currentStepId = currentStepId,
    progressToken = progressToken,
    latestDurableProgressEvent = latestDurableProgressEvent,
    latestGoalObservabilityEvent = latestGoalObservabilityEvent,
    latestDeclaredProgressEvent = latestDeclaredProgressEvent,
    latestLivenessSignal = latestLivenessSignal,
    lastSnapshotUpdatedAt = lastSnapshotUpdatedAt,
  )
}

data class GoalRunnerProgressEventRecordRequest(
  val workflowId: String,
  val event: GoalProgressEvent,
) {
  init {
    require(workflowId.isNotBlank()) { "workflowId is required." }
  }
}

data class GoalRunnerAttemptLedgerRecordRequest(
  val workflowId: String,
  val entry: GoalAttemptLedgerEntry,
) {
  init {
    require(workflowId.isNotBlank()) { "workflowId is required." }
  }
}

data class GoalRunnerLedgerSequenceWatermarks(
  val maxLedgerSequence: Int? = null,
  val maxProgressSequence: Int? = null,
  val backwardEdgeCounts: Map<String, Int> = emptyMap(),
)

data class GoalRunnerOutOfBandAcceptance(
  val subtaskId: Int,
  val commitSha: String,
  val reason: String,
  val acceptedAt: String,
) {
  init {
    require(subtaskId > 0) { "subtaskId must be positive." }
    require(commitSha.isNotBlank()) { "commitSha is required." }
    require(reason.isNotBlank()) { "reason is required." }
    require(acceptedAt.isNotBlank()) { "acceptedAt is required." }
  }
}

data class GoalPullRequestRequest(
  val repoRoot: Path,
  val issueKey: String,
  val featureName: String,
  val baseBranch: String,
  val headBranch: String,
  val title: String,
  val body: String,
)

sealed interface GoalPullRequestResult {
  data class Opened(val url: String) : GoalPullRequestResult
  data class Existing(val url: String) : GoalPullRequestResult
  data class Failed(val reason: String) : GoalPullRequestResult
}
