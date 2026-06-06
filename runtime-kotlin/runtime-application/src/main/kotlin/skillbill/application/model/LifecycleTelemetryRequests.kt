package skillbill.application.model

import skillbill.boundary.OpenBoundaryMap

data class FeatureImplementStartedRequest(
  val featureSize: String,
  val source: String = "production",
  val acceptanceCriteriaCount: Int,
  val openQuestionsCount: Int,
  val specInputTypes: List<String>,
  val specWordCount: Int,
  val rolloutNeeded: Boolean,
  val featureName: String,
  val issueKey: String,
  val issueKeyType: String,
  val specSummary: String,
)

data class FeatureImplementFinishedRequest(
  val sessionId: String,
  val source: String = "production",
  val completionStatus: String,
  val planCorrectionCount: Int,
  val planTaskCount: Int,
  val planPhaseCount: Int,
  val featureFlagUsed: Boolean,
  val filesCreated: Int,
  val filesModified: Int,
  val tasksCompleted: Int,
  val reviewIterations: Int,
  val auditResult: String,
  val auditIterations: Int,
  val validationResult: String,
  val boundaryHistoryWritten: Boolean,
  val prCreated: Boolean,
  val featureFlagPattern: String,
  val boundaryHistoryValue: String,
  val planDeviationNotes: String,
  @OpenBoundaryMap("Caller-supplied JSON child-step telemetry payload")
  val childSteps: List<Map<String, Any?>>,
)

data class FeatureTaskRuntimeStartedRequest(
  val featureSize: String,
  val issueKey: String,
  val featureName: String,
  val sessionId: String = "",
)

data class FeatureTaskRuntimeFinishedRequest(
  val sessionId: String,
  val completionStatus: String,
  val completedPhaseIds: List<String>,
  val phaseOutcomes: Map<String, String>,
  val lastIncompletePhase: String,
  val blockedReason: String,
  val resolvedBranch: String,
  val parallelReviewRequested: Boolean = false,
  val defaultReviewAgentId: String = "",
  val alternativeReviewAgentId: String = "",
  val reviewLaneCount: Int = 1,
  val reviewLaneStatuses: List<FeatureTaskRuntimeReviewLaneTelemetry> = emptyList(),
  val mergedReviewFindingCount: Int = 0,
  val acceptedReviewFindingCount: Int = 0,
  val rejectedReviewFindingCount: Int = 0,
  val unresolvedReviewFindingCount: Int = 0,
)

data class FeatureTaskRuntimeReviewLaneTelemetry(
  val laneId: String,
  val agentId: String,
  val status: String,
  val findingCount: Int,
)

data class FeatureTaskRuntimeParallelReviewTelemetry(
  val requested: Boolean,
  val defaultReviewAgentId: String,
  val alternativeReviewAgentId: String,
  val laneCount: Int,
  val laneStatuses: List<FeatureTaskRuntimeReviewLaneTelemetry>,
  val mergedFindingCount: Int,
  val acceptedFindingCount: Int,
  val rejectedFindingCount: Int,
  val unresolvedFindingCount: Int,
) {
  companion object {
    val NONE = FeatureTaskRuntimeParallelReviewTelemetry(
      requested = false,
      defaultReviewAgentId = "",
      alternativeReviewAgentId = "",
      laneCount = 1,
      laneStatuses = emptyList(),
      mergedFindingCount = 0,
      acceptedFindingCount = 0,
      rejectedFindingCount = 0,
      unresolvedFindingCount = 0,
    )
  }
}

data class QualityCheckStartedRequest(
  val routedSkill: String,
  val detectedStack: String,
  val scopeType: String,
  val initialFailureCount: Int,
  val orchestrated: Boolean,
)

data class QualityCheckFinishedRequest(
  val finalFailureCount: Int,
  val iterations: Int,
  val result: String,
  val sessionId: String,
  val failingCheckNames: List<String>,
  val unsupportedReason: String,
  val orchestrated: Boolean,
  val routedSkill: String,
  val detectedStack: String,
  val scopeType: String,
  val initialFailureCount: Int,
  val durationSeconds: Int,
)

data class FeatureVerifyStartedRequest(
  val acceptanceCriteriaCount: Int,
  val rolloutRelevant: Boolean,
  val specSummary: String,
  val orchestrated: Boolean,
)

data class FeatureVerifyFinishedRequest(
  val featureFlagAuditPerformed: Boolean,
  val reviewIterations: Int,
  val auditResult: String,
  val completionStatus: String,
  val historyRelevance: String,
  val historyHelpfulness: String,
  val sessionId: String,
  val gapsFound: List<String>,
  val orchestrated: Boolean,
  val acceptanceCriteriaCount: Int,
  val rolloutRelevant: Boolean,
  val specSummary: String,
  val durationSeconds: Int,
)

data class PrDescriptionGeneratedRequest(
  val commitCount: Int,
  val filesChangedCount: Int,
  val wasEditedByUser: Boolean,
  val prCreated: Boolean,
  val prTitle: String,
  val orchestrated: Boolean,
)

data class GoalStartedRequest(
  val issueKey: String,
  val featureName: String,
  val workflowId: String,
  val subtaskTotal: Int,
  val resumed: Boolean,
  val startedAt: String,
)

data class GoalSubtaskFinishedRequest(
  val issueKey: String,
  val workflowId: String,
  val subtaskId: Int,
  val subtaskName: String,
  val status: String,
  val startedAt: String,
  val finishedAt: String,
  val durationMs: Long,
  val attemptCount: Int,
  val blockedReason: String?,
)

data class GoalFinishedRequest(
  val issueKey: String,
  val workflowId: String,
  val status: String,
  val startedAt: String,
  val finishedAt: String,
  val durationMs: Long,
  val subtasksComplete: Int,
  val subtasksBlocked: Int,
  val subtasksSkipped: Int,
)
