package skillbill.engine.featuretask.review.goal

import skillbill.contracts.JsonCodec
import skillbill.ports.workflow.gitops.model.GoalSubtaskReviewInput
import kotlin.test.Test
import kotlin.test.assertEquals

class GoalSubtaskReviewInputWireTest {
  @Test
  fun `goal review input artifact keeps the pre-change JSON shape and order`() {
    val input =
      GoalSubtaskReviewInput(
        reviewBaseSha = "a".repeat(40),
        currentHeadSha = "b".repeat(40),
        trackedDelta = "tracked delta",
        ownedUntrackedPatches = "owned patch",
      )

    assertEquals(
      """{"review_base_sha":"${"a".repeat(40)}","current_head_sha":"${"b".repeat(40)}",""" +
        """"tracked_delta":"tracked delta","owned_untracked_patches":"owned patch"}""",
      JsonCodec.mapToJsonString(goalReviewInputArtifactMap(input)),
    )
  }
}
