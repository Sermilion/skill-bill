package skillbill.workflow.goal.model

import skillbill.contracts.SharedPayloadKeys
import skillbill.contracts.review.ReviewFindingPayloadKeys
import skillbill.contracts.review.ReviewVerificationSignalKeys
import skillbill.error.InvalidGoalSubtaskReviewStateSchemaError
import skillbill.error.InvalidWorkflowStateSchemaError
import skillbill.review.context.model.CodeReviewExecutionMode
import skillbill.workflow.taskruntime.model.FEATURE_TASK_RUNTIME_GOAL_CONTINUATION_ARTIFACT_KEY
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeGoalContinuationArtifact
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeReviewSeverity
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeVerdict

val GOAL_SUBTASK_REVIEW_PASS_VERDICTS: Set<FeatureTaskRuntimeVerdict> = setOf(
  FeatureTaskRuntimeVerdict.APPROVED,
  FeatureTaskRuntimeVerdict.CHANGES_REQUESTED,
  FeatureTaskRuntimeVerdict.REVIEW_CAP_REACHED,
  FeatureTaskRuntimeVerdict.REVIEW_SKIPPED_BY_USER,
)

data class GoalSubtaskReviewCompactFinding(
  val severity: String,
  val label: String,
  val text: String,

  val findingId: String? = null,
) {
  val isBlocker: Boolean get() = severity == GOAL_SUBTASK_REVIEW_BLOCKER_SEVERITY

  val blocksAdvance: Boolean get() = severity == GOAL_SUBTASK_REVIEW_BLOCKER_SEVERITY || severity == "major"

  init {
    require(severity in FeatureTaskRuntimeReviewSeverity.entries.map { it.wireValue }) {
      "Invalid review finding severity '$severity'."
    }
    require(label.isNotBlank()) { "GoalSubtaskReviewCompactFinding.label must be non-blank." }
    require(text.isNotBlank()) { "GoalSubtaskReviewCompactFinding.text must be non-blank." }
    findingId?.let { require(it.isNotBlank()) { "GoalSubtaskReviewCompactFinding.findingId must be non-blank." } }
  }

  internal fun toArtifactMap(): Map<String, Any?> = linkedMapOf<String, Any?>(
    "severity" to severity,
    "label" to label,
    "text" to text,
  ).apply { findingId?.let { put(ReviewFindingPayloadKeys.FINDING_ID, it) } }

  companion object {
    internal fun fromArtifactMap(raw: Map<String, Any?>, path: String): GoalSubtaskReviewCompactFinding {
      raw.requireOnlyReviewStateKeys(setOf("severity", "label", "text", "finding_id"), path)
      val reader = reviewStateReader(raw, path)
      return GoalSubtaskReviewCompactFinding(
        severity = reader.requiredString("severity"),
        label = reader.requiredString("label"),
        text = reader.requiredString("text"),
        findingId = reader.optionalString("finding_id"),
      )
    }
  }
}

data class GoalSubtaskReviewPassResult(
  val passNumber: Int,
  val verdict: FeatureTaskRuntimeVerdict,
  val reviewResultArtifact: String,
  val unresolvedFindingCount: Int,
  val findings: List<GoalSubtaskReviewCompactFinding>,
  val executedMode: CodeReviewExecutionMode? = null,

  val commitFocusedAccounting: GoalSubtaskCommitFocusedAccounting? = null,
) {
  init {
    require(passNumber >= 1) { "Goal review pass number must be a positive integer." }
    require(verdict in GOAL_SUBTASK_REVIEW_PASS_VERDICTS) {
      "Goal review pass verdict is invalid: '${verdict.wireValue}'."
    }
    require(reviewResultArtifact == "$GOAL_SUBTASK_REVIEW_RESULT_ARTIFACT_PREFIX.$passNumber") {
      "Goal review result artifact must identify its exact review pass."
    }
    require(unresolvedFindingCount >= 0) { "Goal unresolved finding count must be non-negative." }
    require(commitFocusedAccounting == null || executedMode != CodeReviewExecutionMode.INLINE) {
      "An inline review pass has no delegated commit sequence and must omit commit-focused accounting."
    }
  }

  val blocksAdvance: Boolean get() = blocksAdvance(unresolvedFindingCount, findings)

  internal fun toArtifactMap(): Map<String, Any?> = linkedMapOf<String, Any?>(
    "pass_number" to passNumber,
    SharedPayloadKeys.VERDICT to verdict.wireValue,
    "review_result_artifact" to reviewResultArtifact,
    "unresolved_finding_count" to unresolvedFindingCount,
    ReviewVerificationSignalKeys.REVIEW_FINDINGS to findings.map(GoalSubtaskReviewCompactFinding::toArtifactMap),
  ).apply {
    executedMode?.let { put("executed_mode", it.wireValue) }
    commitFocusedAccounting?.let { put("commit_focused_accounting", it.toArtifactMap()) }
  }

  companion object {
    internal fun fromArtifactMap(raw: Map<String, Any?>, path: String): GoalSubtaskReviewPassResult {
      raw.requireOnlyReviewStateKeys(
        setOf(
          "pass_number",
          "verdict",
          "review_result_artifact",
          "unresolved_finding_count",
          "findings",
          "executed_mode",
          "commit_focused_accounting",
        ),
        path,
      )
      val reader = reviewStateReader(raw, path)
      val findings = reader.requiredList("findings").mapIndexed { index, value ->
        GoalSubtaskReviewCompactFinding.fromArtifactMap(
          value.toReviewStateMap("$path.findings[$index]"),
          "$path.findings[$index]",
        )
      }
      return GoalSubtaskReviewPassResult(
        passNumber = reader.requiredInt("pass_number"),
        verdict = FeatureTaskRuntimeVerdict.fromWire(reader.requiredString("verdict")),
        reviewResultArtifact = reader.requiredString("review_result_artifact"),
        unresolvedFindingCount = reader.requiredInt("unresolved_finding_count"),
        findings = findings,
        executedMode = reader.optionalString("executed_mode")?.let(CodeReviewExecutionMode::fromWire),
        commitFocusedAccounting = raw["commit_focused_accounting"]?.let {
          GoalSubtaskCommitFocusedAccounting.fromArtifactMap(
            it.toReviewStateMap("$path.commit_focused_accounting"),
            "$path.commit_focused_accounting",
          )
        },
      )
    }
  }
}

data class GoalSubtaskReviewArtifacts(
  val continuation: FeatureTaskRuntimeGoalContinuationArtifact,
  val state: GoalSubtaskReviewState,
  val rawResults: Map<String, String>,
)

object GoalSubtaskReviewArtifactDecoder {
  fun decode(artifacts: Any): GoalSubtaskReviewArtifacts? =
    decodeWire(artifacts.asGoalWorkflowArtifactMap("goal subtask review artifacts"))

  fun decodeContinuationOnly(artifacts: Any): FeatureTaskRuntimeGoalContinuationArtifact? =
    decodeContinuationOnlyWire(artifacts.asGoalWorkflowArtifactMap("goal subtask review continuation artifacts"))

  fun decodeReviewStateOnly(artifacts: Any): GoalSubtaskReviewState? =
    decodeReviewStateOnlyWire(artifacts.asGoalWorkflowArtifactMap("goal subtask review state artifacts"))

  internal fun decodeWire(artifacts: Map<String, Any?>): GoalSubtaskReviewArtifacts? {
    val hasContinuation = FEATURE_TASK_RUNTIME_GOAL_CONTINUATION_ARTIFACT_KEY in artifacts
    val hasState = GOAL_SUBTASK_REVIEW_STATE_ARTIFACT_KEY in artifacts
    if (!hasContinuation && !hasState) {
      if (GOAL_SUBTASK_REVIEW_RESULTS_ARTIFACT_KEY in artifacts) {
        reviewStateError(
          GOAL_SUBTASK_REVIEW_RESULTS_ARTIFACT_KEY,
          "must be absent when no goal-subtask review child state exists.",
        )
      }
      return null
    }
    if (!hasContinuation) {
      reviewStateError(
        FEATURE_TASK_RUNTIME_GOAL_CONTINUATION_ARTIFACT_KEY,
        "must be present whenever a goal-subtask review state exists.",
      )
    }
    if (!hasState) {
      reviewStateError(
        GOAL_SUBTASK_REVIEW_STATE_ARTIFACT_KEY,
        "must be present whenever a goal-continuation child exists.",
      )
    }
    val continuation = try {
      FeatureTaskRuntimeGoalContinuationArtifact.fromArtifactMap(
        artifacts.getValue(FEATURE_TASK_RUNTIME_GOAL_CONTINUATION_ARTIFACT_KEY).toReviewStateMap(
          FEATURE_TASK_RUNTIME_GOAL_CONTINUATION_ARTIFACT_KEY,
        ),
      )
    } catch (error: InvalidWorkflowStateSchemaError) {
      reviewStateError(FEATURE_TASK_RUNTIME_GOAL_CONTINUATION_ARTIFACT_KEY, error.message.orEmpty(), error)
    }
    val state = GoalSubtaskReviewState.fromArtifactMap(
      artifacts.getValue(GOAL_SUBTASK_REVIEW_STATE_ARTIFACT_KEY).toReviewStateMap(
        GOAL_SUBTASK_REVIEW_STATE_ARTIFACT_KEY,
      ),
    )
    if (state.codeReviewMode != continuation.codeReviewMode) {
      reviewStateError(
        "$GOAL_SUBTASK_REVIEW_STATE_ARTIFACT_KEY.code_review_mode",
        "must match the immutable goal-continuation review policy.",
      )
    }
    return GoalSubtaskReviewArtifacts(
      continuation = continuation,
      state = state,
      rawResults = rawResults(artifacts, state),
    )
  }

  internal fun decodeContinuationOnlyWire(artifacts: Map<String, Any?>): FeatureTaskRuntimeGoalContinuationArtifact? {
    if (FEATURE_TASK_RUNTIME_GOAL_CONTINUATION_ARTIFACT_KEY !in artifacts) {
      if (GOAL_SUBTASK_REVIEW_STATE_ARTIFACT_KEY in artifacts) {
        reviewStateError(
          FEATURE_TASK_RUNTIME_GOAL_CONTINUATION_ARTIFACT_KEY,
          "must be present whenever a goal-subtask review state exists.",
        )
      }
      if (GOAL_SUBTASK_REVIEW_RESULTS_ARTIFACT_KEY in artifacts) {
        reviewStateError(
          GOAL_SUBTASK_REVIEW_RESULTS_ARTIFACT_KEY,
          "must be absent when no goal-subtask review child state exists.",
        )
      }
      return null
    }
    return try {
      decodeWire(artifacts)?.continuation
    } catch (error: InvalidGoalSubtaskReviewStateSchemaError) {
      if (GOAL_SUBTASK_REVIEW_STATE_ARTIFACT_KEY !in artifacts) decodeContinuationDirect(artifacts) else throw error
    }
  }

  internal fun decodeReviewStateOnlyWire(artifacts: Map<String, Any?>): GoalSubtaskReviewState? =
    if (GOAL_SUBTASK_REVIEW_STATE_ARTIFACT_KEY !in artifacts) null else decodeWire(artifacts)?.state

  private fun decodeContinuationDirect(artifacts: Map<String, Any?>): FeatureTaskRuntimeGoalContinuationArtifact = try {
    FeatureTaskRuntimeGoalContinuationArtifact.fromArtifactMap(
      artifacts.getValue(FEATURE_TASK_RUNTIME_GOAL_CONTINUATION_ARTIFACT_KEY).toReviewStateMap(
        FEATURE_TASK_RUNTIME_GOAL_CONTINUATION_ARTIFACT_KEY,
      ),
    )
  } catch (error: InvalidWorkflowStateSchemaError) {
    reviewStateError(FEATURE_TASK_RUNTIME_GOAL_CONTINUATION_ARTIFACT_KEY, error.message.orEmpty(), error)
  }

  private fun rawResults(artifacts: Map<String, Any?>, state: GoalSubtaskReviewState): Map<String, String> {
    if (state.completedPassCount == 0) {
      val cleared = artifacts[GOAL_SUBTASK_REVIEW_RESULTS_ARTIFACT_KEY]
        ?.toReviewStateMap(GOAL_SUBTASK_REVIEW_RESULTS_ARTIFACT_KEY)
        .orEmpty()
      if (cleared.isNotEmpty()) {
        reviewStateError(
          GOAL_SUBTASK_REVIEW_RESULTS_ARTIFACT_KEY,
          "must hold no durable raw review result before the first completed review pass.",
        )
      }
      return emptyMap()
    }
    val raw = artifacts[GOAL_SUBTASK_REVIEW_RESULTS_ARTIFACT_KEY]
      ?.toReviewStateMap(GOAL_SUBTASK_REVIEW_RESULTS_ARTIFACT_KEY)
      ?: reviewStateError(
        GOAL_SUBTASK_REVIEW_RESULTS_ARTIFACT_KEY,
        "must contain the durable raw review result for every completed pass.",
      )
    val expectedKeys = state.passResults.map { result -> result.passNumber.toString() }.toSet()
    if (raw.keys != expectedKeys) {
      reviewStateError(
        GOAL_SUBTASK_REVIEW_RESULTS_ARTIFACT_KEY,
        "must contain exactly one durable raw review result for every completed pass.",
      )
    }
    return raw.mapValues { (passNumber, value) ->
      (value as? String)?.takeIf(String::isNotBlank)
        ?: reviewStateError(
          "$GOAL_SUBTASK_REVIEW_RESULTS_ARTIFACT_KEY.$passNumber",
          "must be a non-blank durable raw review result.",
        )
    }
  }
}
