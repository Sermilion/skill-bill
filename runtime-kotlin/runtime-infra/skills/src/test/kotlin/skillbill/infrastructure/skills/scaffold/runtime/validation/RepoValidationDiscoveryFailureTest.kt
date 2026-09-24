package skillbill.infrastructure.skills.scaffold.runtime.validation

import skillbill.infrastructure.skills.nativeagent.testNativeAgentCompositionContext
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertTrue

class RepoValidationDiscoveryFailureTest {
  @Test
  fun `a malformed skill class manifest is named instead of emptying the class list`() {
    val repoRoot = fixtureRepo("skill-class")
    val classesDir = repoRoot.resolve("orchestration/skill-classes")
    Files.createDirectories(classesDir)
    Files.writeString(classesDir.resolve("broken.yaml"), "matchers: [unclosed\n")

    assertIssue(repoRoot, prefix = "orchestration/skill-classes:", names = "broken.yaml")
  }

  @Test
  fun `a malformed native agent source is named instead of emptying the agent list`() {
    val repoRoot = fixtureRepo("native-agent")
    val agentsDir = repoRoot.resolve("skills/bill-fixture/native-agents")
    Files.createDirectories(agentsDir)
    Files.writeString(agentsDir.resolve("broken-agent.md"), "no frontmatter here\n")

    assertIssue(repoRoot, prefix = "native agent sources:", names = "broken-agent.md")
  }

  @Test
  fun `a malformed platform manifest is named instead of emptying the portable review skills`() {
    val repoRoot = fixtureRepo("platform-manifest")
    val packRoot = repoRoot.resolve("platform-packs/bill-broken")
    Files.createDirectories(packRoot)
    Files.writeString(packRoot.resolve("platform.yaml"), "code_review: [unclosed\n")

    assertIssue(repoRoot, prefix = "platform-packs/bill-broken/platform.yaml:", names = "platform.yaml")
  }

  private fun assertIssue(
    repoRoot: Path,
    prefix: String,
    names: String,
  ) {
    val report = RepoValidationRuntime.validateRepo(repoRoot, testNativeAgentCompositionContext(repoRoot))

    assertTrue(
      report.issues.any { issue -> issue.startsWith(prefix) && issue.contains(names) },
      "a failed discovery must surface as an issue naming the broken file; issues=${report.issues}",
    )
  }

  private fun fixtureRepo(label: String): Path {
    val repoRoot = Files.createTempDirectory("skillbill-discovery-failure-$label")
    Files.createDirectories(repoRoot.resolve("skills/bill-fixture"))
    Files.writeString(
      repoRoot.resolve("skills/bill-fixture/content.md"),
      """
      ---
      name: bill-fixture
      description: Fixture skill.
      ---

      Authored body.
      """.trimIndent(),
    )
    return repoRoot
  }
}
