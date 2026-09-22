package skillbill.ports.idestatus

import skillbill.idestatus.model.WorktreeEditTick

object EmptyWorktreeEditJournalRepository : WorktreeEditJournalRepository {
  override fun append(
    workflowId: String,
    tick: WorktreeEditTick,
  ) = Unit

  override fun latestTick(workflowId: String): WorktreeEditTick? = null

  override fun trimToCap(
    workflowId: String,
    maxRows: Int,
  ): Int = 0
}
