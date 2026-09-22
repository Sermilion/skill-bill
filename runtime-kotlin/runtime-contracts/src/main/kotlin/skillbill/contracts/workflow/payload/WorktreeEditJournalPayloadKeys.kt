package skillbill.contracts.workflow.payload

object WorktreeEditJournalPayloadKeys {
  const val WORKTREE_EDITS: String = "worktree_edits"
  const val RECORDED_AT: String = "recorded_at"
  const val PHASE_ID: String = "phase_id"
  const val PATH_SAMPLE: String = "path_sample"
  const val NET_INSERTIONS: String = "net_insertions"
  const val NET_DELETIONS: String = "net_deletions"
  const val SOURCE: String = "source"
  const val AUDIT_AC_RETRY_COUNT: String = "audit_ac_retry_count"
  const val MAX_ROWS_PER_WORKFLOW: Int = 2000
  const val PATH_SAMPLE_LIMIT: Int = 5
}
