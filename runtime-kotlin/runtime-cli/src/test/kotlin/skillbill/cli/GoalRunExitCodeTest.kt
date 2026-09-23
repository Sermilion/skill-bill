package skillbill.cli

import skillbill.goalrunner.model.GoalRunnerRunReport
import skillbill.cli.goal.core.goalRunExitCode
import skillbill.goalrunner.model.GoalPullRequestStatus
import skillbill.goalrunner.model.GoalRunnerStopReason
import skillbill.goalrunner.model.GoalRunnerStopReport
import kotlin.test.Test
import kotlin.test.assertEquals

class GoalRunExitCodeTest {
  @Test
  fun `complete exits 0`() {
    assertEquals(
      0,
      GoalRunnerRunReport.Completed(
          issueKey = "SKILL-371",
          attemptedSubtasks = emptyList(),
          pullRequestUrl = null,
          pullRequestStatus = GoalPullRequestStatus.DEFERRED,
          subtasksCompleted = 1,
          subtasksPending = 0,
          subtasksBlocked = 0,
        ).goalRunExitCode(),
    )
  }

  @Test
  fun `paused exits 2 not 1`() {
    assertEquals(2, stopped(GoalRunnerStopReason.PAUSED, "paused").goalRunExitCode())
  }

  @Test
  fun `failed exits 1`() {
    assertEquals(1, stopped(GoalRunnerStopReason.FAILED, "failed").goalRunExitCode())
  }

  @Test
  fun `timeout classifies as failed exit 1`() {
    assertEquals(1, stopped(GoalRunnerStopReason.TIMEOUT, "timed out").goalRunExitCode())
  }

  @Test
  fun `blocked exits 3`() {
    assertEquals(3, stopped(GoalRunnerStopReason.BLOCKED, "dependency failed to resolve").goalRunExitCode())
  }

  @Test
  fun `policy_blocked exits 3`() {
    assertEquals(3, stopped(GoalRunnerStopReason.POLICY_BLOCKED, "policy denied").goalRunExitCode())
  }

  private fun stopped(reason: GoalRunnerStopReason, blockedReason: String): GoalRunnerRunReport =
    GoalRunnerRunReport.Stopped(
      issueKey = "SKILL-371",
      attemptedSubtasks = listOf(1),
      stop =
        GoalRunnerStopReport(
          issueKey = "SKILL-371",
          subtaskId = 1,
          reason = reason,
          blockedReason = blockedReason,
          workflowId = "workflow-1",
          lastResumableStep = "review",
        ),
    )
  }
