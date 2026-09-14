package skillbill.contracts.decomposition

object DecompositionManifestPayloadKeys {
  const val FEATURE_NAME: String = "feature_name"
  const val FEATURE_BRANCH: String = "feature_branch"
  const val CURRENT_SUBTASK_INTENT: String = "current_subtask_intent"
  const val COMMIT_SHA: String = "commit_sha"
  const val BLOCKED_REASON: String = "blocked_reason"
  const val LAST_RESUMABLE_STEP: String = "last_resumable_step"
  const val FINALIZING_AGENT_ID: String = "finalizing_agent_id"
  const val PARTICIPATING_AGENT_IDS: String = "participating_agent_ids"
  const val ACTION: String = "action"
}
