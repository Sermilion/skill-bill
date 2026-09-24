package skillbill.infrastructure.launcher.launcher

import skillbill.agentaddon.model.AgentAddonSelection
import skillbill.agentaddon.model.PersistedAgentAddonSelectionEntry
import skillbill.contracts.workflow.identity.task.FeatureTaskRuntimeGoalContinuationLaunchTokens
import skillbill.experiment.model.ExperimentArmId
import skillbill.infrastructure.launcher.agentrun.headlessAgentRunAdapters
import skillbill.install.model.SupportedAgent
import skillbill.ports.agentrun.model.SkillRunGoalContinuationContext
import skillbill.ports.agentrun.model.SkillRunRequest
import skillbill.ports.workflow.gitops.model.GoalSubtaskReviewBaseline
import skillbill.review.context.model.launch.CodeReviewExecutionMode
import skillbill.workflow.model.ValidationDepth
import skillbill.workflow.taskruntime.model.core.FeatureTaskRuntimeQualityGateSelection
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class AgentRunGoalContinuationCommandTest {
  @Test
  fun `ordinary phase worker does not receive goal continuation marker`() {
    val runner = RecordingAgentRunProcessRunner()
    requireNotNull(adapters(runner)[SupportedAgent.CODEX]).launch(
      skillRunRequest(goalContinuation = null).copy(promptOverride = "Run the implementation phase."),
    )

    assertNull(runner.requests.single().environment["SKILL_BILL_GOAL_CONTINUATION"])
  }

  @Test
  fun `goal-continuation child with no child workflow id runs skill-bill feature-task run directly`() {
    val runner = RecordingAgentRunProcessRunner()
    val outcome =
      requireNotNull(adapters(runner)[SupportedAgent.CLAUDE])
        .launch(skillRunRequest())

    assertEquals(SupportedAgent.CLAUDE, outcome.agent)
    val request = runner.requests.single()
    assertEquals(
      listOf(
        "skill-bill",
        "--db",
        "/tmp/skillbill-agent-run/metrics.db",
        "feature-task",
        "run",
        "SKILL-56",
        ".feature-specs/SKILL-56-goal/spec_subtask_2.md",
        "--goal-parent-issue-key",
        "SKILL-56",
        "--goal-subtask-id",
        "2",
        "--goal-branch",
        "feat/SKILL-56-goal",
        "--suppress-pr",
        "--goal-parent-workflow-id",
        "wfl-parent",
        "--goal-last-resumable-step",
        "implement",
        "--code-review-mode",
        "inline",
        "--quality-gate-selection",
        "validate",
        "--agent",
        "claude",
      ),
      request.command,
    )
    assertNull(request.stdinText)
    assertFalse(request.command.any { value -> "bill-feature-task" in value })
    assertFalse(request.command.any { value -> value == "claude" && request.command.indexOf(value) == 0 })
    assertEquals("1", request.environment["SKILL_BILL_GOAL_CONTINUATION"])
    assertEquals("inline", request.environment["SKILL_BILL_CODE_REVIEW_MODE"])
    assertEquals("full", request.environment["SKILL_BILL_VALIDATION_DEPTH"])
    assertTrue(request.inheritEnvironment)
  }

  @Test
  fun `goal-continuation child with existing child workflow id runs feature-task resume`() {
    val runner = RecordingAgentRunProcessRunner()
    requireNotNull(adapters(runner)[SupportedAgent.CLAUDE]).launch(
      skillRunRequest(goalContinuation = goalContinuationContext(childWorkflowId = "wfl-child-runtime")),
    )

    val request = runner.requests.single()
    assertEquals(
      listOf(
        "skill-bill",
        "--db",
        "/tmp/skillbill-agent-run/metrics.db",
        "feature-task",
        "resume",
        "wfl-child-runtime",
        "SKILL-56",
        ".feature-specs/SKILL-56-goal/spec_subtask_2.md",
        "--goal-parent-issue-key",
        "SKILL-56",
        "--goal-subtask-id",
        "2",
        "--goal-branch",
        "feat/SKILL-56-goal",
        "--suppress-pr",
        "--goal-parent-workflow-id",
        "wfl-parent",
        "--goal-last-resumable-step",
        "implement",
        "--code-review-mode",
        "inline",
        "--quality-gate-selection",
        "validate",
        "--agent",
        "claude",
      ),
      request.command,
    )
    assertNull(request.stdinText)
    assertFalse(request.command.any { value -> "bill-feature-task" in value })
    assertFalse(request.command.contains("--workflow-id"))
  }

  @Test
  fun `goal-continuation child with assigned workflow id and no child runs feature-task run with workflow-id`() {
    val runner = RecordingAgentRunProcessRunner()
    requireNotNull(adapters(runner)[SupportedAgent.CLAUDE]).launch(
      skillRunRequest(
        goalContinuation = goalContinuationContext(childWorkflowId = null, assignedWorkflowId = "wfl-assigned"),
      ),
    )

    val request = runner.requests.single()
    assertContains(request.command, "feature-task")
    assertContains(request.command, "run")
    assertContains(request.command, "SKILL-56")
    assertContains(request.command, ".feature-specs/SKILL-56-goal/spec_subtask_2.md")
    val workflowIdFlagIndex = request.command.indexOf("--workflow-id")
    assertTrue(workflowIdFlagIndex >= 0)
    assertEquals("wfl-assigned", request.command[workflowIdFlagIndex + 1])
    assertFalse(request.command.contains("resume"))
  }

  @Test
  fun `every agent goal-continuation child spawns skill-bill feature-task and never the skill`() {
    val runner = RecordingAgentRunProcessRunner()

    listOf(SupportedAgent.CLAUDE, SupportedAgent.CODEX, SupportedAgent.JUNIE, SupportedAgent.CURSOR).forEach { agent ->
      requireNotNull(adapters(runner)[agent]).launch(skillRunRequest())
    }

    assertEquals(4, runner.requests.size)
    runner.requests.forEach { request ->
      assertEquals(
        listOf("skill-bill", "--db", "/tmp/skillbill-agent-run/metrics.db", "feature-task"),
        request.command.take(4),
      )
      assertContains(request.command, "run")
      assertContains(request.command, "--suppress-pr")
      assertNull(request.stdinText)
      assertFalse(request.command.any { value -> "bill-feature-task" in value })
      assertFalse(request.command.any { value -> "workflow continue" in value })
      assertFalse(request.command.any { value -> "use the" in value.lowercase() && "skill" in value.lowercase() })
    }
    val perAgent = listOf("claude", "codex", "junie", "cursor")
    runner.requests.forEachIndexed { index, request ->
      assertEquals(perAgent[index], request.command.last())
      assertEquals("--agent", request.command[request.command.size - 2])
    }
  }

  @Test
  fun `goal-continuation child always carries suppress-pr`() {
    val runner = RecordingAgentRunProcessRunner()
    requireNotNull(adapters(runner)[SupportedAgent.CODEX])
      .launch(skillRunRequest())

    assertContains(runner.requests.single().command, "--suppress-pr")
  }

  @Test
  fun `goal-continuation wrapper never receives model or effort flags`() {
    val runner = RecordingAgentRunProcessRunner()
    requireNotNull(adapters(runner)[SupportedAgent.CODEX]).launch(
      skillRunRequest().copy(modelOverride = "gpt-sol", effortOverride = "high"),
    )

    val command = runner.requests.single().command
    assertFalse(command.contains("--model"))
    assertFalse(command.contains("--effort"))
    assertFalse(command.any { it.startsWith("model_reasoning_effort=") })
  }

  private fun skillRunRequest(
    goalContinuation: SkillRunGoalContinuationContext? = goalContinuationContext(),
  ): SkillRunRequest =
    SkillRunRequest(
      issueKey = "SKILL-56",
      repoRoot = Path.of("/tmp/skillbill-agent-run"),
      subtaskId = 2,
      timeout = 3.seconds,
      goalContinuation = goalContinuation,
    )

  private fun adapters(runner: RecordingAgentRunProcessRunner) =
    headlessAgentRunAdapters(
      runner,
      ALL_EXECUTABLES_AVAILABLE,
      Path.of("/tmp/skillbill-agent-run/metrics.db"),
    )

  private fun goalContinuationContext(
    childWorkflowId: String? = null,
    assignedWorkflowId: String? = null,
  ): SkillRunGoalContinuationContext =
    SkillRunGoalContinuationContext(
      parentIssueKey = "SKILL-56",
      subtaskId = 2,
      goalBranch = "feat/SKILL-56-goal",
      suppressPr = true,
      specPath = ".feature-specs/SKILL-56-goal/spec_subtask_2.md",
      parentWorkflowId = "wfl-parent",
      lastResumableStep = "implement",
      childWorkflowId = childWorkflowId,
      assignedWorkflowId = assignedWorkflowId,
    )

  @Test
  fun `cursor goal-continuation child with no child workflow id runs skill-bill feature-task run directly`() {
    val runner = RecordingAgentRunProcessRunner()
    val outcome =
      requireNotNull(adapters(runner)[SupportedAgent.CURSOR])
        .launch(skillRunRequest())

    assertEquals(SupportedAgent.CURSOR, outcome.agent)
    val request = runner.requests.single()
    assertEquals(
      listOf(
        "skill-bill",
        "--db",
        "/tmp/skillbill-agent-run/metrics.db",
        "feature-task",
        "run",
        "SKILL-56",
        ".feature-specs/SKILL-56-goal/spec_subtask_2.md",
        "--goal-parent-issue-key",
        "SKILL-56",
        "--goal-subtask-id",
        "2",
        "--goal-branch",
        "feat/SKILL-56-goal",
        "--suppress-pr",
        "--goal-parent-workflow-id",
        "wfl-parent",
        "--goal-last-resumable-step",
        "implement",
        "--code-review-mode",
        "inline",
        "--quality-gate-selection",
        "validate",
        "--agent",
        "cursor",
      ),
      request.command,
    )
    assertNull(request.stdinText)
    assertEquals("1", request.environment["SKILL_BILL_GOAL_CONTINUATION"])
    assertEquals("inline", request.environment["SKILL_BILL_CODE_REVIEW_MODE"])
    assertEquals("full", request.environment["SKILL_BILL_VALIDATION_DEPTH"])
    assertTrue(request.inheritEnvironment)
  }

  @Test
  fun `cursor goal-continuation child with existing child workflow id runs feature-task resume`() {
    val runner = RecordingAgentRunProcessRunner()
    requireNotNull(adapters(runner)[SupportedAgent.CURSOR]).launch(
      skillRunRequest(goalContinuation = goalContinuationContext(childWorkflowId = "wfl-child-runtime")),
    )

    val request = runner.requests.single()
    assertEquals(
      listOf(
        "skill-bill",
        "--db",
        "/tmp/skillbill-agent-run/metrics.db",
        "feature-task",
        "resume",
        "wfl-child-runtime",
        "SKILL-56",
        ".feature-specs/SKILL-56-goal/spec_subtask_2.md",
        "--goal-parent-issue-key",
        "SKILL-56",
        "--goal-subtask-id",
        "2",
        "--goal-branch",
        "feat/SKILL-56-goal",
        "--suppress-pr",
        "--goal-parent-workflow-id",
        "wfl-parent",
        "--goal-last-resumable-step",
        "implement",
        "--code-review-mode",
        "inline",
        "--quality-gate-selection",
        "validate",
        "--agent",
        "cursor",
      ),
      request.command,
    )
    assertNull(request.stdinText)
    assertFalse(request.command.contains("--workflow-id"))
  }

  @Test
  fun `cursor goal-continuation child with assigned workflow id and no child runs feature-task run with workflow-id`() {
    val runner = RecordingAgentRunProcessRunner()
    requireNotNull(adapters(runner)[SupportedAgent.CURSOR]).launch(
      skillRunRequest(
        goalContinuation = goalContinuationContext(childWorkflowId = null, assignedWorkflowId = "wfl-assigned"),
      ),
    )

    val request = runner.requests.single()
    assertContains(request.command, "feature-task")
    assertContains(request.command, "run")
    assertContains(request.command, "SKILL-56")
    assertContains(request.command, ".feature-specs/SKILL-56-goal/spec_subtask_2.md")
    val workflowIdFlagIndex = request.command.indexOf("--workflow-id")
    assertTrue(workflowIdFlagIndex >= 0)
    assertEquals("wfl-assigned", request.command[workflowIdFlagIndex + 1])
    assertFalse(request.command.contains("resume"))
    assertEquals("cursor", request.command.last())
    assertEquals("--agent", request.command[request.command.size - 2])
  }

  @Test
  fun `goal-continuation builder stamps full validation depth in environment only`() {
    val runner = RecordingAgentRunProcessRunner()
    val adapter = requireNotNull(adapters(runner)[SupportedAgent.CLAUDE])

    adapter.launch(
      skillRunRequest(goalContinuation = goalContinuationContext().copy(validationDepth = ValidationDepth.FULL)),
    )

    val request = runner.requests.single()
    assertFalse(request.command.contains("--validation-depth"))
    assertEquals("full", request.environment["SKILL_BILL_VALIDATION_DEPTH"])
  }

  @Test
  fun `goal-continuation launch emits every context field for resume and assigned run`() {
    listOf(
      goalContinuationContext(childWorkflowId = "wfl-child-runtime", assignedWorkflowId = null),
      goalContinuationContext(childWorkflowId = null, assignedWorkflowId = "wfl-assigned"),
    ).forEach { continuation ->
      val runner = RecordingAgentRunProcessRunner()
      requireNotNull(adapters(runner)[SupportedAgent.CLAUDE]).launch(
        skillRunRequest(goalContinuation = fullyPopulated(continuation)),
      )

      val request = runner.requests.single()
      val tokens = FeatureTaskRuntimeGoalContinuationLaunchTokens
      assertContains(request.command, tokens.GOAL_PARENT_ISSUE_KEY_FLAG)
      assertContains(request.command, "SKILL-56")
      assertContains(request.command, tokens.GOAL_SUBTASK_ID_FLAG)
      assertContains(request.command, "2")
      assertContains(request.command, tokens.GOAL_BRANCH_FLAG)
      assertContains(request.command, "feat/SKILL-56-goal")
      assertContains(request.command, tokens.SUPPRESS_PR_FLAG)
      assertContains(request.command, tokens.GOAL_PARENT_WORKFLOW_ID_FLAG)
      assertContains(request.command, "wfl-parent")
      assertContains(request.command, tokens.GOAL_LAST_RESUMABLE_STEP_FLAG)
      assertContains(request.command, "implement")
      assertContains(request.command, tokens.CODE_REVIEW_MODE_FLAG)
      assertContains(request.command, CodeReviewExecutionMode.AUTO.wireValue)
      assertContains(request.command, tokens.QUALITY_GATE_SELECTION_FLAG)
      assertContains(request.command, FeatureTaskRuntimeQualityGateSelection.BUILD.wireValue)
      assertContains(request.command, tokens.GOAL_EXPERIMENT_ARM_ID_FLAG)
      assertContains(request.command, ExperimentArmId.TREATMENT.wireValue)
      assertContains(request.command, tokens.GOAL_EXPERIMENT_TREATMENT_CAPABILITIES_FLAG)
      assertContains(request.command, "capability-a")
      assertContains(request.command, "capability-b")
      assertContains(request.command, tokens.DEFER_REMOTE_PUBLICATION_FLAG)
      assertContains(request.command, tokens.GOAL_REVIEW_BASE_SHA_FLAG)
      assertContains(request.command, "a".repeat(40))
      assertContains(request.command, tokens.GOAL_BASELINE_UNTRACKED_PATH_FLAG)
      assertContains(request.command, "existing.txt")
      assertContains(request.command, tokens.AGENT_ADDON_SELECTION_JSON_FLAG)
      assertTrue(request.command.any { "review-helper" in it })
      assertEquals("1", request.environment[tokens.GOAL_CONTINUATION_ENV])
      assertEquals("SKILL-56", request.environment[tokens.GOAL_PARENT_ISSUE_KEY_ENV])
      assertEquals("2", request.environment[tokens.GOAL_SUBTASK_ID_ENV])
      assertEquals("feat/SKILL-56-goal", request.environment[tokens.GOAL_BRANCH_ENV])
      assertEquals("true", request.environment[tokens.SUPPRESS_PR_ENV])
      assertEquals("wfl-parent", request.environment[tokens.GOAL_PARENT_WORKFLOW_ID_ENV])
      assertEquals("implement", request.environment[tokens.GOAL_LAST_RESUMABLE_STEP_ENV])
      assertEquals(CodeReviewExecutionMode.AUTO.wireValue, request.environment[tokens.CODE_REVIEW_MODE_ENV])
      assertEquals(ValidationDepth.FULL.wireValue, request.environment[tokens.VALIDATION_DEPTH_ENV])
      assertEquals(
        FeatureTaskRuntimeQualityGateSelection.BUILD.wireValue,
        request.environment[tokens.QUALITY_GATE_SELECTION_ENV],
      )
      assertEquals(ExperimentArmId.TREATMENT.wireValue, request.environment[tokens.GOAL_EXPERIMENT_ARM_ID_ENV])
      assertEquals(
        "capability-a,capability-b",
        request.environment[tokens.GOAL_EXPERIMENT_TREATMENT_CAPABILITIES_ENV],
      )
      assertEquals("true", request.environment[tokens.DEFER_REMOTE_PUBLICATION_ENV])
    }
  }

  private fun fullyPopulated(context: SkillRunGoalContinuationContext): SkillRunGoalContinuationContext =
    context.copy(
      experimentArmId = ExperimentArmId.TREATMENT,
      experimentTreatmentCapabilities = linkedSetOf("capability-a", "capability-b"),
      deferRemotePublication = true,
      codeReviewMode = CodeReviewExecutionMode.AUTO,
      validationDepth = ValidationDepth.FULL,
      qualityGateSelection = FeatureTaskRuntimeQualityGateSelection.BUILD,
      reviewBaseline = GoalSubtaskReviewBaseline("a".repeat(40), listOf("existing.txt")),
      agentAddonSelection =
        AgentAddonSelection(
          listOf(
            PersistedAgentAddonSelectionEntry("review-helper", "source:review-helper", "b".repeat(64)),
          ),
        ),
    )
}
