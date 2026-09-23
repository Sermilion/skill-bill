package skillbill.workflow.decomposition

import skillbill.workflow.decomposition.model.CurrentSubtaskIntent
import skillbill.workflow.decomposition.model.DecompositionManifest
import skillbill.workflow.decomposition.model.DecompositionSubtask
import skillbill.workflow.model.DecompositionStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class DecompositionManifestTransitionsTest {
  @Test
  fun `parent status rolls up blocked over complete and complete only when every subtask is terminal`() {
    val complete = DecompositionStatus.COMPLETE.wireValue
    val skipped = DecompositionStatus.SKIPPED.wireValue
    val blocked = DecompositionStatus.BLOCKED.wireValue
    val pending = DecompositionStatus.PENDING.wireValue

    assertEquals(complete, manifest(subtask(1, complete), subtask(2, skipped)).withParentStatus().status)
    assertEquals(blocked, manifest(subtask(1, complete), subtask(2, blocked)).withParentStatus().status)
    assertEquals(
      DecompositionStatus.IN_PROGRESS.wireValue,
      manifest(subtask(1, complete), subtask(2, pending)).withParentStatus().status,
    )
    assertEquals(pending, manifest(subtask(1, pending), subtask(2, pending)).withParentStatus().status)
  }

  @Test
  fun `retrying a blocked subtask clears its reason and leaves siblings untouched`() {
    val blockedManifest =
      manifest(subtask(1, DecompositionStatus.COMPLETE.wireValue), subtask(2, DecompositionStatus.PENDING.wireValue))
        .withBlockedSubtask(subtaskId = 2, reason = " ", lastResumableStep = "review")

    val blockedSubtask = blockedManifest.subtasks.single { it.id == 2 }
    assertEquals(DecompositionStatus.BLOCKED.wireValue, blockedManifest.status)
    assertEquals(DecompositionStatus.BLOCKED.wireValue, blockedSubtask.status)
    assertEquals("Subtask 2 is blocked.", blockedSubtask.blockedReason)

    val retried = blockedManifest.withRetriedSubtask(subtaskId = 2, workflowId = "wfl-2", lastResumableStep = "review")

    val retriedSubtask = retried.subtasks.single { it.id == 2 }
    assertEquals(DecompositionStatus.IN_PROGRESS.wireValue, retried.status)
    assertEquals(CurrentSubtaskIntent(subtaskId = 2, action = "resume"), retried.currentSubtaskIntent)
    assertEquals(DecompositionStatus.IN_PROGRESS.wireValue, retriedSubtask.status)
    assertEquals("wfl-2", retriedSubtask.workflowId)
    assertNull(retriedSubtask.blockedReason)
    assertEquals(blockedManifest.subtasks.single { it.id == 1 }, retried.subtasks.single { it.id == 1 })
  }

  @Test
  fun `retrying an unknown subtask fails loudly`() {
    val error =
      assertFailsWith<IllegalArgumentException> {
        manifest(subtask(1, DecompositionStatus.BLOCKED.wireValue))
          .withRetriedSubtask(subtaskId = 9, workflowId = "wfl-9", lastResumableStep = "implement")
      }
    assertEquals("Cannot retry unknown decomposition subtask '9'.", error.message)
  }

  private fun manifest(vararg subtasks: DecompositionSubtask): DecompositionManifest =
    DecompositionManifest(
      issueKey = "SKILL-370",
      featureName = "transitions",
      parentSpecPath = ".feature-specs/SKILL-370-transitions/spec.md",
      baseBranch = "main",
      featureBranch = "feat/SKILL-370-transitions",
      currentSubtaskIntent = CurrentSubtaskIntent(subtaskId = 1, action = "start"),
      subtasks = subtasks.toList(),
    )

  private fun subtask(
    id: Int,
    status: String,
  ): DecompositionSubtask =
    DecompositionSubtask(
      id = id,
      name = "subtask-$id",
      specPath = ".feature-specs/SKILL-370-transitions/spec_subtask_$id.md",
      status = status,
    )
}
