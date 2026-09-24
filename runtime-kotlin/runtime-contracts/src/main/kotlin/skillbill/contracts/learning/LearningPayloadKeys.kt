package skillbill.contracts.learning

import skillbill.contracts.SharedPayloadKeys

object LearningPayloadKeys {
  const val REFERENCE: String = "reference"
  const val SCOPE: String = "scope"
  const val SCOPE_KEY: String = "scope_key"
  const val STATUS: String = SharedPayloadKeys.STATUS
  const val TITLE: String = "title"
  const val RULE_TEXT: String = "rule_text"
  const val RATIONALE: String = "rationale"
  const val SOURCE_REVIEW_RUN_ID: String = "source_review_run_id"
  const val SOURCE_FINDING_ID: String = "source_finding_id"
  const val DB_PATH: String = "db_path"
  const val LEARNINGS: String = "learnings"
  const val REPO_SCOPE_KEY: String = "repo_scope_key"
  const val SKILL_NAME: String = "skill_name"
  const val SCOPE_PRECEDENCE: String = "scope_precedence"
  const val APPLIED_LEARNINGS: String = "applied_learnings"
  const val REVIEW_SESSION_ID: String = "review_session_id"
  const val DELETED_LEARNING_ID: String = "deleted_learning_id"
  const val APPLIED_LEARNING_COUNT: String = "applied_learning_count"
  const val APPLIED_LEARNING_REFERENCES: String = "applied_learning_references"
  const val SCOPE_COUNTS: String = "scope_counts"
  const val LEARNING_CANDIDATES: String = "learning_candidates"
  const val SUGGESTED_TITLE: String = "suggested_title"
  const val SUGGESTED_RULE_TEXT: String = "suggested_rule_text"
  const val SUGGESTED_SCOPE: String = "suggested_scope"
  const val SUGGESTED_SCOPE_KEY: String = "suggested_scope_key"
}
