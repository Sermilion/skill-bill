package skillbill.cli

import skillbill.cli.core.CliRuntime
import skillbill.cli.goal.goalResetExitCode
import skillbill.cli.goal.goalResetText
import skillbill.cli.goal.toGoalResetCliMap
import skillbill.contracts.SharedPayloadKeys
import skillbill.contracts.goalrunner.GoalRunnerResetPayloadKeys
import skillbill.engine.goalrunner.model.GoalRunnerResetResult
import skillbill.engine.goalrunner.model.GoalRunnerResetSnapshot
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

class CliGoalResetOptionGateTest {
  @Test
  fun `goal reset rejects preserve-planning without hard`() {
    val fixture = goalFixture(subtaskCount = 1)
    val launcher = GoalFixtureAgentRunLauncher(fixture)

    val rejected = CliRuntime.run(
      listOf("--db", fixture.dbPath.toString(), "goal", "reset", "SKILL-901", "--preserve-planning"),
      fixture.context(launcher = launcher),
    )

    assertEquals(1, rejected.exitCode, rejected.stdout)
    assertContains(rejected.stdout, "--preserve-planning only applies to a hard reset")
  }

  @Test
  fun `goal reset accepts preserve-planning with a confirmed hard reset`() {
    val fixture = goalFixture(subtaskCount = 1)
    val launcher = GoalFixtureAgentRunLauncher(fixture)

    val accepted = CliRuntime.run(
      listOf(
        "--db",
        fixture.dbPath.toString(),
        "goal",
        "reset",
        "SKILL-901",
        "--hard",
        "--preserve-planning",
        "--confirm-issue-key",
        "SKILL-901",
        "--repo-root",
        fixture.tempDir.toString(),
      ),
      fixture.context(launcher = launcher),
    )

    assertEquals(0, accepted.exitCode, accepted.stdout)
    assertContains(accepted.stdout, "mode: hard")
  }

  @Test
  fun `goal reset accepts yes as a hard reset confirmation bypass`() {
    val fixture = goalFixture(subtaskCount = 1)
    val launcher = GoalFixtureAgentRunLauncher(fixture)

    val accepted = CliRuntime.run(
      listOf(
        "--db",
        fixture.dbPath.toString(),
        "goal",
        "reset",
        "SKILL-901",
        "--hard",
        "--yes",
        "--repo-root",
        fixture.tempDir.toString(),
      ),
      fixture.context(launcher = launcher),
    )

    assertEquals(0, accepted.exitCode, accepted.stdout)
    assertContains(accepted.stdout, "mode: hard")
  }

  @Test
  fun `goal reset requires both scoped child deletion selectors`() {
    val fixture = goalFixture(subtaskCount = 1)
    val launcher = GoalFixtureAgentRunLauncher(fixture)

    val missingDelete = CliRuntime.run(
      listOf("--db", fixture.dbPath.toString(), "goal", "reset", "SKILL-901", "--subtask", "1"),
      fixture.context(launcher = launcher),
    )
    val missingSubtask = CliRuntime.run(
      listOf("--db", fixture.dbPath.toString(), "goal", "reset", "SKILL-901", "--delete-child-workflow"),
      fixture.context(launcher = launcher),
    )

    assertEquals(1, missingDelete.exitCode, missingDelete.stdout)
    assertContains(missingDelete.stdout, "--subtask ID and --delete-child-workflow")
    assertEquals(1, missingSubtask.exitCode, missingSubtask.stdout)
    assertContains(missingSubtask.stdout, "--subtask ID and --delete-child-workflow")
  }

  @Test
  fun `hard reset output documents the branch action taken`() {
    val snapshot = GoalRunnerResetSnapshot(
      status = "pending",
      currentSubtaskId = 1,
      currentAction = "start",
      subtasks = emptyList(),
    )
    val payload = GoalRunnerResetResult(
      issueKey = "SKILL-346",
      mode = "hard",
      parentWorkflowId = "wfl-parent",
      before = snapshot,
      after = snapshot,
      branchActionTaken = "reset_feature_branch_tip_to_parent",
    ).toGoalResetCliMap("SKILL-346", hard = true)

    assertEquals("ok", payload[SharedPayloadKeys.STATUS])
    assertEquals(0, payload.goalResetExitCode())
    assertContains(
      goalResetText(payload),
      "${GoalRunnerResetPayloadKeys.BRANCH_ACTION_TAKEN}: reset_feature_branch_tip_to_parent",
    )
  }

  @Test
  fun `goal reset rejects scoped child deletion with hard reset`() {
    val fixture = goalFixture(subtaskCount = 1)
    val launcher = GoalFixtureAgentRunLauncher(fixture)

    val rejected = CliRuntime.run(
      listOf(
        "--db",
        fixture.dbPath.toString(),
        "goal",
        "reset",
        "SKILL-901",
        "--subtask",
        "1",
        "--delete-child-workflow",
        "--hard",
        "--force",
      ),
      fixture.context(launcher = launcher),
    )

    assertEquals(1, rejected.exitCode, rejected.stdout)
    assertContains(rejected.stdout, "incompatible with --hard")
  }
}
