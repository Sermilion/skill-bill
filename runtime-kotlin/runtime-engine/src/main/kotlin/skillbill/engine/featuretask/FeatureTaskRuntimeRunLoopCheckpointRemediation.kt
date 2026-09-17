package skillbill.engine.featuretask

import skillbill.contracts.JsonCodec
import skillbill.contracts.SharedPayloadKeys
import skillbill.engine.diagnostics.RuntimeDiagnosticsBestEffortWarning
import skillbill.engine.featuretask.model.FeatureTaskRuntimeCheckpointDecision
import skillbill.engine.featuretask.model.FeatureTaskRuntimeRunRequest
import skillbill.engine.featuretask.model.FeatureTaskRuntimeSubtaskCommitIdentity
import skillbill.ports.workflow.gitops.captureIndexState
import skillbill.ports.workflow.gitops.model.WorkflowGitOperationResult
import skillbill.ports.workflow.gitops.pathContentIdentities
import skillbill.ports.workflow.gitops.repositoryOwnedPaths
import skillbill.ports.workflow.gitops.stagePaths
import skillbill.workflow.model.WorkflowStepStatus
import skillbill.workflow.taskruntime.FeatureTaskRuntimePhaseWorkflowDefinition
import skillbill.workflow.taskruntime.FeatureTaskRuntimeWorkflowArtifactMap
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeCheckpointIdentity
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeResolvedBranch

object FeatureTaskRuntimeRunLoopCheckpointRemediation {
  internal fun concurrentlyModifiedOwnedPaths(
    request: FeatureTaskRuntimeRunRequest,
    session: FeatureTaskRuntimeRunLoopSession,
    phaseGates: FeatureTaskRuntimePhaseGates,
    phaseId: String,
    ownedPaths: List<String>,
  ): List<String> {
    val captured = session.phaseContentIdentitiesFor(phaseId)
    if (captured.isEmpty()) return emptyList()
    val current = phaseGates.gitOperations.pathContentIdentities(request.repoRoot, ownedPaths)
    if (current !is WorkflowGitOperationResult.Ok) return emptyList()
    val now = FeatureTaskRuntimeRunLoopLaunch.parseContentIdentities(current.value.orEmpty())
    return captured.filter { (path, identity) -> path in now && now[path] != identity }.keys.sorted()
  }

  internal fun blockCheckpointScope(
    context: FeatureTaskRuntimeRunLoopContext,
    precedingPhaseId: String,
    branch: String,
    error: String,
    blockedReason: (
      String,
      String,
    ) -> String,
  ): FeatureTaskRuntimeCheckpointDecision? {
    with(context) {
      with(FeatureTaskRuntimeRunLoopCheckpoint) {
        FeatureTaskRuntimeRunLoopCheckpoint.blockCheckpoint(context, precedingPhaseId, branch, error, blockedReason)
      }
      return null
    }
  }

  internal fun checkpointWorktreeDelta(
    request: FeatureTaskRuntimeRunRequest,
    phaseGates: FeatureTaskRuntimePhaseGates,
    baselineOwnedPaths: List<String>,
  ): List<String>? {
    val owned = phaseGates.gitOperations.repositoryOwnedPaths(request.repoRoot)
    if (owned !is WorkflowGitOperationResult.Ok) return null
    val baseline = baselineOwnedPaths.toSet()
    return owned.value.orEmpty()
      .split(OWNED_PATH_DELIMITER)
      .map(String::trim)
      .filter(String::isNotBlank)
      .filterNot { it in baseline }
      .filterNot(::isRuntimePrivatePath)
      .distinct()
      .sorted()
  }

  internal fun recordRemediationBaseSha(
    context: FeatureTaskRuntimeRunLoopContext,
    precedingPhaseId: String,
    commitSha: String? = null,
  ): Boolean {
    with(context) {
      if (!isGoalContinuationRun(request)) return true
      if (FeatureTaskRuntimeRunLoopPlanningBranch.goalReviewStateOrNull(
          request,
          goalContinuationRecorder,
        ) == null
      ) {
        return true
      }
      val baseSha = commitSha?.trim()?.takeIf(String::isNotBlank) ?: run {
        val head = phaseGates.gitOperations.headCommitSha(request.repoRoot)
        if (head !is WorkflowGitOperationResult.Ok || head.value.isBlank()) {
          return FeatureTaskRuntimeRunLoopRepairReceipt.blockRemediationBaseSha(
            request,
            state,
            session,
            precedingPhaseId,
            head.error.ifBlank { "HEAD resolved to an empty sha." },
          )
        }
        head.value.trim()
      }
      return runCatching {
        goalContinuationRecorder.updateReviewState(
          request.workflowId,
        ) { state ->
          state.copy(remediationBaseSha = baseSha)
        }
      }.fold(
        onSuccess = { recorded ->
          if (recorded != null) {
            true
          } else {
            FeatureTaskRuntimeRunLoopRepairReceipt.blockRemediationBaseSha(
              request,
              state,
              session,
              precedingPhaseId,
              "the review persistence.state could not be updated.",
            )
          }
        },
        onFailure = { error ->
          FeatureTaskRuntimeRunLoopRepairReceipt.blockRemediationBaseSha(
            request,
            state,
            session,
            precedingPhaseId,
            error.message.orEmpty(),
          )
        },
      )
    }
  }

  internal fun completedImplementFixProducedOutputs(
    run: PhaseRun,
    outputMap: FeatureTaskRuntimeWorkflowArtifactMap,
  ): Map<String, Any?>? = outputMap
    .takeIf {
      run.phaseId == FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_IMPLEMENT_FIX &&
        (it[SharedPayloadKeys.STATUS] as? String)?.let(WorkflowStepStatus::fromWire) == WorkflowStepStatus.COMPLETED
    }
    ?.let { JsonCodec.anyToStringAnyMap(it[SharedPayloadKeys.PRODUCED_OUTPUTS]).orEmpty() }

  internal fun establishRemediationCheckpoint(
    context: FeatureTaskRuntimeRunLoopContext,
    precedingPhaseId: String,
    loopId: String,
  ): Boolean {
    with(context) {
      if (FeatureTaskRuntimeRunLoopCheckpointRemediation.remediationCheckpointSkippable(session)) {
        return FeatureTaskRuntimeRunLoopCheckpointRemediation.recordRemediationBaseIfNeeded(
          context,
          precedingPhaseId,
          loopId,
          commitSha = null,

          parentSha = null,
        )
      }
      val branch = requireNotNull(session.resolvedBranch)
      if (FeatureTaskRuntimeRunLoopCheckpointRemediation.remediationCheckpointOffBranch(request, phaseGates, branch)) {
        return FeatureTaskRuntimeRunLoopCheckpointRemediation.recordRemediationBaseIfNeeded(
          context,
          precedingPhaseId,
          loopId,
          commitSha = null,

          parentSha = null,
        )
      }
      val scope = with(FeatureTaskRuntimeRunLoopCheckpoint) {
        FeatureTaskRuntimeRunLoopCheckpoint.resolveCheckpointScope(
          context,
          precedingPhaseId,

          branch,
        ) { errorBranch, error ->
          FeatureTaskRuntimeRunLoopPlanningBranch.remediationCheckpointBlockedReason(
            errorBranch,
            error,
          )
        }
      } ?: return false
      return when (scope) {
        is FeatureTaskRuntimeCheckpointDecision.Skip ->
          FeatureTaskRuntimeRunLoopCheckpointRemediation.recordRemediationBaseIfNeeded(
            context,
            precedingPhaseId,
            loopId,
            commitSha = null,

            parentSha = null,
          )
        is FeatureTaskRuntimeCheckpointDecision.Block -> {
          FeatureTaskRuntimeRunLoopPlanningBranch.blockAt(request, state, session, precedingPhaseId, scope.reason)
          false
        }
        is FeatureTaskRuntimeCheckpointDecision.Stage ->
          FeatureTaskRuntimeRunLoopCheckpointRemediation.establishRemediationCheckpointStage(
            context,
            precedingPhaseId,
            branch,
            loopId,

            scope,
          )
      }
    }
  }

  internal fun commitRemediationCheckpoint(
    context: FeatureTaskRuntimeRunLoopContext,
    precedingPhaseId: String,
    branch: String,
    loopId: String,
    ownedPaths: List<String>,
  ): RemediationCheckpointCommit? {
    with(context) {
      val prepared = FeatureTaskRuntimeRunLoopCheckpointRemediation.prepareRemediationCommit(
        context,
        precedingPhaseId,
        branch,
        loopId,
        ownedPaths,
      ) ?: return null
      return with(FeatureTaskRuntimeRunLoopCheckpoint) {
        FeatureTaskRuntimeRunLoopCheckpoint.finalizeRemediationCommit(context, prepared)
      }
    }
  }

  internal fun recordRemediationBaseIfNeeded(
    context: FeatureTaskRuntimeRunLoopContext,
    precedingPhaseId: String,
    loopId: String,
    commitSha: String?,
    parentSha: String?,
  ): Boolean {
    with(context) {
      if (loopId != FeatureTaskRuntimePhaseWorkflowDefinition.REVIEW_FIX_LOOP_ID) return true
      val recorded = FeatureTaskRuntimeRunLoopCheckpointRemediation.recordRemediationBaseSha(
        context,
        precedingPhaseId,

        commitSha,
      )
      if (recorded) return true
      if (commitSha != null) {
        FeatureTaskRuntimeRunLoopCheckpointRemediation.rollbackRemediationCheckpointCommit(
          context,
          commitSha,
          parentSha,
          identityRecorded = true,
        )
      }
      return false
    }
  }

  internal fun rollbackRemediationCheckpointCommit(
    context: FeatureTaskRuntimeRunLoopContext,
    commitSha: String,
    parentSha: String?,
    identityRecorded: Boolean,
  ) {
    with(context) {
      val normalizedCommit = commitSha.trim()
      val head = phaseGates.gitOperations.headCommitSha(request.repoRoot)
      if (head !is WorkflowGitOperationResult.Ok || head.value.trim() != normalizedCommit) return
      val identities = with(FeatureTaskRuntimeRunLoopCheckpoint) {
        FeatureTaskRuntimeRunLoopCheckpoint.checkpointIdentitiesForRollback(context, normalizedCommit)
      }
      val restoreSha = FeatureTaskRuntimeRunLoopCheckpointRemediation.remediationRollbackTargetSha(
        context,
        identities = identities,
        commitSha = normalizedCommit,
        parentSha = parentSha,

        identityRecorded = identityRecorded,
      ) ?: return
      val reset = phaseGates.gitOperations.resetSoftToCommit(request.repoRoot, restoreSha)
      if (reset !is WorkflowGitOperationResult.Ok) {
        FeatureTaskRuntimeRunLoopCheckpointRemediation.recordRemediationRollbackDegradation(
          context,
          seam = "FeatureTaskRuntimeRunLoop.rollbackRemediationCheckpointCommit",
          valueUsed = restoreSha,
          valueExpected = "successful soft reset to restore target",

          cause = reset.error.ifBlank { "resetSoftToCommit failed" },
        )
      }
    }
  }

  internal fun recordRemediationRollbackDegradation(
    context: FeatureTaskRuntimeRunLoopContext,
    seam: String,
    valueUsed: String,
    valueExpected: String,
    cause: String,
  ) {
    with(context) {
      goalContinuationRecorder.appendRemediationRollbackDegradationEvidence(
        workflowId = request.workflowId,
        signal = RemediationDegradationSignal(
          seam = seam,
          valueUsed = valueUsed,
          valueExpected = valueExpected,
          cause = cause,
        ),
      )
    }
  }

  internal fun remediationRollbackTargetSha(
    context: FeatureTaskRuntimeRunLoopContext,
    identities: List<FeatureTaskRuntimeCheckpointIdentity>,
    commitSha: String,
    parentSha: String?,
    identityRecorded: Boolean,
  ): String? {
    with(context) {
      val fallback = parentSha?.trim()?.takeIf(String::isNotBlank)
      val predecessor = rollbackPredecessor(identities, commitSha, identityRecorded) ?: return fallback
      return FeatureTaskRuntimeRunLoopCheckpointRemediation.resolvedPredecessorSha(context, predecessor) ?: fallback
    }
  }

  internal fun rollbackPredecessor(
    identities: List<FeatureTaskRuntimeCheckpointIdentity>,
    commitSha: String,
    identityRecorded: Boolean,
  ): FeatureTaskRuntimeCheckpointIdentity? {
    val currentIdentity = if (identityRecorded) identities.lastOrNull { it.commitSha == commitSha } else null
    return when {
      currentIdentity != null && currentIdentity.sequenceNumber > 0 ->
        identities.find { it.sequenceNumber == currentIdentity.sequenceNumber - 1 }
      currentIdentity != null -> null
      else -> identities.maxByOrNull { it.sequenceNumber }
    }
  }

  internal fun resolvedPredecessorSha(
    context: FeatureTaskRuntimeRunLoopContext,
    predecessor: FeatureTaskRuntimeCheckpointIdentity,
  ): String? {
    with(context) {
      val predecessorCommitSha = predecessor.commitSha.trim()
      if (predecessorCommitSha.isBlank()) {
        FeatureTaskRuntimeRunLoopCheckpointRemediation.recordRemediationRollbackDegradation(
          context,
          seam = "FeatureTaskRuntimeRunLoop.remediationRollbackTargetSha",
          valueUsed = "(blank)",
          valueExpected = "resolvable predecessor identity commit",

          cause = "predecessor identity commit sha was missing or blank",
        )
        return null
      }
      val resolved = phaseGates.gitOperations.resolveCommit(request.repoRoot, predecessorCommitSha)
      val predecessorSha = resolved.value.orEmpty().trim()
        .takeIf { resolved is WorkflowGitOperationResult.Ok && it.isNotBlank() }
      if (predecessorSha == null) {
        FeatureTaskRuntimeRunLoopCheckpointRemediation.recordRemediationRollbackDegradation(
          context,
          seam = "FeatureTaskRuntimeRunLoop.remediationRollbackTargetSha",
          valueUsed = predecessorCommitSha,
          valueExpected = "resolvable predecessor identity commit",

          cause = resolved.error.takeIf { resolved !is WorkflowGitOperationResult.Ok && it.isNotBlank() }
            ?: "predecessor commit '$predecessorCommitSha' did not resolve",
        )
      }
      return predecessorSha
    }
  }

  internal fun checkpointEstablished(
    context: FeatureTaskRuntimeRunLoopContext,
    precedingPhaseId: String,
    loopId: String?,
    intent: String,
    blockedReason: (
      String,
      String,
    ) -> String,
  ): Boolean {
    with(context) {
      val branch = session.resolvedBranch
      if (branch == null || FeatureTaskRuntimeBranchSetup.protectedBranchName(branch) != null) {
        return true
      }
      val head = phaseGates.gitOperations.currentBranch(request.repoRoot)
      if (head !is WorkflowGitOperationResult.Ok || head.value.trim() != branch.trim()) {
        return true
      }
      val scope = with(FeatureTaskRuntimeRunLoopCheckpoint) {
        FeatureTaskRuntimeRunLoopCheckpoint.resolveCheckpointScope(context, precedingPhaseId, branch, blockedReason)
      } ?: return false
      return when (scope) {
        is FeatureTaskRuntimeCheckpointDecision.Skip -> true
        is FeatureTaskRuntimeCheckpointDecision.Block -> {
          FeatureTaskRuntimeRunLoopPlanningBranch.blockAt(request, state, session, precedingPhaseId, scope.reason)
          false
        }
        is FeatureTaskRuntimeCheckpointDecision.Stage -> {
          if (scope.adoptedPaths.isNotEmpty()) {
            RuntimeDiagnosticsBestEffortWarning.record(
              diagnostics,
              adoptionWarning(branch, scope.adoptedPaths),
            )
          }
          with(FeatureTaskRuntimeRunLoopRepairReceipt) {
            FeatureTaskRuntimeRunLoopRepairReceipt.commitCheckpoint(
              context,
              CommitCheckpointArgs(
                precedingPhaseId = precedingPhaseId,
                branch = branch,
                loopId = loopId,
                intent = intent,
                ownedPaths = scope.ownedPaths,
                blockedReason = blockedReason,
              ),
            )
          }
        }
      }
    }
  }

  internal fun remediationCheckpointSkippable(session: FeatureTaskRuntimeRunLoopSession): Boolean {
    val branch = session.resolvedBranch
    return branch == null || FeatureTaskRuntimeBranchSetup.protectedBranchName(branch) != null
  }

  internal fun remediationCheckpointOffBranch(
    request: FeatureTaskRuntimeRunRequest,
    phaseGates: FeatureTaskRuntimePhaseGates,
    branch: String,
  ): Boolean {
    val head = phaseGates.gitOperations.currentBranch(request.repoRoot)
    return head !is WorkflowGitOperationResult.Ok || head.value.trim() != branch.trim()
  }

  internal fun establishRemediationCheckpointStage(
    context: FeatureTaskRuntimeRunLoopContext,
    precedingPhaseId: String,
    branch: String,
    loopId: String,
    scope: FeatureTaskRuntimeCheckpointDecision.Stage,
  ): Boolean {
    with(context) {
      if (scope.adoptedPaths.isNotEmpty()) {
        RuntimeDiagnosticsBestEffortWarning.record(
          diagnostics,
          adoptionWarning(branch, scope.adoptedPaths),
        )
      }
      val committed = FeatureTaskRuntimeRunLoopCheckpointRemediation.commitRemediationCheckpoint(
        context,
        precedingPhaseId = precedingPhaseId,
        branch = branch,
        loopId = loopId,

        ownedPaths = scope.ownedPaths,
      ) ?: return false
      return FeatureTaskRuntimeRunLoopCheckpointRemediation.recordRemediationBaseIfNeeded(
        context,
        precedingPhaseId = precedingPhaseId,
        loopId = loopId,
        commitSha = committed.commitSha,
        parentSha = committed.parentSha,
      )
    }
  }

  internal data class RemediationCommitPrepared(
    val precedingPhaseId: String,
    val branch: String,
    val loopId: String,
    val ownedPaths: List<String>,
    val indexSnapshot: String,
    val parentSha: String?,
    val subtaskIdentity: FeatureTaskRuntimeSubtaskCommitIdentity,
    val message: String,
  )

  internal fun prepareRemediationCommit(
    context: FeatureTaskRuntimeRunLoopContext,
    precedingPhaseId: String,
    branch: String,
    loopId: String,
    ownedPaths: List<String>,
  ): RemediationCommitPrepared? {
    with(context) {
      val snapshot = phaseGates.gitOperations.captureIndexState(request.repoRoot, ownedPaths)
      if (snapshot !is WorkflowGitOperationResult.Ok) {
        return blockRemediationCommitPreparation(context, precedingPhaseId, branch, snapshot.error)
      }
      val parentSha = phaseGates.gitOperations.headCommitSha(request.repoRoot)
        .takeIf { it is WorkflowGitOperationResult.Ok }?.value?.trim()?.takeIf(String::isNotBlank)
      val staged = phaseGates.gitOperations.stagePaths(request.repoRoot, ownedPaths)
      if (staged !is WorkflowGitOperationResult.Ok) {
        return blockRemediationCommitPreparation(
          context,
          precedingPhaseId,
          branch,
          FeatureTaskRuntimeRunLoopCheckpoint.withIndexRestoreOutcome(
            request,
            phaseGates,
            staged.error,
            ownedPaths,
            snapshot.value.orEmpty(),
          ),
        )
      }
      val subtaskIdentity = FeatureTaskRuntimeRunLoopCheckpoint.subtaskCommitIdentity(request)
      val message = FeatureTaskRuntimeRunLoopCheckpoint.checkpointCommitMessage(
        request,
        state,
        diagnostics,
        CheckpointCommitMessageArgs(
          branch = branch,
          phaseId = precedingPhaseId,
          loopId = loopId,
          identity = subtaskIdentity,
          intent = FeatureTaskRuntimeCheckpointMessage.INTENT_REMEDIATION,
        ),
      )
      return RemediationCommitPrepared(
        precedingPhaseId = precedingPhaseId,
        branch = branch,
        loopId = loopId,
        ownedPaths = ownedPaths,
        indexSnapshot = snapshot.value.orEmpty(),
        parentSha = parentSha,
        subtaskIdentity = subtaskIdentity,
        message = message,
      )
    }
  }

  private fun blockRemediationCommitPreparation(
    context: FeatureTaskRuntimeRunLoopContext,
    precedingPhaseId: String,
    branch: String,
    reason: String,
  ): RemediationCommitPrepared? {
    FeatureTaskRuntimeRunLoopCheckpoint.blockCheckpoint(
      context,
      precedingPhaseId,
      branch,
      reason,
      FeatureTaskRuntimeRunLoopCheckpoint.remediationCheckpointBlockedReasonFor(),
    )
    return null
  }
}

fun FeatureTaskRuntimeResolvedBranch.baselineOwnedPathsForCheckpoint(): List<String> =
  baselineOwnedPaths.ifEmpty { baselineUntrackedPaths }
