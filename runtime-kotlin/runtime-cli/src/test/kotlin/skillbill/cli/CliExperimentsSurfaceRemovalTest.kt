package skillbill.cli

import skillbill.cli.core.CliRuntime
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class CliExperimentsSurfaceRemovalTest {
  @Test
  fun `goal run and preflight reject --experiments as an unknown option`() {
    val fixture = goalFixture(subtaskCount = 1)
    val launcher = GoalFixtureAgentRunLauncher(fixture)

    listOf(
      listOf("--db", fixture.dbPath.toString(), "goal", "SKILL-901", "--experiments", "pair-a"),
      listOf("--db", fixture.dbPath.toString(), "goal", "preflight", "SKILL-901", "--experiments", "pair-a"),
    ).forEach { argv ->
      val rejected = CliRuntime.run(argv, fixture.context(launcher = launcher))

      assertEquals(1, rejected.exitCode, rejected.stdout)
      assertContains(rejected.stdout + rejected.stderr, "--experiments")
    }
  }

  @Test
  fun `the experiments command group is not registered`() {
    val fixture = goalFixture(subtaskCount = 1)
    val launcher = GoalFixtureAgentRunLauncher(fixture)

    val rejected = CliRuntime.run(listOf("experiments", "list"), fixture.context(launcher = launcher))

    assertEquals(1, rejected.exitCode, rejected.stdout)

    val help = CliRuntime.run(listOf("--help"), fixture.context(launcher = launcher))
    assertEquals(0, help.exitCode, help.stdout)
    assertFalse(help.stdout.contains("experiments"), help.stdout)
  }
}
