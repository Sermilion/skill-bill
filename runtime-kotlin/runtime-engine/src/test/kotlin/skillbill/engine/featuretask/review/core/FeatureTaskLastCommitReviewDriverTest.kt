package skillbill.engine.featuretask.review.core

import skillbill.application.review.model.ParallelCodeReviewRequest
import skillbill.application.reviewevidence.model.ParallelReviewScope
import skillbill.engine.featuretask.model.phase.FeatureTaskRuntimePhaseSettlementTarget
import skillbill.engine.featuretask.slot.PhaseLaunchObservation
import skillbill.engine.featuretask.slot.PhaseRunState
import skillbill.engine.featuretask.slot.PhaseSettledEnvelopeRead
import skillbill.engine.featuretask.slot.runner.DefaultPhaseRunner
import skillbill.install.model.SupportedAgent
import skillbill.ports.agentrun.agentRunLaunchFacts
import skillbill.ports.agentrun.model.AgentRunTermination
import skillbill.ports.agentrun.model.UnsupportedAgentRunLaunch
import skillbill.ports.goalrunner.runner.GoalRunnerSubtaskLauncher
import skillbill.ports.goalrunner.runner.model.GoalRunnerSubtaskLaunchRequest
import skillbill.ports.workflow.gitops.NoopWorkflowGitOperations
import skillbill.review.context.model.launch.CodeReviewExecutionMode
import skillbill.review.model.ParallelReviewSeverity
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FeatureTaskLastCommitReviewDriverTest {
  @Test
  fun `last-commit review launches one mutating agent in the repo without evidence isolation`() {
    val captured = mutableListOf<GoalRunnerSubtaskLaunchRequest>()
    val driver =
      lastCommitDriver(
        GoalRunnerSubtaskLauncher { launch ->
          captured += launch
          agentRunLaunchFacts(
            agent = SupportedAgent.CURSOR,
            stdout = "reviewed last commit\nverdict: approved",
            stderr = "",
          )
        },
      )

    val result = driver.run(reviewRequest())

    val launch = captured.single()
    val skill = launch.skillRunRequest
    assertEquals("cursor", launch.invokedAgentId)
    assertNull(launch.configuredAgentOverrideId)
    assertEquals(Path.of("/tmp/repo"), skill.repoRoot)
    assertEquals("wftr-review-child", skill.issueKey)
    assertNull(skill.reviewEvidenceBroker)
    assertNull(skill.reviewEvidenceEndpoint)
    assertFalse(skill.readOnlyPhase)
    assertNull(skill.progressIdleTimeout)
    val prompt = requireNotNull(skill.promptOverride)
    assertTrue(prompt.contains("git diff abc^ def"))
    assertTrue(prompt.contains("Fix every Blocker and Major"))
    assertTrue(prompt.contains("You may edit files"))
    assertTrue(prompt.contains("the runtime owns the review checkpoint"))
    assertTrue(result.lane1.success)
    assertEquals("reviewed last commit\nverdict: approved", result.output)
    assertEquals(emptyList(), result.mergeResult.findings)
  }

  @Test
  fun `remaining blocker findings parse into the driver register`() {
    val driver =
      lastCommitDriver(
        GoalRunnerSubtaskLauncher {
          agentRunLaunchFacts(
            agent = SupportedAgent.CURSOR,
            stdout = "- [F-001] Blocker | High | src/main/App.kt:42 | remaining defect\nverdict: changes_requested",
            stderr = "",
          )
        },
      )

    val result = driver.run(reviewRequest())

    val finding = result.mergeResult.findings.single()
    assertEquals(ParallelReviewSeverity.BLOCKER, finding.severity)
    assertEquals("src/main/App.kt", finding.repositoryPath)
    assertEquals(42, finding.line)
    assertEquals("remaining defect", finding.description)
    assertTrue(result.lane1.success)
  }

  @Test
  fun `a hung child is a failed lane not an approved empty register`() {
    val driver =
      lastCommitDriver(
        GoalRunnerSubtaskLauncher {
          agentRunLaunchFacts(
            agent = SupportedAgent.CURSOR,
            termination = AgentRunTermination.TimedOut,
            stdout = "",
            stderr = "",
          )
        },
      )

    val result = driver.run(reviewRequest())

    assertFalse(result.lane1.success)
    assertEquals("agent timed out", result.lane1.failureReason)
    assertEquals(emptyList(), result.mergeResult.findings)
  }

  @Test
  fun `unsupported agent fails the lane`() {
    val driver =
      lastCommitDriver(
        GoalRunnerSubtaskLauncher {
          UnsupportedAgentRunLaunch(agent = SupportedAgent.CURSOR, reason = "cursor is not installed")
        },
      )

    val result = driver.run(reviewRequest())

    assertFalse(result.lane1.success)
    assertEquals("cursor is not installed", result.lane1.failureReason)
  }

  private fun lastCommitDriver(launcher: GoalRunnerSubtaskLauncher) =
    FeatureTaskLastCommitReviewDriver(DefaultPhaseRunner(launcher, NoopWorkflowGitOperations), UntrackedRunState)

  private object UntrackedRunState : PhaseRunState {
    override fun settlementTarget(attempt: Int): FeatureTaskRuntimePhaseSettlementTarget =
      error("An untracked review launch must not pin a settlement target.")

    override fun launchObservation(stepName: String): PhaseLaunchObservation =
      error("An untracked review launch must not observe the worktree.")

    override fun recordTokenUsage(
      stepName: String,
      inputTokens: Int,
      outputTokens: Int,
    ): Unit = error("An untracked review launch must not record token usage.")

    override fun settledEnvelope(
      stepName: String,
      target: FeatureTaskRuntimePhaseSettlementTarget,
    ): PhaseSettledEnvelopeRead = error("An untracked review launch must not read a settled envelope.")
  }

  private fun reviewRequest() =
    ParallelCodeReviewRequest(
      agent1Id = "cursor",
      scope = ParallelReviewScope.BRANCH,
      repoRoot = Path.of("/tmp/repo"),
      timeout = null,
      codeReviewMode = CodeReviewExecutionMode.INLINE,
      reviewRunId = "rvw-last-commit",
      activityWorkflowId = "wftr-review-child",
      baseRevision = "abc^",
      headRevision = "def",
    )
}
