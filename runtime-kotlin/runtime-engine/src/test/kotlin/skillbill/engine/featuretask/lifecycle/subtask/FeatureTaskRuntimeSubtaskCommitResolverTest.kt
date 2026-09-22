package skillbill.engine.featuretask.lifecycle.subtask
import skillbill.engine.featuretask.model.subtask.FeatureTaskRuntimeSubtaskCommitIdentity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

private const val ISSUE = "SKILL-190"
private const val HEAD_SHA = "1111111111111111111111111111111111111111"
private const val OTHER_SHA = "2222222222222222222222222222222222222222"

class FeatureTaskRuntimeSubtaskCommitResolverTest {
  private val identity = FeatureTaskRuntimeSubtaskCommitIdentity(issueKey = ISSUE, subtaskId = "3")

  @Test
  fun `amend is keyed on this subtask's own unpushed commit and nothing else`() {
    assertIs<FeatureTaskRuntimeSubtaskCommitAmend>(
      decide(durableCommitSha = HEAD_SHA, headCommitMessage = null),
    )

    assertIs<FeatureTaskRuntimeSubtaskCommitCreate>(
      decide(durableCommitSha = null, headCommitMessage = "done\n\nSkill-Bill-Subtask: $ISSUE/2\n"),
    )
    assertIs<FeatureTaskRuntimeSubtaskCommitCreate>(
      decide(durableCommitSha = OTHER_SHA, headCommitMessage = null),
    )
    assertIs<FeatureTaskRuntimeSubtaskCommitCreate>(
      decide(durableCommitSha = null, headCommitMessage = "a hand-written commit\n"),
    )
    assertIs<FeatureTaskRuntimeSubtaskCommitAmend>(
      decide(durableCommitSha = HEAD_SHA, headCommitMessage = null, headIsUnpushed = false),
    )
    assertIs<FeatureTaskRuntimeSubtaskCommitAmend>(
      decide(
        durableCommitSha = null,
        headCommitMessage = "wip\n\nSkill-Bill-Subtask: $ISSUE/3\n",
        headIsUnpushed = false,
      ),
    ).also { decision ->
      assertEquals(true, decision.recoveredFromTrailer)
    }
    assertIs<FeatureTaskRuntimeSubtaskCommitCreate>(
      decide(durableCommitSha = HEAD_SHA, headCommitMessage = null, headSha = null),
    )
  }

  @Test
  fun `an absent pointer recovers the amend target from the HEAD trailer and flags the fallback`() {
    val decision =
      assertIs<FeatureTaskRuntimeSubtaskCommitAmend>(
        decide(durableCommitSha = null, headCommitMessage = "wip\n\nSkill-Bill-Subtask: $ISSUE/3\n"),
      )

    assertEquals(HEAD_SHA, decision.ownedHeadSha)
    assertEquals(4, decision.sequenceNumber)
    assertEquals(true, decision.recoveredFromTrailer)
  }

  @Test
  fun `a stale pointer still amends a matching HEAD trailer`() {
    val decision =
      assertIs<FeatureTaskRuntimeSubtaskCommitAmend>(
        decide(
          durableCommitSha = OTHER_SHA,
          headCommitMessage = "wip\n\nSkill-Bill-Subtask: $ISSUE/3\n",
        ),
      )

    assertEquals(HEAD_SHA, decision.ownedHeadSha)
    assertEquals(true, decision.recoveredFromTrailer)
  }

  private fun decide(
    durableCommitSha: String?,
    headCommitMessage: String?,
    headIsUnpushed: Boolean = true,
    headSha: String? = HEAD_SHA,
  ) = FeatureTaskRuntimeSubtaskCommitResolver.decide(
    identity = identity,
    durableCommitSha = durableCommitSha,
    head =
      FeatureTaskRuntimeSubtaskCommitHeadState(
        sha = headSha,
        commitMessage = headCommitMessage,
        isUnpushed = headIsUnpushed,
      ),
    sequenceNumber = 4,
  )
}
