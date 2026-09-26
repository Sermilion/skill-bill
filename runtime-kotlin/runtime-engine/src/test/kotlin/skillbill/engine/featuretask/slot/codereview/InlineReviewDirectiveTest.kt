package skillbill.engine.featuretask.slot.codereview

import skillbill.agentaddon.model.AgentAddonPromptFormatter
import skillbill.agentaddon.model.HydratedAgentAddonSelection
import skillbill.agentaddon.model.HydratedAgentAddonSelectionEntry
import skillbill.agentaddon.model.PersistedAgentAddonSelectionEntry
import skillbill.engine.featuretask.slot.ReviewTarget
import skillbill.engine.featuretask.slotbaseline.SlotBaselineFullRunCapture
import skillbill.engine.featuretask.slotbaseline.SlotBaselinePaths
import skillbill.engine.featuretask.slotbaseline.SlotBaselineTestResources
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private const val NORMALIZED_SHA = "__NORMALIZED_SHA40__"

class InlineReviewDirectiveTest {
  @Test
  fun `last-commit prompt is byte-identical to the pre-change fixture`() {
    val fixture =
      Files.readString(SlotBaselineTestResources.resolve("${SlotBaselinePaths.STANDALONE}/prompts/review.txt"))

    assertEquals(fixture, compose(ReviewTarget.LastCommit))
  }

  @Test
  fun `uncommitted target reviews the workspace against HEAD`() {
    val prompt = compose(ReviewTarget.Uncommitted)

    assertTrue(prompt.startsWith("Review the uncommitted changes in this repository workspace against `HEAD`"))
    assertTrue(prompt.contains("`git diff HEAD`"))
    assertTrue(NORMALIZED_SHA !in prompt)
    assertFalse(prompt.contains("last commit", ignoreCase = true))
  }

  @Test
  fun `named commit target reviews that commit against its first parent`() {
    val prompt = compose(ReviewTarget.Commit("c0ffee"))

    assertTrue(prompt.startsWith("Review commit `c0ffee` against its first parent `c0ffee^`.\n"))
    assertTrue(prompt.contains("`git diff c0ffee^ c0ffee`"))
    assertTrue(NORMALIZED_SHA !in prompt)
    assertFalse(prompt.contains("last commit", ignoreCase = true))
  }

  @Test
  fun `selected add-ons are appended after the review instructions`() {
    val selection =
      HydratedAgentAddonSelection(
        listOf(
          HydratedAgentAddonSelectionEntry(
            PersistedAgentAddonSelectionEntry("first", "local:first", "a".repeat(64)),
            "first",
            "first body\n",
          ),
        ),
      )
    val section = AgentAddonPromptFormatter.format(selection)

    val prompt = compose(ReviewTarget.LastCommit, section)

    assertEquals(compose(ReviewTarget.LastCommit).trimEnd() + "\n\n" + section, prompt)
  }

  private fun compose(
    target: ReviewTarget,
    agentAddonsSection: String = "",
  ): String =
    InlineReviewDirective.compose(
      target = target,
      baseRevision = NORMALIZED_SHA,
      headRevision = NORMALIZED_SHA,
      specPath = Path.of(SlotBaselineFullRunCapture.SPEC_REFERENCE),
      agentAddonsSection = agentAddonsSection,
    )
}
