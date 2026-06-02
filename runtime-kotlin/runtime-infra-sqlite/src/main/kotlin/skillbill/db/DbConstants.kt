package skillbill.db

import java.nio.file.Path

object DbConstants {
  const val DB_ENVIRONMENT_KEY: String = "SKILL_BILL_REVIEW_DB"
  const val FEATURE_IMPLEMENT_WORKFLOW_CONTRACT_VERSION: String = "0.1"
  const val FEATURE_VERIFY_WORKFLOW_CONTRACT_VERSION: String = "0.1"

  // SKILL-65 Subtask 2: internal table contract version for the experimental
  // feature-task-runtime workflow rows. This is the table's own contract
  // version, NOT a new external YAML schema; the existing workflow-state schema
  // continues to govern the artifacts_json envelope.
  const val FEATURE_TASK_RUNTIME_WORKFLOW_CONTRACT_VERSION: String = "0.1"

  val findingOutcomeTypes: Set<String> =
    setOf(
      "finding_accepted",
      "fix_applied",
      "finding_edited",
      "fix_rejected",
      "false_positive",
    )

  fun defaultDbPath(userHome: Path): Path = userHome.resolve(".skill-bill").resolve("review-metrics.db")
}
