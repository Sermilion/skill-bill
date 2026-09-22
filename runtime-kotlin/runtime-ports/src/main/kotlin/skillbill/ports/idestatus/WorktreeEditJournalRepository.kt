package skillbill.ports.idestatus

import skillbill.idestatus.model.WorktreeEditTick

interface WorktreeEditJournalRepository {
  fun append(
    workflowId: String,
    tick: WorktreeEditTick,
  )

  fun latestTick(workflowId: String): WorktreeEditTick?

  /** Drops whole oldest ticks by recorded_at until rows <= [maxRows]; returns deleted row count. */
  fun trimToCap(
    workflowId: String,
    maxRows: Int,
  ): Int
}
