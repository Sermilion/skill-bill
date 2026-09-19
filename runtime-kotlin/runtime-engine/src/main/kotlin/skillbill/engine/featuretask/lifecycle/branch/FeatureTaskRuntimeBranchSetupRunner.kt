package skillbill.engine.featuretask.lifecycle.branch

import me.tatarka.inject.annotations.Inject
import skillbill.engine.featuretask.lifecycle.subtask.decide
import skillbill.engine.featuretask.model.core.FeatureTaskRuntimeRunRequest
import skillbill.engine.featuretask.phase.record.FeatureTaskRuntimePhaseRecorder
import skillbill.engine.featuretask.runloop.observability.FeatureTaskRuntimeRunObservability
import skillbill.ports.workflow.gitops.WorkflowGitOperations
import skillbill.ports.workflow.gitops.captureGoalSubtaskReviewBaseline
import skillbill.ports.workflow.gitops.model.WorkflowGitOperationResult
import skillbill.ports.workflow.gitops.model.WorkflowGitOperationStatus
import skillbill.ports.workflow.gitops.repositoryOwnedPaths
import skillbill.workflow.taskruntime.model.core.FeatureTaskRuntimeResolvedBranch
@Inject
class FeatureTaskRuntimeBranchSetupRunner(
  private val recorder: FeatureTaskRuntimePhaseRecorder,
  private val gitOperations: WorkflowGitOperations,
) {
  internal fun ensureFeatureBranch(
    request: FeatureTaskRuntimeRunRequest,
    observability: FeatureTaskRuntimeRunObservability,
  ): FeatureTaskRuntimeBranchSetupOutcome {
    val current = gitOperations.currentBranch(request.repoRoot)
    if (current !is WorkflowGitOperationResult.Ok) {
      return FeatureTaskRuntimeBranchSetupOutcome.blocked(branchSetupBlockedReason(current.error))
    }
    val persisted = recorder.loadResolvedBranch(request.workflowId)
    return when {
      persisted != null -> reattachPersisted(request, observability, persisted.branch, current.value)
      request.goalContinuation != null -> reattachGoalContinuationBranch(request, observability, current.value)
      else -> resolveAndEstablish(request, observability, current.value)
    }
  }

  private fun reattachGoalContinuationBranch(
    request: FeatureTaskRuntimeRunRequest,
    observability: FeatureTaskRuntimeRunObservability,
    currentBranch: String,
  ): FeatureTaskRuntimeBranchSetupOutcome {
    val decision = FeatureTaskRuntimeBranchSetup.goalContinuationDecision(
      requireNotNull(request.goalContinuation).goalBranch,
    )
    return when (decision) {
      is FeatureTaskRuntimeBranchDecisionInvalid ->
        FeatureTaskRuntimeBranchSetupOutcome.blocked(branchSetupDeriveBlockedReason(decision.reason))
      is FeatureTaskRuntimeBranchDecisionResolved -> {
        val blockedReason = reattachBlockedReason(request, decision.branch, currentBranch)
        blockedReason?.let(FeatureTaskRuntimeBranchSetupOutcome::blocked)
          ?: establishBranch(request, observability, decision.branch, baseBranch = null, created = false)
      }
    }
  }

  private fun reattachPersisted(
    request: FeatureTaskRuntimeRunRequest,
    observability: FeatureTaskRuntimeRunObservability,
    persistedBranch: String,
    currentBranch: String,
  ): FeatureTaskRuntimeBranchSetupOutcome {
    val blockedReason = reattachBlockedReason(request, persistedBranch, currentBranch)
    return blockedReason?.let(FeatureTaskRuntimeBranchSetupOutcome::blocked) ?: run {
      observability.branchResolved(
        featureTaskRuntimeBranchSetupGuardPhase,
        persistedBranch,
        created = false,
        reused = true,
      )
      FeatureTaskRuntimeBranchSetupOutcome.established(persistedBranch)
    }
  }

  private fun reattachBlockedReason(
    request: FeatureTaskRuntimeRunRequest,
    persistedBranch: String,
    currentBranch: String,
  ): String? {
    val normalizedPersisted = persistedBranch.trim()
    return if (currentBranch.trim() == normalizedPersisted) {
      FeatureTaskRuntimeBranchSetup.protectedBranchName(normalizedPersisted)
        ?.let(::branchSetupReattachProtectedReason)
    } else {
      persistedBranchUnusableReason(request, persistedBranch, currentBranch)
        ?: checkoutAndConfirmReason(request, persistedBranch, currentBranch)
    }
  }

  private fun persistedBranchUnusableReason(
    request: FeatureTaskRuntimeRunRequest,
    persistedBranch: String,
    currentBranch: String,
  ): String? {
    val exists = gitOperations.branchExists(request.repoRoot, persistedBranch)
    return when {
      exists !is WorkflowGitOperationResult.Ok ->
        branchSetupReattachExistenceUnreadableReason(persistedBranch, currentBranch, exists.error)
      exists.value.trim() != "true" -> branchSetupReattachMissingReason(persistedBranch, currentBranch)
      else -> null
    }
  }

  private fun checkoutAndConfirmReason(
    request: FeatureTaskRuntimeRunRequest,
    persistedBranch: String,
    currentBranch: String,
  ): String? {
    val checkout = gitOperations.checkoutBranch(request.repoRoot, persistedBranch, baseBranch = null)
    return if (checkout !is WorkflowGitOperationResult.Ok) {
      branchSetupReattachBlockedReason(persistedBranch, currentBranch, checkout.error)
    } else {
      landedBranchBlockedReason(request, persistedBranch)
    }
  }

  private fun resolveAndEstablish(
    request: FeatureTaskRuntimeRunRequest,
    observability: FeatureTaskRuntimeRunObservability,
    currentBranch: String,
  ): FeatureTaskRuntimeBranchSetupOutcome {
    val decision = FeatureTaskRuntimeBranchSetup.decide(
      issueKey = request.issueKey,
      specReference = request.runInvariants.specReference,
      currentBranch = currentBranch,
    )
    return when (decision) {
      is FeatureTaskRuntimeBranchDecisionInvalid ->
        FeatureTaskRuntimeBranchSetupOutcome.blocked(branchSetupDeriveBlockedReason(decision.reason))
      is FeatureTaskRuntimeBranchDecisionResolved ->
        if (decision.create) {
          createAndSwitch(request, observability, decision.branch, requireNotNull(decision.baseBranch))
        } else {
          establishBranch(request, observability, decision.branch, baseBranch = null, created = false)
        }
    }
  }

  private fun createAndSwitch(
    request: FeatureTaskRuntimeRunRequest,
    observability: FeatureTaskRuntimeRunObservability,
    branch: String,
    baseBranch: String,
  ): FeatureTaskRuntimeBranchSetupOutcome {
    val checkout = gitOperations.checkoutBranch(request.repoRoot, branch, baseBranch)
    if (checkout !is WorkflowGitOperationResult.Ok) {
      return FeatureTaskRuntimeBranchSetupOutcome.blocked(
        branchSetupCreateBlockedReason(branch, baseBranch, checkout.error),
      )
    }
    return landedBranchBlockedReason(request, branch)?.let(FeatureTaskRuntimeBranchSetupOutcome::blocked)
      ?: establishBranch(request, observability, branch, baseBranch, created = true)
  }

  private fun landedBranchBlockedReason(request: FeatureTaskRuntimeRunRequest, expectedBranch: String): String? {
    val landed = gitOperations.currentBranch(request.repoRoot)
    if (landed !is WorkflowGitOperationResult.Ok) {
      return branchSetupBlockedReason(landed.error)
    }
    val landedBranch = landed.value.trim()
    return when {
      landedBranch != expectedBranch.trim() ->
        "Feature-task-runtime checkout reported success but HEAD is on '$landedBranch', not the " +
          "expected feature branch '$expectedBranch'; refusing to run file-mutating phases."
      FeatureTaskRuntimeBranchSetup.protectedBranchName(landedBranch) != null ->
        "Feature-task-runtime landed on a protected branch '$landedBranch' for the feature branch; " +
          "refusing to run file-mutating phases on a protected branch."
      else -> null
    }
  }

  private fun establishBranch(
    request: FeatureTaskRuntimeRunRequest,
    observability: FeatureTaskRuntimeRunObservability,
    branch: String,
    baseBranch: String?,
    created: Boolean,
  ): FeatureTaskRuntimeBranchSetupOutcome {
    val baseline = gitOperations.captureGoalSubtaskReviewBaseline(request.repoRoot, branch)
    if (baseline.status != WorkflowGitOperationStatus.OK || baseline.baseline == null) {
      return FeatureTaskRuntimeBranchSetupOutcome.blocked(
        "Feature-task-runtime could not capture its immutable review base before implementation: ${baseline.error}",
      )
    }
    val immutableBase = requireNotNull(baseline.baseline)
    val baselineOwnedPaths = gitOperations.repositoryOwnedPaths(request.repoRoot)
    if (baselineOwnedPaths !is WorkflowGitOperationResult.Ok) {
      return FeatureTaskRuntimeBranchSetupOutcome.blocked(
        "Feature-task-runtime could not capture its workflow ownership baseline: ${baselineOwnedPaths.error}",
      )
    }
    val recorded = recorder.recordResolvedBranch(
      request.workflowId,
      FeatureTaskRuntimeResolvedBranch(
        branch = branch,
        baseBranch = baseBranch,
        created = created,
        reviewBaseSha = immutableBase.reviewBaseSha,
        baselineUntrackedPaths = immutableBase.baselineUntrackedPaths,
        baselineOwnedPaths = baselineOwnedPaths.value.orEmpty()
          .split('\u0000')
          .map(String::trim)
          .filter(String::isNotBlank)
          .distinct()
          .sorted(),
      ),
    )
    if (!recorded) {
      return FeatureTaskRuntimeBranchSetupOutcome.blocked(branchSetupNotPersistedBlockedReason(branch))
    }
    observability.branchResolved(featureTaskRuntimeBranchSetupGuardPhase, branch, created = created, reused = !created)
    return FeatureTaskRuntimeBranchSetupOutcome.established(branch)
  }
}

internal sealed interface FeatureTaskRuntimeBranchSetupOutcome {
  val establishedBranch: String?
  val blockedReason: String?

  companion object {
    fun established(branch: String): FeatureTaskRuntimeBranchSetupOutcome = Established(branch)

    fun blocked(reason: String): FeatureTaskRuntimeBranchSetupOutcome = Blocked(reason)
  }
}

private data class Established(val branch: String) : FeatureTaskRuntimeBranchSetupOutcome {
  override val establishedBranch: String get() = branch
  override val blockedReason: String? get() = null
}

private data class Blocked(val reason: String) : FeatureTaskRuntimeBranchSetupOutcome {
  override val establishedBranch: String? get() = null
  override val blockedReason: String get() = reason
}
