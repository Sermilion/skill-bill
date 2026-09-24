package skillbill.infrastructure.sqlite.telemetry

object SqliteReviewTelemetryPayloadKeys {
  const val LATEST_OUTCOME_COUNTS: String = "latest_outcome_counts"
  const val FROM_VERSION: String = "from_version"
  const val TO_VERSION: String = "to_version"
  const val MALFORMED: String = "malformed"
  const val AGENT_IDS: String = "agent_ids"
  const val COMMIT_SHAS: String = "commit_shas"
  const val LINE: String = "line"
  const val ORIGIN_LAYER_CHAINS: String = "origin_layer_chains"
  const val SPECIALIST_SKILL_NAMES: String = "specialist_skill_names"
  const val UNREVIEWED_SEGMENT_IDS: String = "unreviewed_segment_ids"
  const val APPLIED_LEARNING_COUNT: String = "applied_learning_count"
  const val APPLIED_LEARNING_REFERENCES: String = "applied_learning_references"
  const val APPLIED_LEARNINGS: String = "applied_learnings"
  const val COMPLETED_PHASE_IDS: String = "completed_phase_ids"
}
