package skillbill.cli.goal

import skillbill.contracts.workflow.WorktreeEditJournalPayloadKeys
import skillbill.goalrunner.model.GoalRunnerStatusProjection
import skillbill.idestatus.model.WorktreeEditSummary
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GoalCliStatusWorktreeEditsTest {
  @Test
  fun `goal status and monitor render measured worktree edits and omit absent measurements`() {
    val measured = GoalRunnerStatusProjection(
      issueKey = "SKILL-355",
      completeCount = 0,
      pendingCount = 1,
      blockedCount = 0,
      currentSubtaskId = 1,
      currentStep = "implement",
      activeAgent = "claude",
      latestWorktreeEdit = WorktreeEditSummary(
        recordedAt = Instant.parse("2026-09-18T12:00:00Z"),
        phaseId = "implement",
        pathSample = listOf("a.kt", "b.kt", "c.kt"),
        netInsertions = 4,
        netDeletions = 1,
      ),
      auditAcRetryCount = 1,
    )

    val full = measured.toGoalStatusCliMap("SKILL-355")
    val worktreeEdits = full[WorktreeEditJournalPayloadKeys.WORKTREE_EDITS] as Map<*, *>
    assertEquals("2026-09-18T12:00:00Z", worktreeEdits[WorktreeEditJournalPayloadKeys.RECORDED_AT])
    assertEquals(listOf("a.kt", "b.kt", "c.kt"), worktreeEdits[WorktreeEditJournalPayloadKeys.PATH_SAMPLE])
    assertEquals(1, full[WorktreeEditJournalPayloadKeys.AUDIT_AC_RETRY_COUNT])

    val bounded = measured.toBoundedGoalStatusCliMap("SKILL-355")
    val boundedEdits = bounded[WorktreeEditJournalPayloadKeys.WORKTREE_EDITS] as Map<*, *>
    assertEquals("a.kt,b.kt,c.kt", boundedEdits[WorktreeEditJournalPayloadKeys.PATH_SAMPLE])
    assertEquals(1, bounded[WorktreeEditJournalPayloadKeys.AUDIT_AC_RETRY_COUNT])

    val statusText = goalStatusText(full)
    assertTrue(
      statusText.contains("worktree_edits: at=2026-09-18T12:00:00Z phase=implement +4 -1 paths=a.kt,b.kt,c.kt"),
    )
    assertTrue(statusText.contains("audit_ac_retry_count: 1"))

    val monitorText = goalMonitorStatusText(bounded)
    assertTrue(
      monitorText.contains("worktree_edits: at=2026-09-18T12:00:00Z phase=implement +4 -1 paths=a.kt,b.kt,c.kt"),
    )
    assertTrue(monitorText.contains("audit_ac_retry_count: 1"))
    assertFalse(monitorText.contains("\npaths="))

    val absent = GoalRunnerStatusProjection(
      issueKey = "SKILL-355",
      completeCount = 0,
      pendingCount = 1,
      blockedCount = 0,
      currentSubtaskId = 1,
      currentStep = "implement",
      activeAgent = "claude",
    )
    val absentFull = absent.toGoalStatusCliMap("SKILL-355")
    val absentBounded = absent.toBoundedGoalStatusCliMap("SKILL-355")
    assertNull(absentFull[WorktreeEditJournalPayloadKeys.WORKTREE_EDITS])
    assertNull(absentFull[WorktreeEditJournalPayloadKeys.AUDIT_AC_RETRY_COUNT])
    assertNull(absentBounded[WorktreeEditJournalPayloadKeys.WORKTREE_EDITS])
    assertNull(absentBounded[WorktreeEditJournalPayloadKeys.AUDIT_AC_RETRY_COUNT])
    assertFalse(goalStatusText(absentFull).contains("worktree_edits"))
    assertFalse(goalStatusText(absentFull).contains("audit_ac_retry_count"))
    assertFalse(goalMonitorStatusText(absentBounded).contains("worktree_edits"))
    assertFalse(goalMonitorStatusText(absentBounded).contains("audit_ac_retry_count"))
  }
}
