package skillbill.architecture

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class RuntimeEngineInboundApiTest {
  @Test
  fun `consumer modules reference only pinned engine inbound api types`() {
    val violations =
      engineInboundApiViolations(
        consumerSourceRoots =
          listOf(
            "runtime-application/src/main/kotlin",
            "runtime-cli/src/main/kotlin",
            "runtime-mcp/src/main/kotlin",
          ),
        allowedTypes = PINNED_ENGINE_INBOUND_API_TYPES,
      )
    assertEquals(
      emptyList(),
      violations,
      "runtime-application, runtime-cli, and runtime-mcp may reference only the pinned engine inbound API.",
    )
  }

  @Test
  fun `engine inbound api scanner rejects synthetic unpinned engine type reference`() {
    val violation =
      engineInboundApiViolationMessage(
        relativePath = "runtime-cli/src/main/kotlin/skillbill/cli/featuretask/SyntheticLeak.kt",
        referencedType = "skillbill.engine.featuretask.runner.FeatureTaskRuntimeRunnerPolicies",
        allowedTypes = PINNED_ENGINE_INBOUND_API_TYPES,
      )
    assertNotNull(
      violation,
      "Regression if an unpinned engine type reference is not reported.",
    )
    assertTrue(
      violation.contains("FeatureTaskRuntimeRunnerPolicies"),
      "Violation must name the unpinned engine type.",
    )
  }

  internal companion object {
    val PINNED_ENGINE_INBOUND_API_TYPES: Set<String> =
      setOf(
        "skillbill.engine.featuretask.lifecycle.continuation.FeatureTaskContinuationLookupService",
        "skillbill.engine.featuretask.phase.core.FeatureTaskPhaseSettlementService",
        "skillbill.engine.featuretask.phase.record.FeatureTaskRuntimePhaseRecorder",
        "skillbill.engine.featuretask.lifecycle.core.FeatureTaskRuntimeAgentResolver",
        "skillbill.engine.featuretask.lifecycle.branch.FeatureTaskRuntimeBranchSetup",
        "skillbill.engine.featuretask.lifecycle.core.FeatureTaskRuntimeModelResolver",
        "skillbill.engine.featuretask.runner.FeatureTaskRuntimeRunner",
        "skillbill.engine.featuretask.runner.FeatureTaskRuntimeStatusService",
        "skillbill.engine.featuretask.lifecycle.core.FeatureTaskRuntimeWorkerCoordinator",
        "skillbill.engine.featuretask.runner.OPERATOR_DECISION_QUALITY_GATE_PHASE_IDS",
        "skillbill.engine.featuretask.model.continuation.FeatureTaskContinuationCandidate",
        "skillbill.engine.featuretask.model.continuation.FeatureTaskContinuationLookupResult",
        "skillbill.engine.featuretask.model.phase.FeatureTaskPhaseSettlementBlockRequest",
        "skillbill.engine.featuretask.model.phase.FeatureTaskPhaseSettlementCompleteRequest",
        "skillbill.engine.featuretask.model.phase.FeatureTaskPhaseSettlementAcknowledgment",
        "skillbill.engine.featuretask.model.core.FeatureTaskRuntimeAgentAssignment",
        "skillbill.engine.featuretask.model.core.FeatureTaskRuntimeGoalContinuationContext",
        "skillbill.engine.featuretask.model.core.FeatureTaskRuntimeModelAssignment",
        "skillbill.engine.featuretask.model.core.FeatureTaskRuntimeOperatorDecisionPause",
        "skillbill.engine.featuretask.model.core.FeatureTaskRuntimePhaseStatus",
        "skillbill.engine.featuretask.model.core.FeatureTaskRuntimeRunEvent",
        "skillbill.engine.featuretask.model.core.FeatureTaskRuntimeRunEventSink",
        "skillbill.engine.featuretask.model.core.FeatureTaskRuntimeRunReport",
        "skillbill.engine.featuretask.model.core.FeatureTaskRuntimeRunRequest",
        "skillbill.engine.featuretask.model.core.FeatureTaskRuntimeStatusProjection",
        "skillbill.engine.featuretask.model.core.FeatureTaskRuntimeStatusRequest",
        "skillbill.engine.featuretask.model.core.FeatureTaskRuntimeSubtaskOutcome",
        "skillbill.engine.goalplanning.GoalPlanningPreparationCheckpoint",
        "skillbill.engine.goalrunner.GoalOperatorDecisionService",
        "skillbill.engine.goalrunner.GoalPreflightService",
        "skillbill.engine.goalrunner.GoalRunner",
        "skillbill.engine.goalrunner.GoalRunnerStatusService",
        "skillbill.engine.goalrunner.goalRepositoryIdentity",
        "skillbill.engine.goalrunner.findings.UnaddressedFindingsLedgerService",
        "skillbill.engine.goalrunner.model.DEFAULT_GOAL_PLANNING_BUDGET",
        "skillbill.engine.goalrunner.model.GoalPreflightRequest",
        "skillbill.engine.goalrunner.model.GoalPreflightResult",
        "skillbill.engine.goalrunner.model.GoalRunnerAcceptRequest",
        "skillbill.engine.goalrunner.model.GoalRunnerAcceptResult",
        "skillbill.engine.goalrunner.model.GoalRunnerEventSink",
        "skillbill.engine.goalrunner.model.GoalRunnerOperatorDecisionRequest",
        "skillbill.engine.goalrunner.model.GoalRunnerOperatorDecisionResult",
        "skillbill.engine.goalrunner.model.GoalRunnerPauseResult",
        "skillbill.engine.goalrunner.model.GoalRunnerPurgeRequest",
        "skillbill.engine.goalrunner.model.GoalRunnerPurgeResult",
        "skillbill.engine.goalrunner.model.GoalRunnerRepairRequest",
        "skillbill.engine.goalrunner.model.GoalRunnerRepairResult",
        "skillbill.engine.goalrunner.model.GoalRunnerRepairStatus",
        "skillbill.engine.goalrunner.model.GoalRunnerReplanRequest",
        "skillbill.engine.goalrunner.model.GoalRunnerReplanResult",
        "skillbill.engine.goalrunner.model.GoalRunnerReplanSnapshot",
        "skillbill.engine.goalrunner.model.GoalRunnerResetRequest",
        "skillbill.engine.goalrunner.model.GoalRunnerResetResult",
        "skillbill.engine.goalrunner.model.GoalRunnerResetSnapshot",
        "skillbill.engine.goalrunner.model.GoalRunnerResumeResult",
        "skillbill.engine.goalrunner.model.GoalRunnerRunRequest",
        "skillbill.engine.goalrunner.model.GoalRunnerStatusRequest",
        "skillbill.engine.goalrunner.model.GoalRunnerStopStatus",
        "skillbill.engine.goalrunner.model.GoalRunnerStopVerbResult",
        "skillbill.engine.goalrunner.planning.GoalPlanningLogService",
        "skillbill.engine.goalrunner.planning.model.GoalPlanningLog",
        "skillbill.engine.goalrunner.planning.model.GoalPlanningLogAttempt",
        "skillbill.engine.goalrunner.planning.model.GoalPlanningLogRequest",
        "skillbill.engine.work.IdeStatusProjector",
        "skillbill.engine.work.IdeStatusService",
        "skillbill.engine.work.model.IdeStatusRequest",
        "skillbill.engine.work.model.IdeStatusResult",
        "skillbill.engine.work.model.IdeStatusSnapshot",
        "skillbill.engine.work.model.IdeStatusProblemCode",
      )
  }
}
