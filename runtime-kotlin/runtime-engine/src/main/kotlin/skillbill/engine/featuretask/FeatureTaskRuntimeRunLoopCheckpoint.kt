package skillbill.engine.featuretask

import skillbill.engine.featuretask.model.AppendCheckpointIdentityArgs
import skillbill.engine.featuretask.model.FeatureTaskRuntimeCheckpointDecision
import skillbill.engine.featuretask.model.FeatureTaskRuntimeCheckpointScopeInput
import skillbill.engine.featuretask.model.FeatureTaskRuntimeRunRequest
import skillbill.engine.featuretask.model.FeatureTaskRuntimeSubtaskCommitIdentity
import skillbill.ports.diagnostics.RuntimeDiagnostics
import skillbill.ports.workflow.gitops.headCommitMessage
import skillbill.ports.workflow.gitops.model.WorkflowGitOperationResult
import skillbill.ports.workflow.gitops.restoreIndexState
import skillbill.ports.workflow.gitops.stagedPaths
import skillbill.workflow.taskruntime.model.FEATURE_TASK_RUNTIME_STANDALONE_SUBTASK_ID
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeBackwardEdge
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeCheckpointIdentity
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeTransitionDeclaration
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeVerdict

object FeatureTaskRuntimeRunLoopCheckpoint {
  internal fun FeatureTaskRuntimeRunLoopContext.resolveCheckpointScope(
    precedingPhaseId: String,
    branch: String,
    blockedReason: (
      String,
      String,
    ) -> String,
  ): FeatureTaskRuntimeCheckpointDecision? {
    val preparation = prepareCheckpointScope(precedingPhaseId, branch, blockedReason) ?: return null
    val ownedInventory = checkpointOwnedInventory(request, preparation)
    val resolved = recorder.loadResolvedBranch(request.workflowId)
    persistOwnedInventory(request, recorder, ownedInventory, resolved?.workflowOwnedPaths.orEmpty())
    session.markCheckpointOwnershipDecided()
    return FeatureTaskRuntimeCheckpointScope.decide(
      FeatureTaskRuntimeCheckpointScopeInput(
        issueKey = request.issueKey,
        ownedPaths = ownedInventory,
        phaseIntroducedPaths = preparation.phaseWritten,
        worktreeDeltaPaths = preparation.worktreeDelta,
        foreignStagedPaths = preparation.stagedPaths,
        concurrentlyModifiedOwnedPaths = FeatureTaskRuntimeRunLoopCheckpointRemediation
          .concurrentlyModifiedOwnedPaths(request, session, phaseGates, precedingPhaseId, ownedInventory),
        deletedPaths = preparation.deletedPaths,
      ),
    )
  }
  internal fun checkpointDeletedPaths(
    request: FeatureTaskRuntimeRunRequest,
    phaseGates: FeatureTaskRuntimePhaseGates,
  ): List<String> {
    val status = phaseGates.gitOperations.worktreeStatus(request.repoRoot)
    if (status !is WorkflowGitOperationResult.Ok) return emptyList()
    return FeatureTaskRuntimePhaseSafetyPolicy.deletedPaths(status.value.orEmpty())
  }

  internal fun absorbableDeletedPaths(deleted: List<String>, ownedOrIntroduced: List<String>): List<String> {
    if (deleted.isEmpty() || ownedOrIntroduced.isEmpty()) return emptyList()
    val anchors = ownedOrIntroduced.map { path -> path.substringBeforeLast('/', missingDelimiterValue = path) }
      .filter(String::isNotBlank)
      .distinct()
    return deleted.filter { removed ->
      val parent = removed.substringBeforeLast('/', missingDelimiterValue = removed)
      anchors.any { anchor ->
        parent == anchor ||
          anchor.startsWith("$parent/") ||
          parent.startsWith("$anchor/")
      }
    }
  }

  internal fun mayExtendOwnedInventory(phaseId: String): Boolean = phaseId in INVENTORY_EXTENDING_PHASES

  internal fun writingPhaseIntroducedPaths(
    request: FeatureTaskRuntimeRunRequest,
    recorder: FeatureTaskRuntimePhaseRecorder,
    diagnostics: RuntimeDiagnostics,
    worktreeDelta: List<String>,
  ): List<String> {
    val records = recorder.loadPhaseRecords(
      request.workflowId,
    ).orEmpty()
    val writingRecords = INVENTORY_EXTENDING_PHASES.mapNotNull { records[it] }
    if (writingRecords.isEmpty()) {
      if (worktreeDelta.isNotEmpty()) {
        runCatching {
          diagnostics.warning(
            "Feature-task-runtime checkpoint has no durable file manifest for any writing phase; " +
              "the whole working-tree delta is treated as this workflow's own writes.",
          )
        }
      }
      return worktreeDelta
    }
    val introduced = writingRecords.flatMap { it.fileManifestIntroduced + it.fileManifestAfter }.distinct()
    return phaseWrittenPaths(worktreeDelta, introduced)
  }

  internal fun FeatureTaskRuntimeRunLoopContext.phaseWrittenPaths(
    phaseId: String,
    worktreeDelta: List<String>,
    persistedInventory: List<String>,
  ): List<String> {
    val record = recorder.loadPhaseRecords(
      request.workflowId,
    )?.get(phaseId)
    if (record == null) {
      if (worktreeDelta.isNotEmpty()) {
        runCatching {
          diagnostics.warning(
            "Feature-task-runtime checkpoint for phase '$phaseId' has no durable file manifest; " +
              "the whole working-tree delta is treated as the phase's own writes.",
          )
        }
      }
      return worktreeDelta
    }
    val owned = persistedInventory.toSet()
    val ownedStillDirty = record.fileManifestAfter.filter { it in owned }
    val manifest = (record.fileManifestIntroduced + ownedStillDirty).distinct()
    return phaseWrittenPaths(worktreeDelta, manifest)
  }

  internal fun persistOwnedInventory(
    request: FeatureTaskRuntimeRunRequest,
    recorder: FeatureTaskRuntimePhaseRecorder,
    inventory: List<String>,
    persisted: List<String>,
  ) {
    if (inventory.sorted() == persisted.sorted()) return
    recorder.recordWorkflowOwnedPaths(request.workflowId, inventory)
  }

  private fun FeatureTaskRuntimeRunLoopContext.stagedCheckpointPaths(
    precedingPhaseId: String,
    branch: String,
    blockedReason: (
      String,
      String,
    ) -> String,
  ): List<String>? {
    val staged = phaseGates.gitOperations.stagedPaths(request.repoRoot)
    if (staged !is WorkflowGitOperationResult.Ok) {
      with(FeatureTaskRuntimeRunLoopCheckpointRemediation) {
        this@stagedCheckpointPaths.blockCheckpointScope(
          precedingPhaseId,
          branch,
          staged.error,
          blockedReason,
        )
      }
      return null
    }
    return staged.value.orEmpty().split(OWNED_PATH_DELIMITER)
      .map(String::trim)
      .filter(String::isNotBlank)
  }

  internal fun FeatureTaskRuntimeRunLoopContext.prepareCheckpointScope(
    precedingPhaseId: String,
    branch: String,
    blockedReason: (
      String,
      String,
    ) -> String,
  ): CheckpointScopePreparation? {
    val resolved = recorder.loadResolvedBranch(request.workflowId)
    val worktreeDelta = FeatureTaskRuntimeRunLoopCheckpointRemediation.checkpointWorktreeDelta(
      request,
      phaseGates,
      resolved?.baselineOwnedPathsForCheckpoint().orEmpty(),
    )
      ?: run {
        with(FeatureTaskRuntimeRunLoopCheckpointRemediation) {
          this@prepareCheckpointScope.blockCheckpointScope(
            precedingPhaseId,
            branch,
            "the owned-path inventory could not be read",
            blockedReason,
          )
        }
        return null
      }
    val stagedPaths = stagedCheckpointPaths(precedingPhaseId, branch, blockedReason) ?: return null
    val persistedOwned = resolved?.workflowOwnedPaths.orEmpty()
    val evictedFeatureSpecs = persistedOwned
      .filter { path -> isFeatureSpecPathForIssue(path, request.issueKey) }
      .toSet()
    val phaseWritten = phaseWrittenPaths(precedingPhaseId, worktreeDelta, persistedOwned)
      .filterNot { it in evictedFeatureSpecs }
    val writingIntroduced = writingPhaseIntroducedPaths(
      request,
      recorder,
      diagnostics,

      worktreeDelta,
    )
    val seedOwned = (
      resolved?.workflowOwnedPaths.orEmpty() +
        phaseWritten
          .takeIf { FeatureTaskRuntimeRunLoopCheckpoint.mayExtendOwnedInventory(precedingPhaseId) }
          .orEmpty() +
        writingIntroduced
      ).distinct()
    val deletedPaths = absorbableDeletedPaths(
      deleted = checkpointDeletedPaths(request, phaseGates),
      ownedOrIntroduced = seedOwned + phaseWritten,
    )
    return CheckpointScopePreparation(
      worktreeDelta = worktreeDelta,
      stagedPaths = stagedPaths,
      phaseWritten = phaseWritten,
      writingIntroduced = writingIntroduced,
      seedOwned = seedOwned,
      deletedPaths = deletedPaths,
    )
  }
  internal fun checkpointOwnedInventory(
    request: FeatureTaskRuntimeRunRequest,
    preparation: CheckpointScopePreparation,
  ): List<String> = reconcileCheckpointPathInventory(
    repoRoot = request.repoRoot,
    issueKey = request.issueKey,
    specReference = request.runInvariants.specReference,
    paths = (preparation.seedOwned + preparation.deletedPaths)
      .filterNot { path -> isFeatureSpecPathForIssue(path, request.issueKey) },
  )

  internal fun FeatureTaskRuntimeRunLoopContext.finalizeRemediationCommit(
    prepared: FeatureTaskRuntimeRunLoopCheckpointRemediation.RemediationCommitPrepared,
  ): RemediationCheckpointCommit? {
    val commit = with(FeatureTaskRuntimeRunLoopCheckpoint) {
      this@finalizeRemediationCommit.writeSubtaskCommit(
        prepared.branch,
        prepared.message,
        prepared.subtaskIdentity,
      )
    }
    if (commit !is WorkflowGitOperationResult.Ok) {
      blockRemediationCommitFailure(prepared, commit.error)
      return null
    }
    val commitSha = commit.value.orEmpty().trim()
    if (commitSha.isBlank()) {
      blockRemediationCommitFailure(prepared, "remediation checkpoint commit returned an empty sha")
      return null
    }
    val recorded = with(FeatureTaskRuntimeRunLoopCheckpoint) {
      this@finalizeRemediationCommit.recordCheckpointIdentity(
        RecordCheckpointIdentityArgs(
          precedingPhaseId = prepared.precedingPhaseId,
          branch = prepared.branch,
          loopId = prepared.loopId,
          ownedPaths = prepared.ownedPaths,
          parentSha = prepared.parentSha,
          commitSha = commitSha,
          blockedReason = FeatureTaskRuntimeRunLoopCheckpoint.remediationCheckpointBlockedReasonFor(),
        ),
      )
    }
    if (!recorded) {
      with(FeatureTaskRuntimeRunLoopCheckpointRemediation) {
        this@finalizeRemediationCommit.rollbackRemediationCheckpointCommit(
          commitSha,
          prepared.parentSha,
          identityRecorded = false,
        )
      }
      return null
    }
    return RemediationCheckpointCommit(commitSha = commitSha, parentSha = prepared.parentSha)
  }

  private fun FeatureTaskRuntimeRunLoopContext.blockRemediationCommitFailure(
    prepared: FeatureTaskRuntimeRunLoopCheckpointRemediation.RemediationCommitPrepared,
    error: String,
  ) {
    with(FeatureTaskRuntimeRunLoopCheckpoint) {
      this@blockRemediationCommitFailure.blockCheckpoint(
        prepared.precedingPhaseId,
        prepared.branch,
        FeatureTaskRuntimeRunLoopCheckpoint.withIndexRestoreOutcome(
          request,
          phaseGates,
          error,
          prepared.ownedPaths,
          prepared.indexSnapshot,
        ),
        FeatureTaskRuntimeRunLoopCheckpoint.remediationCheckpointBlockedReasonFor(),
      )
    }
  }

  internal fun FeatureTaskRuntimeRunLoopContext.checkpointIdentitiesForRollback(
    commitSha: String,
  ): List<FeatureTaskRuntimeCheckpointIdentity> {
    require(commitSha.isNotBlank()) { "rollback requires a non-blank commit sha" }
    val subtaskId = request.goalContinuation?.subtaskId?.toString()
      ?: FEATURE_TASK_RUNTIME_STANDALONE_SUBTASK_ID
    return runCatching {
      recorder.loadCheckpointIdentities(request.workflowId)
    }.fold(
      onSuccess = { loaded -> loaded.orEmpty() },
      onFailure = { error ->
        with(FeatureTaskRuntimeRunLoopCheckpointRemediation) {
          this@checkpointIdentitiesForRollback.recordRemediationRollbackDegradation(
            seam = "FeatureTaskRuntimeRunLoop.rollbackRemediationCheckpointCommit",
            valueUsed = request.workflowId,
            valueExpected = "checkpoint identities for rollback",
            cause = "loadCheckpointIdentities failed: " +
              error.message.orEmpty().ifBlank { error::class.simpleName.orEmpty() },
          )
        }
        emptyList()
      },
    )
      .filter { it.issueKey == request.issueKey && it.subtaskId == subtaskId }
      .sortedBy { it.sequenceNumber }
  }

  internal fun subtaskCommitIdentity(request: FeatureTaskRuntimeRunRequest): FeatureTaskRuntimeSubtaskCommitIdentity =
    FeatureTaskRuntimeSubtaskCommitIdentity(
      issueKey = request.issueKey,
      subtaskId = request.goalContinuation?.subtaskId?.toString() ?: FEATURE_TASK_RUNTIME_STANDALONE_SUBTASK_ID,
    )

  internal fun checkpointCommitMessage(
    request: FeatureTaskRuntimeRunRequest,
    state: FeatureTaskRuntimeRunState,
    diagnostics: RuntimeDiagnostics,
    args: CheckpointCommitMessageArgs,
  ): String {
    val branch = args.branch
    val phaseId = args.phaseId
    val loopId = args.loopId
    val identity = args.identity
    val intent = args.intent
    val subtaskName = request.goalContinuation?.subtaskName?.trim()?.takeIf(String::isNotBlank)
    if (subtaskName == null && request.goalContinuation != null) {
      runCatching {
        diagnostics.warning(
          FeatureTaskRuntimeCheckpointMessage.missingSubtaskNameRecord(identity.issueKey, identity.subtaskId),
        )
      }
    }
    return FeatureTaskRuntimeCheckpointMessage.build(
      issueKey = request.issueKey,
      subtaskName = subtaskName,
      metadata = FeatureTaskRuntimeCheckpointMetadata(
        phaseId = phaseId,
        loopId = loopId,
        generation = FeatureTaskRuntimeRunLoopCheckpoint.checkpointGeneration(state, loopId),
        branch = branch,
        intent = intent,
      ),
      identity = identity,
    )
  }

  internal fun subtaskCommitLedgerState(
    request: FeatureTaskRuntimeRunRequest,
    recorder: FeatureTaskRuntimePhaseRecorder,
    diagnostics: RuntimeDiagnostics,
    identity: FeatureTaskRuntimeSubtaskCommitIdentity,
  ): SubtaskCommitLedgerState {
    val read = runCatching {
      recorder.loadCheckpointIdentities(
        request.workflowId,
      )
    }
    val identities = read.getOrNull()
    val cause = read.exceptionOrNull()
      ?.let { "the checkpoint-identity store could not be read (${it.message ?: it::class.simpleName})" }
      ?: "no workflow row recorded any checkpoint identity for this run".takeIf { identities == null }
    if (cause != null) {
      val ledgerRecord = FeatureTaskRuntimeRunLoopCheckpoint.ledgerUnavailableRecord(identity, cause)
      runCatching { diagnostics.warning(ledgerRecord) }
      return SubtaskCommitLedgerState(commitSha = null, nextSequenceNumber = 0)
    }
    val recorded = requireNotNull(identities)
    return SubtaskCommitLedgerState(
      commitSha = recorded
        .filter { it.issueKey == identity.issueKey && it.subtaskId == identity.subtaskId }
        .maxByOrNull { it.sequenceNumber }
        ?.commitSha,
      nextSequenceNumber = (recorded.maxOfOrNull { it.sequenceNumber } ?: -1) + 1,
    )
  }

  internal fun ledgerUnavailableRecord(identity: FeatureTaskRuntimeSubtaskCommitIdentity, cause: String): String =
    "seam=FeatureTaskRuntimeRunLoop.subtaskCommitLedgerState value_used='no durable pointer, sequence 0' " +
      "value_expected=the recorded checkpoint-identity ledger for '${identity.issueKey}/${identity.subtaskId}' " +
      "cause=$cause"

  internal fun FeatureTaskRuntimeRunLoopContext.writeSubtaskCommit(
    branch: String,
    message: String,
    identity: FeatureTaskRuntimeSubtaskCommitIdentity,
  ): WorkflowGitOperationResult {
    val ledger = FeatureTaskRuntimeRunLoopCheckpoint.subtaskCommitLedgerState(request, recorder, diagnostics, identity)
    val headSha = phaseGates.gitOperations.headCommitSha(request.repoRoot)
      .takeIf { it is WorkflowGitOperationResult.Ok }?.value?.trim()?.takeIf(String::isNotBlank)
    val decision = FeatureTaskRuntimeSubtaskCommitResolver.decide(
      identity = identity,
      durableCommitSha = ledger.commitSha,
      head = FeatureTaskRuntimeSubtaskCommitHeadState(
        sha = headSha,
        commitMessage = if (ledger.commitSha == null && headSha != null) {
          headCommitMessageOrNull(
            request,
            phaseGates,
          )
        } else {
          null
        },
        isUnpushed = branchHasUnpushedCommits(request, phaseGates, branch),
      ),
      sequenceNumber = ledger.nextSequenceNumber,
    )
    return phaseGates.gitOperations.writeSubtaskCommitPreservingHistory(
      SubtaskCommitPreservationRequest(
        repoRoot = request.repoRoot,
        decision = decision,
        identity = identity,
        message = message,
        allowUnchangedIndex = false,
        record = { record -> runCatching { diagnostics.warning(record) } },
      ),
    )
  }

  internal fun headCommitMessageOrNull(
    request: FeatureTaskRuntimeRunRequest,
    phaseGates: FeatureTaskRuntimePhaseGates,
  ): String? = phaseGates.gitOperations.headCommitMessage(request.repoRoot)
    .takeIf { it is WorkflowGitOperationResult.Ok }?.value

  internal fun branchHasUnpushedCommits(
    request: FeatureTaskRuntimeRunRequest,
    phaseGates: FeatureTaskRuntimePhaseGates,
    branch: String,
  ): Boolean {
    val unpushed = phaseGates.gitOperations.localBranchHasUnpushedCommits(request.repoRoot, branch)
    return unpushed is WorkflowGitOperationResult.Ok &&
      unpushed.value.orEmpty().trim().equals("true", ignoreCase = true)
  }

  internal fun withIndexRestoreOutcome(
    request: FeatureTaskRuntimeRunRequest,
    phaseGates: FeatureTaskRuntimePhaseGates,
    error: String,
    ownedPaths: List<String>,
    snapshot: String,
  ): String {
    val restored = phaseGates.gitOperations.restoreIndexState(request.repoRoot, ownedPaths, snapshot)
    return if (restored is WorkflowGitOperationResult.Ok) {
      "$error; the pre-checkpoint index was restored and the working tree is unchanged"
    } else {
      "$error; the pre-checkpoint index could NOT be restored (${restored.error}) — inspect " +
        "`git status` before committing anything yourself"
    }
  }

  internal fun checkpointGeneration(state: FeatureTaskRuntimeRunState, loopId: String?): Int = loopId?.let {
    state.edgeIterationCount(it)
  } ?: 0

  internal fun FeatureTaskRuntimeRunLoopContext.recordCheckpointIdentity(args: RecordCheckpointIdentityArgs): Boolean {
    val precedingPhaseId = args.precedingPhaseId
    val branch = args.branch
    val loopId = args.loopId
    val ownedPaths = args.ownedPaths
    val parentSha = args.parentSha
    val commitSha = args.commitSha
    val blockedReason = args.blockedReason
    val recorded = runCatching {
      recorder.appendCheckpointIdentity(
        AppendCheckpointIdentityArgs(
          workflowId = request.workflowId,
          issueKey = request.issueKey,
          subtaskId = request.goalContinuation?.subtaskId?.toString()
            ?: FEATURE_TASK_RUNTIME_STANDALONE_SUBTASK_ID,
          branch = branch,
          phaseId = precedingPhaseId,
          loopId = loopId,
          generation = checkpointGeneration(state, loopId),
          parentSha = parentSha,
          ownedPaths = ownedPaths,
          commitSha = commitSha,
        ),
      )
    }
    return if (recorded.getOrDefault(false)) {
      true
    } else {
      with(FeatureTaskRuntimeRunLoopCheckpoint) {
        this@recordCheckpointIdentity.blockCheckpoint(
          precedingPhaseId,
          branch,
          "checkpoint commit '$commitSha' was created but its durable identity record could not be " +
            "written (${recorded.exceptionOrNull()?.message ?: "the workflow row was absent"}), so the " +
            "commit cannot be attributed to this workflow's authority boundary",
          blockedReason,
        )
      }
    }
  }

  internal fun remediationCheckpointBlockedReasonFor(): (String, String) -> String =
    { branch, error -> FeatureTaskRuntimeRunLoopPlanningBranch.remediationCheckpointBlockedReason(branch, error) }

  internal fun FeatureTaskRuntimeRunLoopContext.blockCheckpoint(
    precedingPhaseId: String,
    branch: String,
    error: String,
    blockedReason: (
      String,
      String,
    ) -> String,
  ): Boolean {
    FeatureTaskRuntimeRunLoopPlanningBranch.blockAt(
      request,
      state,
      session,
      precedingPhaseId,
      blockedReason(
        branch,
        error,
      ),
    )
    return false
  }

  internal fun matchingBackwardEdge(
    transitions: FeatureTaskRuntimeTransitionDeclaration,
    phaseId: String,
    verdict: FeatureTaskRuntimeVerdict,
  ): FeatureTaskRuntimeBackwardEdge? =
    transitions.backwardEdges.firstOrNull { it.fromPhaseId == phaseId && it.triggeringVerdict == verdict }
}
