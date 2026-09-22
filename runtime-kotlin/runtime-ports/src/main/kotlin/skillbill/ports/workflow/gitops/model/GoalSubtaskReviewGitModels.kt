package skillbill.ports.workflow.gitops.model

import java.security.MessageDigest

data class GoalSubtaskReviewBaseline(
  val reviewBaseSha: String,
  val baselineUntrackedPaths: List<String>,
  val ownedPathspec: List<String> = emptyList(),
) {
  init {
    require(GOAL_REVIEW_GIT_SHA.matches(reviewBaseSha)) {
      "reviewBaseSha must be a 40- or 64-character lowercase commit SHA."
    }
    require(baselineUntrackedPaths.all(String::isNotBlank)) { "baselineUntrackedPaths must not contain blanks." }
    require(ownedPathspec.all(String::isNotBlank)) { "ownedPathspec must not contain blanks." }
  }
}

data class GoalSubtaskReviewBaselineResult(
  val status: WorkflowGitOperationStatus,
  val baseline: GoalSubtaskReviewBaseline? = null,
  val error: String = "",
)

data class GoalSubtaskReviewBaselineRecoveryRequest(
  val unreachableSha: String,
  val failureReason: GoalSubtaskReviewInputFailureReason,
  val baselineUntrackedPaths: List<String>,
  val ownedPathspec: List<String> = emptyList(),
) {
  init {
    require(GOAL_REVIEW_GIT_SHA.matches(unreachableSha)) {
      "unreachableSha must be a 40- or 64-character lowercase commit SHA."
    }
    require(baselineUntrackedPaths.all(String::isNotBlank)) { "baselineUntrackedPaths must not contain blanks." }
    require(ownedPathspec.all(String::isNotBlank)) { "ownedPathspec must not contain blanks." }
  }

  fun toRecoveredBaseline(recoveredSha: String): GoalSubtaskReviewBaseline =
    GoalSubtaskReviewBaseline(recoveredSha, baselineUntrackedPaths, ownedPathspec)
}

data class GoalSubtaskReviewInput(
  val reviewBaseSha: String,
  val currentHeadSha: String,
  val trackedDelta: String,
  val ownedUntrackedPatches: String,
) {
  init {
    require(GOAL_REVIEW_GIT_SHA.matches(reviewBaseSha)) {
      "reviewBaseSha must be a 40- or 64-character lowercase commit SHA."
    }
    require(GOAL_REVIEW_GIT_SHA.matches(currentHeadSha)) {
      "currentHeadSha must be a 40- or 64-character lowercase commit SHA."
    }
  }

  val deltaDigest: String get() =
    MessageDigest.getInstance("SHA-256")
      .digest("$trackedDelta$ownedUntrackedPatches".toByteArray())
      .joinToString("") { byte -> "%02x".format(byte) }

  val reviewText: String get() =
    buildString {
      append(trackedDelta)
      if (ownedUntrackedPatches.isNotBlank()) {
        if (isNotEmpty() && !endsWith("\n")) append('\n')
        append(ownedUntrackedPatches)
      }
    }
}

data class GoalSubtaskReviewInputResult(
  val status: WorkflowGitOperationStatus,
  val input: GoalSubtaskReviewInput? = null,
  val error: String = "",
  val failureReason: GoalSubtaskReviewInputFailureReason? = null,
)

enum class GoalSubtaskReviewInputFailureReason {
  BASE_MISSING,
  BASE_NOT_ANCESTOR,
}

private val GOAL_REVIEW_GIT_SHA = Regex("^[0-9a-f]{40}(?:[0-9a-f]{24})?$")
