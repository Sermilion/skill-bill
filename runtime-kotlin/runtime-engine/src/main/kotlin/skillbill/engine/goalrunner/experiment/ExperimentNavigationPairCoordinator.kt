package skillbill.engine.goalrunner.experiment
import me.tatarka.inject.annotations.Inject
import skillbill.contracts.experiment.EXPERIMENT_PAIR_CONTRACT_VERSION
import skillbill.contracts.experiment.ExperimentPairPayloadKeys
import skillbill.contracts.experiment.ExperimentTelemetryPayloadKeys
import skillbill.contracts.telemetry.TelemetryMeasurementAvailability
import skillbill.engine.experiment.navigation.NavigationHiddenLabelScore
import skillbill.engine.experiment.navigation.NavigationHiddenLabelScorer
import skillbill.engine.experiment.observation.ExperimentObservationMeasurement
import skillbill.engine.experiment.observation.ExperimentObservationRecorder
import skillbill.engine.experiment.telemetry.ExperimentTelemetryRecorder
import skillbill.error.shellcontent.ExperimentIsolationCapabilityRefusalError
import skillbill.error.shellcontent.ExperimentNavigationRevisionError
import skillbill.error.shellcontent.ExperimentNavigationSpecError
import skillbill.experiment.model.ExperimentArmId
import skillbill.experiment.model.ExperimentExecutionMode
import skillbill.ports.experiment.measurement.ExperimentArmMeasurement
import skillbill.ports.experiment.measurement.ExperimentArmMeasurementPort
import skillbill.ports.experiment.measurement.ExperimentMeasuredValue
import skillbill.ports.experiment.navigation.ExperimentNavigationSessionRequest
import skillbill.ports.experiment.navigation.ExperimentNavigationSessionResult
import skillbill.ports.experiment.navigation.ExperimentNavigationSessionRunnerPort
import skillbill.ports.experiment.navigation.ExperimentNavigationTerminalOutcome
import skillbill.ports.experiment.pair.ExperimentPairOwnerPort
import skillbill.ports.experiment.pair.ExperimentPairPersistedState
import skillbill.ports.experiment.selection.ExperimentSelectionPort
import skillbill.ports.workflow.gitops.WorkflowGitOperations
import skillbill.ports.workflow.gitops.model.WorkflowGitOperationResult
import skillbill.ports.workflow.gitops.repositoryFingerprint
import skillbill.ports.workflow.gitops.worktree.LinkedWorktreeAddRequest
import skillbill.ports.workflow.gitops.worktree.LinkedWorktreeRemoveRequest
import skillbill.review.spec.GovernedSpecSectionParser
import skillbill.review.spec.GovernedSpecSectionParser.ACCEPTANCE_CRITERIA_PREFIX
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.time.Clock
import java.time.Instant
import java.util.UUID
import kotlin.time.Duration.Companion.minutes

@Inject
class ExperimentNavigationPairCoordinator(
  private val selectionPort: ExperimentSelectionPort,
  private val sessionRunner: ExperimentNavigationSessionRunnerPort,
  private val gitOperations: WorkflowGitOperations,
  private val pairOwner: ExperimentPairOwnerPort,
  private val measurementPort: ExperimentArmMeasurementPort? = null,
  private val telemetryRecorder: ExperimentTelemetryRecorder? = null,
  private val clock: Clock = Clock.systemUTC(),
) {
  fun run(
    name: String,
    repoRoot: Path,
    revision: String,
    specBytes: ByteArray,
    criteria: List<String>,
    pairId: String? = null,
    hiddenLabels: Map<String, Set<String>> = emptyMap(),
    annotationsExhaustive: Boolean = false,
  ): String {
    val resolvedPairId = pairId ?: UUID.randomUUID().toString()
    validateBeforeLease(
      pairId = resolvedPairId,
      repoRoot = repoRoot,
      name = name,
      revision = revision,
      specBytes = specBytes,
      criteria = criteria,
    )
    val leaseToken = UUID.randomUUID().toString()
    if (!pairOwner.acquireLease(resolvedPairId, leaseToken, clock.millis(), 30.minutes.inWholeMilliseconds)) {
      throw ExperimentIsolationCapabilityRefusalError(
        "Experiment pair $resolvedPairId is already owned by another runtime.",
      )
    }
    return try {
      runPair(
        name = name,
        repoRoot = repoRoot,
        revision = revision,
        specBytes = specBytes,
        criteria = criteria,
        pairId = resolvedPairId,
        hiddenLabels = hiddenLabels,
        annotationsExhaustive = annotationsExhaustive,
      )
    } finally {
      pairOwner.releaseLease(resolvedPairId, leaseToken)
    }
  }

  private fun validateBeforeLease(
    pairId: String,
    repoRoot: Path,
    name: String,
    revision: String,
    specBytes: ByteArray,
    criteria: List<String>,
  ) {
    if (specBytes.isEmpty()) {
      throw ExperimentNavigationSpecError(repoRoot.toString(), "the spec is empty")
    }
    if (criteria.isEmpty() || criteria.any(String::isBlank)) {
      throw ExperimentNavigationSpecError(repoRoot.toString(), "at least one acceptance criterion is required")
    }
    val persisted = pairOwner.load(pairId)
    val selection = selectionPort.resolveForLaunch(
      repoRoot = repoRoot,
      parameter = name,
      mode = ExperimentExecutionMode.NAVIGATION,
      savedSelection = persisted?.selectedNames,
    )
    if (selection.normalizedNames != listOf(name)) {
      throw ExperimentNavigationSpecError(
        repoRoot.toString(),
        "the requested navigation descriptor did not resolve to exactly '$name'",
      )
    }
    val resolvedRevision = when (val result = gitOperations.resolveCommit(repoRoot, revision)) {
      is WorkflowGitOperationResult.Ok -> result.value.trim().takeIf(String::isNotBlank)
      is WorkflowGitOperationResult.Failed -> null
    } ?: throw ExperimentNavigationRevisionError(
      revision = revision,
      reason = "the revision could not be resolved",
    )
    val repositoryIdentity = when (val result = gitOperations.repositoryFingerprint(repoRoot)) {
      is WorkflowGitOperationResult.Ok -> result.value.trim().takeIf(String::isNotBlank)
      is WorkflowGitOperationResult.Failed -> null
    } ?: throw ExperimentNavigationRevisionError(
      revision = revision,
      reason = "the repository identity could not be captured",
    )
    verifyPersistedInputs(
      persisted = persisted,
      repositoryIdentity = repositoryIdentity,
      revision = resolvedRevision,
      specBytes = specBytes,
    )
  }

  private fun runPair(
    name: String,
    repoRoot: Path,
    revision: String,
    specBytes: ByteArray,
    criteria: List<String>,
    pairId: String,
    hiddenLabels: Map<String, Set<String>>,
    annotationsExhaustive: Boolean,
  ): String {
    if (specBytes.isEmpty()) {
      throw ExperimentNavigationSpecError(repoRoot.toString(), "the spec is empty")
    }
    if (criteria.isEmpty() || criteria.any(String::isBlank)) {
      throw ExperimentNavigationSpecError(repoRoot.toString(), "at least one acceptance criterion is required")
    }
    val resolvedPairId = pairId
    val persisted = pairOwner.load(resolvedPairId)
    val selection = selectionPort.resolveForLaunch(
      repoRoot,
      name,
      ExperimentExecutionMode.NAVIGATION,
      persisted?.selectedNames,
    )
    if (selection.normalizedNames != listOf(name)) {
      throw ExperimentNavigationSpecError(
        repoRoot.toString(),
        "the requested navigation descriptor did not resolve to exactly '$name'",
      )
    }
    val resolvedRevision = when (val result = gitOperations.resolveCommit(repoRoot, revision)) {
      is WorkflowGitOperationResult.Ok -> result.value.trim().takeIf(String::isNotBlank)
      is WorkflowGitOperationResult.Failed -> null
    } ?: throw ExperimentNavigationRevisionError(
      revision = revision,
      reason = "the revision could not be resolved",
    )
    val repositoryIdentity = when (val result = gitOperations.repositoryFingerprint(repoRoot)) {
      is WorkflowGitOperationResult.Ok -> result.value.trim().takeIf(String::isNotBlank)
      is WorkflowGitOperationResult.Failed -> null
    } ?: throw ExperimentNavigationRevisionError(
      revision = revision,
      reason = "the repository identity could not be captured",
    )
    verifyPersistedInputs(
      persisted = persisted,
      repositoryIdentity = repositoryIdentity,
      revision = resolvedRevision,
      specBytes = specBytes,
    )
    if (persisted == null) {
      pairOwner.save(
        ExperimentPairPersistedState(
          pairId = resolvedPairId,
          executionMode = ExperimentExecutionMode.NAVIGATION,
          selectedNames = selection.normalizedNames,
          armOrder = listOf(ExperimentArmId.CONTROL, ExperimentArmId.TREATMENT),
          randomSeed = resolvedPairId,
          pairPayload = navigationPairPayload(
            resolvedPairId,
            name,
            repositoryIdentity,
            resolvedRevision,
            specBytes,
          ),
        ),
      )
    }
    val completedArms = persisted?.pairPayload
      ?.get(ExperimentPairPayloadKeys.ARM_OUTCOMES)
      ?.let { it as? List<*> }
      ?.filterIsInstance<Map<*, *>>()
      ?.filter { it[ExperimentPairPayloadKeys.TERMINAL_STATUS] == "completed" }
      ?.mapNotNull { it[ExperimentPairPayloadKeys.ARM_ID]?.toString()?.let(ExperimentArmId::fromWire) }
      ?.toSet()
      .orEmpty()
    completedArms.forEach { arm ->
      val outcome = armOutcome(pairOwner.load(resolvedPairId)?.pairPayload, arm) ?: return@forEach
      recordArmObservation(
        pairId = resolvedPairId,
        arm = arm,
        workflowId = outcome[ExperimentPairPayloadKeys.WORKFLOW_ID]?.toString()
          ?: "$resolvedPairId:${arm.wireValue}",
      )
    }
    for (arm in listOf(ExperimentArmId.CONTROL, ExperimentArmId.TREATMENT).filterNot(completedArms::contains)) {
      verifyCurrentInputs(repoRoot, resolvedRevision, repositoryIdentity)
      pairOwner.save(
        updateNavigationLifecycle(
          payload = pairOwner.load(resolvedPairId)?.pairPayload.orEmpty(),
          pairId = resolvedPairId,
          arm = arm,
          terminalStatus = "running",
        ),
      )
      var snapshot: Path? = null
      var primaryFailure: Throwable? = null
      val result = try {
        snapshot = createNavigationSnapshot(repoRoot, resolvedPairId, arm, resolvedRevision)
        sessionRunner.runSession(
          ExperimentNavigationSessionRequest(
            pairId = resolvedPairId,
            armId = arm.wireValue,
            repoRoot = snapshot,
            revision = resolvedRevision,
            frozenSpecBytes = specBytes.copyOf(),
            acceptanceCriteria = criteria.toList(),
            treatmentEnabled = arm == ExperimentArmId.TREATMENT,
          ),
        )
      } catch (failure: Throwable) {
        primaryFailure = failure
        pairOwner.save(
          updateNavigationLifecycle(
            payload = pairOwner.load(resolvedPairId)?.pairPayload.orEmpty(),
            pairId = resolvedPairId,
            arm = arm,
            terminalStatus = "failed",
            failureReason = failure.message.orEmpty().ifBlank { failure::class.simpleName.orEmpty() },
          ),
        )
        throw failure
      } finally {
        snapshot?.let { snapshotPath ->
          try {
            removeNavigationSnapshot(repoRoot, snapshotPath)
          } catch (cleanupFailure: Throwable) {
            primaryFailure?.addSuppressed(cleanupFailure) ?: throw cleanupFailure
          }
        }
      }
      pairOwner.save(
        updateOutcome(
          pairOwner.load(resolvedPairId)?.pairPayload.orEmpty(),
          resolvedPairId,
          arm,
          result,
          NavigationHiddenLabelScorer.score(
            acceptanceCriteria = criteria,
            hiddenLabels = hiddenLabels,
            reads = result.readReceipts,
            deliveredPaths = result.deliveredPaths,
            annotationsExhaustive = annotationsExhaustive,
          ),
        ),
      )
      val workflowId = "$resolvedPairId:${arm.wireValue}"
      recordArmObservation(
        pairId = resolvedPairId,
        arm = arm,
        workflowId = workflowId,
        result = result,
      )
      if (result.outcome == ExperimentNavigationTerminalOutcome.CANCELLED) break
    }
    telemetryRecorder?.record(
      pairId = resolvedPairId,
      cohort = ExperimentExecutionMode.NAVIGATION.wireValue,
      metrics = mapOf(
        ExperimentTelemetryPayloadKeys.PAIR_STATUS to pairOwner.load(resolvedPairId)
          ?.pairPayload?.get(ExperimentPairPayloadKeys.PAIR_STATUS),
        ExperimentTelemetryPayloadKeys.ARM_COUNT to
          pairOwner.load(resolvedPairId)?.pairPayload?.get(ExperimentPairPayloadKeys.ARM_OUTCOMES)
            .let { (it as? List<*>)?.size ?: 0 },
        ExperimentTelemetryPayloadKeys.DELIVERED_FEATURE_COUNT to 0,
      ),
    )
    return resolvedPairId
  }

  private fun verifyPersistedInputs(
    persisted: ExperimentPairPersistedState?,
    repositoryIdentity: String,
    revision: String,
    specBytes: ByteArray,
  ) {
    if (persisted == null) return
    val frozen = persisted.pairPayload[ExperimentPairPayloadKeys.FROZEN_INPUT_IDENTITY] as? Map<*, *>
      ?: throw ExperimentNavigationRevisionError(
        revision = revision,
        reason = "the persisted navigation pair has no frozen input identity",
      )
    val matches = frozen[ExperimentPairPayloadKeys.REPOSITORY_IDENTITY]?.toString() == repositoryIdentity &&
      frozen[ExperimentPairPayloadKeys.SOURCE_COMMIT_SHA]?.toString() == revision &&
      frozen[ExperimentPairPayloadKeys.SPEC_BUNDLE_HASH]?.toString() == sha256Hex(specBytes)
    if (!matches) {
      throw ExperimentNavigationRevisionError(
        revision = revision,
        reason = "the frozen repository revision, identity, or specification changed",
      )
    }
  }

  private fun verifyCurrentInputs(repoRoot: Path, revision: String, repositoryIdentity: String) {
    val currentRevision = when (val result = gitOperations.resolveCommit(repoRoot, revision)) {
      is WorkflowGitOperationResult.Ok -> result.value.trim()
      is WorkflowGitOperationResult.Failed ->
        throw ExperimentNavigationRevisionError(
          revision = revision,
          reason = "the revision could not be revalidated",
        )
    }
    val currentRepositoryIdentity = when (val result = gitOperations.repositoryFingerprint(repoRoot)) {
      is WorkflowGitOperationResult.Ok -> result.value.trim()
      is WorkflowGitOperationResult.Failed ->
        throw ExperimentNavigationRevisionError(
          revision = revision,
          reason = "the repository identity could not be revalidated",
        )
    }
    val status = when (val result = gitOperations.worktreeStatus(repoRoot)) {
      is WorkflowGitOperationResult.Ok -> result.value.orEmpty()
      is WorkflowGitOperationResult.Failed ->
        throw ExperimentNavigationRevisionError(
          revision = revision,
          reason = "the source worktree status could not be revalidated",
        )
    }
    val dirty = status.isNotBlank()
    if (currentRevision != revision || currentRepositoryIdentity != repositoryIdentity || dirty) {
      throw ExperimentNavigationRevisionError(
        revision = revision,
        reason = if (dirty) {
          "the source worktree is not clean after the navigation pair was frozen"
        } else {
          "the source changed after the navigation pair was frozen"
        },
      )
    }
  }

  private fun navigationPairPayload(
    pairId: String,
    name: String,
    repositoryIdentity: String,
    revision: String,
    specBytes: ByteArray,
  ): Map<String, Any?> = linkedMapOf(
    ExperimentPairPayloadKeys.CONTRACT_VERSION to EXPERIMENT_PAIR_CONTRACT_VERSION,
    ExperimentPairPayloadKeys.PAIR_ID to pairId,
    ExperimentPairPayloadKeys.EXECUTION_MODE to ExperimentExecutionMode.NAVIGATION.wireValue,
    ExperimentPairPayloadKeys.SELECTED_EXPERIMENT_NAMES to listOf(name),
    ExperimentPairPayloadKeys.ARM_ORDER to listOf(
      ExperimentArmId.CONTROL.wireValue,
      ExperimentArmId.TREATMENT.wireValue,
    ),
    ExperimentPairPayloadKeys.RANDOM_SEED to pairId,
    ExperimentPairPayloadKeys.DELIVERY_ARM to "undecided",
    ExperimentPairPayloadKeys.PAIR_STATUS to "running",
    ExperimentPairPayloadKeys.DELIVERY_STATUS to "not_applicable",
    ExperimentPairPayloadKeys.FROZEN_INPUT_IDENTITY to mapOf(
      ExperimentPairPayloadKeys.REPOSITORY_IDENTITY to repositoryIdentity,
      ExperimentPairPayloadKeys.SOURCE_COMMIT_SHA to revision,
      ExperimentPairPayloadKeys.SOURCE_TREE_SHA to revision,
      ExperimentPairPayloadKeys.SPEC_BUNDLE_HASH to sha256Hex(specBytes),
      ExperimentPairPayloadKeys.EFFECTIVE_CONFIG_HASH to name,
      ExperimentPairPayloadKeys.SKILL_BILL_VERSION to "runtime",
    ),
    ExperimentPairPayloadKeys.ARM_OUTCOMES to emptyList<Any>(),
  )

  private fun sha256Hex(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { byte ->
      "%02x".format(byte)
    }

  private fun updateOutcome(
    payload: Map<String, Any?>,
    pairId: String,
    arm: ExperimentArmId,
    result: ExperimentNavigationSessionResult,
    hiddenScore: NavigationHiddenLabelScore,
  ): ExperimentPairPersistedState {
    val outcomes = (
      (payload[ExperimentPairPayloadKeys.ARM_OUTCOMES] as? List<*>)
        ?.filterIsInstance<Map<*, *>>()
        ?.filterNot { it[ExperimentPairPayloadKeys.ARM_ID] == arm.wireValue }
        ?.map { it.entries.associate { entry -> entry.key.toString() to entry.value } }
        ?.toMutableList() ?: mutableListOf()
      )
    outcomes += mapOf(
      ExperimentPairPayloadKeys.ARM_ID to arm.wireValue,
      ExperimentPairPayloadKeys.TERMINAL_STATUS to terminalStatus(result.outcome),
      ExperimentPairPayloadKeys.DELIVERED_PATHS to result.deliveredPaths,
      ExperimentPairPayloadKeys.SHORTLISTED_PATHS to result.shortlistedPaths,
      ExperimentPairPayloadKeys.READ_RECEIPTS to result.readReceipts.map { receipt ->
        mapOf(ExperimentPairPayloadKeys.PATH to receipt.path, ExperimentPairPayloadKeys.PURPOSE to receipt.purpose)
      },
      ExperimentPairPayloadKeys.ATTEMPT_COUNT to result.attemptCount,
      ExperimentPairPayloadKeys.LABELLED_CRITERIA to result.labelCoverage.labelledCriteria,
      ExperimentPairPayloadKeys.TOTAL_CRITERIA to result.labelCoverage.totalCriteria,
      ExperimentPairPayloadKeys.PRECISION_AVAILABLE to result.labelCoverage.precisionAvailable,
      ExperimentPairPayloadKeys.EXCLUDED_PATHS to result.excludedPaths,
      ExperimentPairPayloadKeys.RESTRICTED_BASELINE to result.restrictedBaseline,
      ExperimentPairPayloadKeys.DELIVERED_CRITERIA to hiddenScore.deliveredCriteria,
      ExperimentPairPayloadKeys.RELEVANT_READS to hiddenScore.relevantReads,
      ExperimentPairPayloadKeys.PRECISION to hiddenScore.precision,
    ).filterValues { it != null }
    val updated = payload.toMutableMap()
    updated[ExperimentPairPayloadKeys.ARM_OUTCOMES] = outcomes
    updated[ExperimentPairPayloadKeys.PAIR_STATUS] = when {
      result.outcome == ExperimentNavigationTerminalOutcome.CANCELLED -> "cancelled"
      outcomes.any { it[ExperimentPairPayloadKeys.TERMINAL_STATUS] == "failed" } -> "failed"
      outcomes.size == 2 -> "completed"
      else -> "running"
    }
    return ExperimentPairPersistedState(
      pairId = pairId,
      executionMode = ExperimentExecutionMode.NAVIGATION,
      selectedNames = (updated[ExperimentPairPayloadKeys.SELECTED_EXPERIMENT_NAMES] as? List<*>)
        ?.map { it.toString() }.orEmpty(),
      armOrder = listOf(ExperimentArmId.CONTROL, ExperimentArmId.TREATMENT),
      randomSeed = updated[ExperimentPairPayloadKeys.RANDOM_SEED].toString(),
      pairPayload = updated,
    )
  }

  private fun terminalStatus(outcome: ExperimentNavigationTerminalOutcome): String = when (outcome) {
    ExperimentNavigationTerminalOutcome.SEARCH_COMPLETED -> "completed"
    ExperimentNavigationTerminalOutcome.BUDGET_EXHAUSTED,
    ExperimentNavigationTerminalOutcome.INSUFFICIENT_EVIDENCE,
    ExperimentNavigationTerminalOutcome.FAILED,
    -> "failed"
    ExperimentNavigationTerminalOutcome.CANCELLED -> "skipped"
  }

  private fun createNavigationSnapshot(repoRoot: Path, pairId: String, arm: ExperimentArmId, revision: String): Path {
    val parent = Files.createTempDirectory("skill-bill-navigation-${safePath(pairId)}-${arm.wireValue}")
    val snapshot = parent.resolve("snapshot")
    gitOperations.linkedWorktreeOperations.addLinkedWorktree(
      LinkedWorktreeAddRequest(
        repositoryRoot = repoRoot,
        worktreePath = snapshot,
        branchName = "skill-bill/navigation/${safePath(pairId)}/${arm.wireValue}",
        baseRef = revision,
      ),
    )
    return snapshot
  }

  private fun removeNavigationSnapshot(repoRoot: Path, snapshot: Path) {
    gitOperations.linkedWorktreeOperations.removeLinkedWorktree(
      LinkedWorktreeRemoveRequest(repositoryRoot = repoRoot, worktreePath = snapshot),
    )
    snapshot.parent?.let { parent -> Files.deleteIfExists(parent) }
  }

  private fun navigationMeasurements(
    result: ExperimentNavigationSessionResult?,
    provider: ExperimentArmMeasurement,
  ): List<ExperimentObservationMeasurement> = buildList {
    result?.let {
      add(
        ExperimentObservationMeasurement(
          metricId = "attempt_count",
          quantity = it.attemptCount.toDouble(),
          availability = TelemetryMeasurementAvailability.MEASURED.wireValue,
        ),
      )
    }
    add(
      ExperimentObservationMeasurement(
        metricId = "usage",
        quantity = provider.usage.quantity,
        availability = provider.usage.availability,
        reason = provider.usage.reason,
      ),
    )
    add(
      ExperimentObservationMeasurement(
        metricId = "setup_cost",
        quantity = provider.setupCost.quantity,
        availability = provider.setupCost.availability,
        reason = provider.setupCost.reason,
      ),
    )
    add(
      ExperimentObservationMeasurement(
        metricId = "cost",
        quantity = provider.cost.quantity,
        availability = provider.cost.availability,
        reason = provider.cost.reason,
      ),
    )
  }

  private fun recordArmObservation(
    pairId: String,
    arm: ExperimentArmId,
    workflowId: String,
    result: ExperimentNavigationSessionResult? = null,
  ) {
    val measurements = measurementPort?.measure(pairId, arm.wireValue, workflowId) ?: unavailableMeasurement()
    ExperimentObservationRecorder(pairOwner).record(
      pairId = pairId,
      armId = arm.wireValue,
      workflowId = workflowId,
      phaseId = "navigation",
      attempt = 1,
      recordedAt = Instant.now(clock).toString(),
      measurements = navigationMeasurements(result, measurements),
    )
  }

  private fun unavailableMeasurement(): ExperimentArmMeasurement = ExperimentArmMeasurement(
    setupCost = unavailableValue("provider setup cost was not recorded"),
    usage = unavailableValue("provider usage was not recorded"),
    cost = unavailableValue("provider cost was not recorded"),
  )

  private fun unavailableValue(reason: String): ExperimentMeasuredValue = ExperimentMeasuredValue(
    availability = TelemetryMeasurementAvailability.UNAVAILABLE_INCOMPLETE.wireValue,
    reason = reason,
  )

  private fun armOutcome(payload: Map<String, Any?>?, arm: ExperimentArmId): Map<String, Any?>? =
    (payload?.get(ExperimentPairPayloadKeys.ARM_OUTCOMES) as? List<*>)
      ?.filterIsInstance<Map<*, *>>()
      ?.map { outcome -> outcome.entries.associate { entry -> entry.key.toString() to entry.value } }
      ?.firstOrNull { outcome -> outcome[ExperimentPairPayloadKeys.ARM_ID] == arm.wireValue }

  private fun updateNavigationLifecycle(
    payload: Map<String, Any?>,
    pairId: String,
    arm: ExperimentArmId,
    terminalStatus: String,
    failureReason: String? = null,
  ): ExperimentPairPersistedState {
    val outcomes = (payload[ExperimentPairPayloadKeys.ARM_OUTCOMES] as? List<*>)
      ?.filterIsInstance<Map<*, *>>()
      ?.map { it.entries.associate { entry -> entry.key.toString() to entry.value } }
      ?.filterNot { it[ExperimentPairPayloadKeys.ARM_ID] == arm.wireValue }
      ?.toMutableList()
      ?: mutableListOf()
    outcomes += mapOf(
      ExperimentPairPayloadKeys.ARM_ID to arm.wireValue,
      ExperimentPairPayloadKeys.TERMINAL_STATUS to terminalStatus,
      ExperimentPairPayloadKeys.FAILURE_REASON to failureReason,
    ).filterValues { it != null }
    val updated = payload.toMutableMap()
    updated[ExperimentPairPayloadKeys.ARM_OUTCOMES] = outcomes
    updated[ExperimentPairPayloadKeys.PAIR_STATUS] =
      if (terminalStatus == "failed") "failed" else "running"
    return ExperimentPairPersistedState(
      pairId = pairId,
      executionMode = ExperimentExecutionMode.NAVIGATION,
      selectedNames = (updated[ExperimentPairPayloadKeys.SELECTED_EXPERIMENT_NAMES] as? List<*>)
        ?.map { it.toString() }.orEmpty(),
      armOrder = listOf(ExperimentArmId.CONTROL, ExperimentArmId.TREATMENT),
      randomSeed = updated[ExperimentPairPayloadKeys.RANDOM_SEED]?.toString().orEmpty(),
      pairPayload = updated,
    )
  }

  private fun safePath(value: String): String = value.replace(Regex("[^A-Za-z0-9._-]"), "-")
}

fun parseNavigationAcceptanceCriteria(specText: String): List<String> =
  GovernedSpecSectionParser.parseListSection(specText) {
    it.startsWith(ACCEPTANCE_CRITERIA_PREFIX)
  }
