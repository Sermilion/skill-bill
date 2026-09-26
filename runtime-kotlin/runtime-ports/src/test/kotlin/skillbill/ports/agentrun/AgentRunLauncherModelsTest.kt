package skillbill.ports.agentrun

import skillbill.goalrunner.model.GoalRunnerObservabilityRecordRequest
import skillbill.install.model.SupportedAgent
import skillbill.ports.agentrun.model.AgentRunTermination
import skillbill.ports.agentrun.model.SkillRunGoalContinuationContext
import skillbill.ports.agentrun.model.SkillRunRequest
import skillbill.ports.agentrun.model.reviewProcessOutcome
import skillbill.ports.review.model.ReviewProcessOutcome
import skillbill.workflow.model.ValidationDepth
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Duration.Companion.seconds

class AgentRunLauncherModelsTest {
  @Test
  fun `skill run goal continuation defaults validationDepth to full`() {
    val context =
      SkillRunGoalContinuationContext(
        parentIssueKey = "SKILL-173",
        subtaskId = 1,
        goalBranch = "feat/SKILL-173",
        suppressPr = true,
        specPath = ".feature-specs/SKILL-173/spec.md",
      )
    assertEquals(ValidationDepth.FULL, context.validationDepth)
  }

  @Test
  fun `skill run request allows no wall-clock cap and validates positive caps and subtask id`() {
    SkillRunRequest(issueKey = "SKILL-56", repoRoot = Path.of("."))

    assertFailsWith<IllegalArgumentException> {
      SkillRunRequest(issueKey = "SKILL-56", repoRoot = Path.of("."), timeout = 0.seconds)
    }
    assertFailsWith<IllegalArgumentException> {
      SkillRunRequest(issueKey = "SKILL-56", repoRoot = Path.of("."), subtaskId = 0)
    }
  }

  @Test
  fun `skill run request validates non-blank model and effort overrides`() {
    SkillRunRequest(
      issueKey = "SKILL-56",
      repoRoot = Path.of("."),
      modelOverride = "gpt-sol",
      effortOverride = "high",
    )

    assertFailsWith<IllegalArgumentException> {
      SkillRunRequest(issueKey = "SKILL-56", repoRoot = Path.of("."), effortOverride = " ")
    }
  }

  @Test
  fun `review fan-out is valid only on a governed review launch`() {
    assertFailsWith<IllegalArgumentException> {
      SkillRunRequest(issueKey = "SKILL-56", repoRoot = Path.of("."), reviewFanOut = true)
    }
  }

  @Test
  fun `goal observability record requests validate runtime-owned identity fields`() {
    GoalRunnerObservabilityRecordRequest(
      workflowId = "wfl-1",
      issueKey = "SKILL-61",
      subtaskId = 1,
      workflowPhase = "implement",
      workerRole = "goal_runner_supervisor",
      livenessClass = "subtask_start",
      activitySummary = "started",
      timestamp = "2026-06-01T00:00:00Z",
    )

    assertFailsWith<IllegalArgumentException> {
      GoalRunnerObservabilityRecordRequest(
        workflowId = "",
        issueKey = "SKILL-61",
        subtaskId = 1,
        workflowPhase = "implement",
        workerRole = "goal_runner_supervisor",
        livenessClass = "subtask_start",
        activitySummary = "started",
        timestamp = "2026-06-01T00:00:00Z",
      )
    }
  }

  @Test
  fun `review process outcome maps each termination and lets truncation apply only to an exit`() {
    val expectations =
      listOf(
        Triple(AgentRunTermination.TimedOut, true, ReviewProcessOutcome.TIMED_OUT),
        Triple(AgentRunTermination.TimedOut, false, ReviewProcessOutcome.TIMED_OUT),
        Triple(AgentRunTermination.Interrupted, true, ReviewProcessOutcome.INTERRUPTED),
        Triple(AgentRunTermination.Interrupted, false, ReviewProcessOutcome.INTERRUPTED),
        Triple(AgentRunTermination.SpawnFailed, true, ReviewProcessOutcome.UNAVAILABLE),
        Triple(AgentRunTermination.SpawnFailed, false, ReviewProcessOutcome.UNAVAILABLE),
        Triple(AgentRunTermination.Exited(0), true, ReviewProcessOutcome.INVALID_OUTPUT),
        Triple(AgentRunTermination.Exited(1), true, ReviewProcessOutcome.INVALID_OUTPUT),
        Triple(AgentRunTermination.Exited(1), false, ReviewProcessOutcome.NON_ZERO_EXIT),
        Triple(AgentRunTermination.Exited(0), false, ReviewProcessOutcome.ZERO_EXIT),
      )

    expectations.forEach { (termination, truncated, expected) ->
      val facts =
        agentRunLaunchFacts(
          agent = SupportedAgent.CLAUDE,
          termination = termination,
          stdoutTruncated = truncated,
        )
      assertEquals(expected, facts.reviewProcessOutcome(), "$termination truncated=$truncated")
    }
  }
}
