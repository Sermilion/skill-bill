package skillbill.workflow.model.goalreview

import skillbill.contracts.JsonCodec
import skillbill.contracts.scaffold.wire.optionalList
import skillbill.contracts.scaffold.wire.optionalString
import skillbill.contracts.workflow.goal.GOAL_OBSERVABILITY_EVENT_CONTRACT_VERSION
import skillbill.workflow.model.persistence.artifact.DurableArtifactMapReader
import skillbill.workflow.model.persistence.artifact.toStringKeyedArtifactMap
import skillbill.workflow.time.parsePersistedInstant

fun goalObservabilityLatestEventFromArtifacts(artifacts: Any): GoalObservabilityEvent? {
  val artifactMap = artifacts.asGoalWorkflowArtifactMap("goal observability artifacts")
  return artifactMap[GOAL_OBSERVABILITY_LATEST_EVENT_ARTIFACT_KEY]
    ?.let { raw -> goalObservabilityEventFromArtifact(raw, GOAL_OBSERVABILITY_LATEST_EVENT_ARTIFACT_KEY) }
}

internal fun goalObservabilityHistoryFromArtifacts(artifacts: Any): GoalObservabilityHistory {
  val artifactMap = artifacts.asGoalWorkflowArtifactMap("goal observability history artifacts")
  val rawHistory = artifactMap[GOAL_OBSERVABILITY_RUN_HISTORY_ARTIFACT_KEY] ?: return GoalObservabilityHistory()
  val rawEvents =
    rawHistory as? List<*>
      ?: throw invalidGoalObservabilityEvent(
        GOAL_OBSERVABILITY_RUN_HISTORY_ARTIFACT_KEY,
        "",
        "run history must be an array of goal-observability events.",
      )
  return GoalObservabilityHistory(
    events =
      rawEvents.mapIndexed { index, raw ->
        goalObservabilityEventFromArtifact(raw, "$GOAL_OBSERVABILITY_RUN_HISTORY_ARTIFACT_KEY[$index]")
      },
  )
}

internal fun goalObservabilityEventFromArtifact(
  raw: Any?,
  sourceLabel: String,
): GoalObservabilityEvent {
  val eventMap = raw.toGoalObservabilityEventMap(sourceLabel)
  eventMap.requireOnlyKeys(GOAL_OBSERVABILITY_EVENT_KEYS, sourceLabel)
  val reader = goalObservabilityReader(eventMap, sourceLabel)
  return GoalObservabilityEvent(
    contractVersion = requireGoalObservabilityContractVersion(reader, sourceLabel),
    recordKind =
      reader.optionalString("record_kind")?.let(GoalObservabilityRecordKind::fromWire)
        ?: GoalObservabilityRecordKind.PROGRESS,
    issueKey = reader.requiredString("issue_key"),
    subtaskId = reader.requiredInt("subtask_id").requirePositiveObservationInt(sourceLabel, "subtask_id"),
    workflowId = reader.optionalString("workflow_id"),
    workflowPhase = reader.requiredString("workflow_phase"),
    workerRole = reader.requiredString("worker_role"),
    livenessClass = reader.requiredString("liveness_class"),
    activitySummary = reader.requiredString("activity_summary"),
    timestamp = parseObservationTimestamp(reader.requiredString("timestamp"), sourceLabel),
    sequenceNumber = reader.requiredInt("sequence_number").requireNonNegativeObservationInt(sourceLabel),
    changedFileSummary =
      reader.optionalNestedObject("changed_file_summary")
        ?.toChangedFileSummary(sourceLabel),
    diffStat = reader.optionalNestedObject("diff_stat")?.toDiffStat(sourceLabel),
    changedFiles = reader.optionalStringList("changed_files"),
    diffStatByFile = reader.optionalList("diff_stat_by_file").toFileDiffStats(sourceLabel),
  )
}

internal fun Any.asGoalWorkflowArtifactMap(sourceLabel: String): Map<String, Any?> =
  JsonCodec.anyToStringAnyMap(this)
    ?: throw invalidGoalObservabilityEvent(sourceLabel, "", "artifacts must decode to an object.")

private fun Int.requirePositiveObservationInt(
  sourceLabel: String,
  field: String,
): Int =
  takeIf { it > 0 }
    ?: throw invalidGoalObservabilityEvent(sourceLabel, field, "field must be a positive integer.")

private fun Int.requireNonNegativeObservationInt(sourceLabel: String): Int =
  takeIf { it >= 0 }
    ?: throw invalidGoalObservabilityEvent(sourceLabel, "sequence_number", "field must be non-negative.")

private fun parseObservationTimestamp(
  value: String,
  sourceLabel: String,
) = try {
  parsePersistedInstant(value)
} catch (error: IllegalArgumentException) {
  throw invalidGoalObservabilityEvent(sourceLabel, "timestamp", "field must be a persisted instant.", error)
}

private fun Any?.toGoalObservabilityEventMap(sourceLabel: String): Map<String, Any?> =
  JsonCodec.anyToStringAnyMap(this)
    ?: (this as? Map<*, *>)?.let { map ->
      map.entries.associate { (key, value) ->
        val stringKey =
          key as? String
            ?: throw invalidGoalObservabilityEvent(sourceLabel, "", "event keys must be strings.")
        stringKey to value
      }
    }
    ?: throw invalidGoalObservabilityEvent(sourceLabel, "", "event must be a JSON object.")

private fun List<*>?.toFileDiffStats(sourceLabel: String): List<GoalObservabilityFileDiffStat> =
  this?.mapIndexed { index, rawFile ->
    rawFile.asRequiredMap("$sourceLabel.diff_stat_by_file[$index]")
      .toFileDiffStat("$sourceLabel.diff_stat_by_file[$index]")
  }.orEmpty()

private fun Map<*, *>.toChangedFileSummary(sourceLabel: String): GoalObservabilityChangedFileSummary =
  requireOnlyKeys(GOAL_OBSERVABILITY_CHANGED_FILE_SUMMARY_KEYS, "$sourceLabel.changed_file_summary").let {
    val reader = goalObservabilityReader(this, "$sourceLabel.changed_file_summary")
    GoalObservabilityChangedFileSummary(
      total = reader.requiredInt("total").requireNonNegative(sourceLabel, "total"),
      added = reader.requiredInt("added").requireNonNegative(sourceLabel, "added"),
      modified = reader.requiredInt("modified").requireNonNegative(sourceLabel, "modified"),
      deleted = reader.requiredInt("deleted").requireNonNegative(sourceLabel, "deleted"),
      renamed = reader.requiredInt("renamed").requireNonNegative(sourceLabel, "renamed"),
      untracked = reader.requiredInt("untracked").requireNonNegative(sourceLabel, "untracked"),
      samplePaths = reader.optionalStringList("sample_paths"),
    )
  }

private fun Map<*, *>.toDiffStat(sourceLabel: String): GoalObservabilityDiffStat =
  requireOnlyKeys(GOAL_OBSERVABILITY_DIFF_STAT_KEYS, "$sourceLabel.diff_stat").let {
    val reader = goalObservabilityReader(this, "$sourceLabel.diff_stat")
    GoalObservabilityDiffStat(
      filesChanged = reader.requiredInt("files_changed").requireNonNegative(sourceLabel, "files_changed"),
      insertions = reader.requiredInt("insertions").requireNonNegative(sourceLabel, "insertions"),
      deletions = reader.requiredInt("deletions").requireNonNegative(sourceLabel, "deletions"),
    )
  }

private fun Map<*, *>.toFileDiffStat(sourceLabel: String): GoalObservabilityFileDiffStat =
  requireOnlyKeys(GOAL_OBSERVABILITY_FILE_DIFF_STAT_KEYS, sourceLabel).let {
    val reader = goalObservabilityReader(this, sourceLabel)
    GoalObservabilityFileDiffStat(
      path = reader.requiredString("path"),
      insertions = reader.requiredInt("insertions").requireNonNegative(sourceLabel, "insertions"),
      deletions = reader.requiredInt("deletions").requireNonNegative(sourceLabel, "deletions"),
    )
  }

private fun Int.requireNonNegative(
  sourceLabel: String,
  field: String,
): Int =
  takeIf { it >= 0 }
    ?: throw invalidGoalObservabilityEvent(sourceLabel, field, "field must be a non-negative integer.")

private val GOAL_OBSERVABILITY_EVENT_KEYS =
  setOf(
    "contract_version",
    "record_kind",
    "issue_key",
    "subtask_id",
    "workflow_id",
    "workflow_phase",
    "worker_role",
    "liveness_class",
    "activity_summary",
    "timestamp",
    "sequence_number",
    "changed_file_summary",
    "diff_stat",
    "changed_files",
    "diff_stat_by_file",
  )

private val GOAL_OBSERVABILITY_CHANGED_FILE_SUMMARY_KEYS =
  setOf(
    "total",
    "added",
    "modified",
    "deleted",
    "renamed",
    "untracked",
    "sample_paths",
  )

private val GOAL_OBSERVABILITY_DIFF_STAT_KEYS = setOf("files_changed", "insertions", "deletions")

private val GOAL_OBSERVABILITY_FILE_DIFF_STAT_KEYS = setOf("path", "insertions", "deletions")

internal fun Map<*, *>.requireOnlyKeys(
  allowedKeys: Set<String>,
  sourceLabel: String,
) {
  keys.forEach { key ->
    val stringKey =
      key as? String
        ?: throw invalidGoalObservabilityEvent(sourceLabel, "", "event keys must be strings.")
    if (stringKey !in allowedKeys) {
      throw invalidGoalObservabilityEvent(sourceLabel, stringKey, "unknown field is not allowed.")
    }
  }
}

internal fun Any?.asRequiredMap(sourceLabel: String): Map<*, *> =
  this as? Map<*, *>
    ?: throw invalidGoalObservabilityEvent(sourceLabel, "", "field must be an object.")

internal fun goalObservabilityReader(
  map: Map<*, *>,
  sourceLabel: String,
): DurableArtifactMapReader {
  val converted =
    map.toStringKeyedArtifactMap { detail ->
      throw invalidGoalObservabilityEvent(sourceLabel, "", detail)
    }
  return DurableArtifactMapReader(converted) { detail ->
    throw invalidGoalObservabilityEvent(sourceLabel, detail, "malformed durable field.")
  }
}

internal fun requireGoalObservabilityContractVersion(
  reader: DurableArtifactMapReader,
  sourceLabel: String,
): String =
  reader.requiredString("contract_version").also { value ->
    if (value != GOAL_OBSERVABILITY_EVENT_CONTRACT_VERSION) {
      throw invalidGoalObservabilityEvent(
        sourceLabel,
        "contract_version",
        "field must equal $GOAL_OBSERVABILITY_EVENT_CONTRACT_VERSION.",
      )
    }
  }
