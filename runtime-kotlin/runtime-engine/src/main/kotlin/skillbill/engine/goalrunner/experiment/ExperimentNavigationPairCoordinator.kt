package skillbill.engine.goalrunner.experiment

import me.tatarka.inject.annotations.Inject
import skillbill.application.decomposition.branchName
import skillbill.contracts.JsonCodec
import skillbill.contracts.experiment.EXPERIMENT_PAIR_CONTRACT_VERSION
import skillbill.contracts.experiment.ExperimentPairPayloadKeys
import skillbill.contracts.experiment.ExperimentTelemetryPayloadKeys
import skillbill.contracts.telemetry.TelemetryMeasurementAvailability
import skillbill.engine.experiment.navigation.NavigationHiddenLabelScore
import skillbill.engine.experiment.navigation.NavigationHiddenLabelScorer
import skillbill.engine.experiment.observation.ExperimentObservationMeasurement
import skillbill.engine.experiment.observation.ExperimentObservationRecordRequest
import skillbill.engine.experiment.observation.ExperimentObservationRecorder
import skillbill.engine.experiment.telemetry.ExperimentTelemetryRecorder
import skillbill.engine.goalrunner.status.completed
import skillbill.engine.work.resolveRepositoryIdentity
import skillbill.error.shellcontent.ExperimentIsolationCapabilityRefusalError
import skillbill.error.shellcontent.ExperimentNavigationRevisionError
import skillbill.error.shellcontent.ExperimentNavigationSpecError
import skillbill.experiment.model.ExperimentArmId
import skillbill.experiment.model.ExperimentExecutionMode
import skillbill.goalrunner.terminalStatus
import skillbill.ports.experiment.measurement.ExperimentArmMeasurement
import skillbill.ports.experiment.measurement.ExperimentArmMeasurementPort
import skillbill.ports.experiment.measurement.ExperimentMeasuredValue
import skillbill.ports.experiment.navigation.ExperimentNavigationRunPort
import skillbill.ports.experiment.navigation.ExperimentNavigationSessionRequest
import skillbill.ports.experiment.navigation.ExperimentNavigationSessionResult
import skillbill.ports.experiment.navigation.ExperimentNavigationSessionRunnerPort
import skillbill.ports.experiment.navigation.ExperimentNavigationTerminalOutcome
import skillbill.ports.experiment.navigation.model.ExperimentNavigationRunRequest
import skillbill.ports.experiment.pair.ExperimentPairOwnerPort
import skillbill.ports.experiment.pair.ExperimentPairPayload
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
import kotlin.io.path.readBytes
import kotlin.time.Duration.Companion.minutes

data class ExperimentNavigationPairSource(
  val name: String,
  val repoRoot: Path,
  val revision: String,
  val specBytes: ByteArray,
  val criteria: List<String>,
)

data class ExperimentNavigationPairEvaluation(
  val hiddenLabels: Map<String, Set<String>> = emptyMap(),
  val annotationsExhaustive: Boolean = false,
)

data class ExperimentNavigationPairRequest(
  val source: ExperimentNavigationPairSource,
  val pairId: String? = null,
  val evaluation: ExperimentNavigationPairEvaluation = ExperimentNavigationPairEvaluation(),
)

private data class PreparedNavigationPair(
  val request: ExperimentNavigationPairRequest,
  val pairId: String,
  val resolvedRevision: String,
  val repositoryIdentity: String,
  val specBytes: ByteArray,
  val criteria: List<String>,
  val persisted: ExperimentPairPersistedState?,
)

@Inject
class ExperimentNavigationPairCoordinator(
  private val selectionPort: ExperimentSelectionPort,
  private val sessionRunner: ExperimentNavigationSessionRunnerPort,
  private val gitOperations: WorkflowGitOperations,
  private val pairOwner: ExperimentPairOwnerPort,
  private val measurementPort: ExperimentArmMeasurementPort?,
  private val telemetryRecorder: ExperimentTelemetryRecorder?,
  private val clock: Clock,
) : ExperimentNavigationRunPort {
  override fun acceptanceCriteria(specText: String): List<String> = parseNavigationAcceptanceCriteria(specText)

  override fun run(request: ExperimentNavigationRunRequest): String {
    val specPath = request.specPath
    val specBytes = specPath.readBytes()
    if (specBytes.isEmpty()) {
      throw ExperimentNavigationSpecError(specPath.toString(), "spec file is empty")
    }
    val criteria = acceptanceCriteria(specBytes.decodeToString())
    if (criteria.isEmpty() || criteria.any(String::isBlank)) {
      throw ExperimentNavigationSpecError(
        specPath.toString(),
        "the governed acceptance criteria section is missing or empty",
      )
    }
    return run(
      ExperimentNavigationPairRequest(
        source =
          ExperimentNavigationPairSource(
            name = request.name,
            repoRoot = request.repoRoot,
            revision = request.revision,
            specBytes = specBytes,
            criteria = criteria,
          ),
      ),
    )
  }

  fun run(request: ExperimentNavigationPairRequest): String {
    val resolvedPairId = request.pairId ?: UUID.randomUUID().toString()
    validateBeforeLease(
      request = request.copy(pairId = resolvedPairId),
    )
    val leaseToken = UUID.randomUUID().toString()
    if (!pairOwner.acquireLease(resolvedPairId, leaseToken, clock.millis(), 30.minutes.inWholeMilliseconds)) {
      throw ExperimentIsolationCapabilityRefusalError(
        "Experiment pair $resolvedPairId is already owned by another runtime.",
      )
    }
    return try {
      runPair(request.copy(pairId = resolvedPairId))
    } finally {
      pairOwner.releaseLease(resolvedPairId, leaseToken)
    }
  }

  private fun validateBeforeLease(request: ExperimentNavigationPairRequest) {
    val pairId = requireNotNull(request.pairId)
    val source = request.source
    validateSpec(source)
    val persisted = pairOwner.load(pairId)
    val selection =
      selectionPort.resolveForLaunch(
        repoRoot = source.repoRoot,
        parameter = source.name,
        mode = ExperimentExecutionMode.NAVIGATION,
        savedSelection = persisted?.selectedNames,
      )
    validateSelection(source, selection.normalizedNames)
    val resolvedRevision = resolveRevision(source)
    val repositoryIdentity = resolveRepositoryIdentity(source)
    verifyPersistedInputs(
      persisted = persisted,
      repositoryIdentity = repositoryIdentity,
      revision = resolvedRevision,
      specBytes = source.specBytes,
    )
  }

  private fun runPair(request: ExperimentNavigationPairRequest): String {
    val prepared = preparePair(request)
    recordCompletedArmObservations(prepared)
    runPendingArms(prepared)
    telemetryRecorder?.record(
      pairId = prepared.pairId,
      cohort = ExperimentExecutionMode.NAVIGATION.wireValue,
      metrics =
        mapOf(
          ExperimentTelemetryPayloadKeys.PAIR_STATUS to
            pairOwner.load(prepared.pairId)
              ?.pairPayload?.get(ExperimentPairPayloadKeys.PAIR_STATUS),
          ExperimentTelemetryPayloadKeys.ARM_COUNT to
            pairOwner.load(prepared.pairId)?.pairPayload?.get(ExperimentPairPayloadKeys.ARM_OUTCOMES)
              .let { (it as? List<*>)?.size ?: 0 },
          ExperimentTelemetryPayloadKeys.DELIVERED_FEATURE_COUNT to 0,
        ),
    )
    return prepared.pairId
  }

  private fun preparePair(request: ExperimentNavigationPairRequest): PreparedNavigationPair {
    val pairId = requireNotNull(request.pairId)
    val source = request.source
    validateSpec(source)
    val persisted = pairOwner.load(pairId)
    val selection =
      selectionPort.resolveForLaunch(
        source.repoRoot,
        source.name,
        ExperimentExecutionMode.NAVIGATION,
        persisted?.selectedNames,
      )
    validateSelection(source, selection.normalizedNames)
    val resolvedRevision = resolveRevision(source)
    val repositoryIdentity = resolveRepositoryIdentity(source)
    verifyPersistedInputs(persisted, repositoryIdentity, resolvedRevision, source.specBytes)
    if (persisted == null) saveNewPair(request, pairId, selection.normalizedNames, repositoryIdentity, resolvedRevision)
    return PreparedNavigationPair(
      request = request,
      pairId = pairId,
      resolvedRevision = resolvedRevision,
      repositoryIdentity = repositoryIdentity,
      specBytes = source.specBytes,
      criteria = source.criteria,
      persisted = persisted,
    )
  }

  private fun validateSpec(source: ExperimentNavigationPairSource) {
    if (source.specBytes.isEmpty() || source.criteria.isEmpty() || source.criteria.any(String::isBlank)) {
      throw ExperimentNavigationSpecError(source.repoRoot.toString(), "at least one acceptance criterion is required")
    }
  }

  private fun validateSelection(
    source: ExperimentNavigationPairSource,
    names: List<String>,
  ) {
    if (names != listOf(source.name)) {
      throw ExperimentNavigationSpecError(
        source.repoRoot.toString(),
        "the requested navigation descriptor did not resolve to exactly '${source.name}'",
      )
    }
  }

  private fun resolveRevision(source: ExperimentNavigationPairSource): String =
    when (val result = gitOperations.resolveCommit(source.repoRoot, source.revision)) {
      is WorkflowGitOperationResult.Ok -> result.value.trim().takeIf(String::isNotBlank)
      is WorkflowGitOperationResult.Failed -> null
    } ?: throw ExperimentNavigationRevisionError(source.revision, "the revision could not be resolved")

  private fun resolveRepositoryIdentity(source: ExperimentNavigationPairSource): String =
    when (val result = gitOperations.repositoryFingerprint(source.repoRoot)) {
      is WorkflowGitOperationResult.Ok -> result.value.trim().takeIf(String::isNotBlank)
      is WorkflowGitOperationResult.Failed -> null
    } ?: throw ExperimentNavigationRevisionError(source.revision, "the repository identity could not be captured")

  private fun saveNewPair(
    request: ExperimentNavigationPairRequest,
    pairId: String,
    selectedNames: List<String>,
    repositoryIdentity: String,
    revision: String,
  ) {
    pairOwner.save(
      ExperimentPairPersistedState(
        pairId = pairId,
        executionMode = ExperimentExecutionMode.NAVIGATION,
        selectedNames = selectedNames,
        armOrder = listOf(ExperimentArmId.CONTROL, ExperimentArmId.TREATMENT),
        randomSeed = pairId,
        pairPayload =
          ExperimentPairPayload(
            navigationPairPayload(
              pairId,
              request.source.name,
              repositoryIdentity,
              revision,
              request.source.specBytes,
            ),
          ),
      ),
    )
  }

  private fun recordCompletedArmObservations(prepared: PreparedNavigationPair) {
    completedArms(prepared.persisted).forEach { arm ->
      val outcome = armOutcome(pairOwner.load(prepared.pairId)?.pairPayload?.toMap(), arm) ?: return@forEach
      recordArmObservation(
        pairId = prepared.pairId,
        arm = arm,
        workflowId =
          outcome[ExperimentPairPayloadKeys.WORKFLOW_ID]?.toString()
            ?: "${prepared.pairId}:${arm.wireValue}",
      )
    }
  }

  private fun completedArms(persisted: ExperimentPairPersistedState?): Set<ExperimentArmId> =
    persisted?.pairPayload
      ?.get(ExperimentPairPayloadKeys.ARM_OUTCOMES)
      ?.let { it as? List<*> }
      ?.filterIsInstance<Map<*, *>>()
      ?.filter { it[ExperimentPairPayloadKeys.TERMINAL_STATUS] == "completed" }
      ?.mapNotNull { it[ExperimentPairPayloadKeys.ARM_ID]?.toString()?.let(ExperimentArmId::fromWire) }
      ?.toSet()
      .orEmpty()

  private fun runPendingArms(prepared: PreparedNavigationPair) {
    val completed = completedArms(prepared.persisted)
    for (arm in listOf(ExperimentArmId.CONTROL, ExperimentArmId.TREATMENT).filterNot(completed::contains)) {
      val result = runArm(prepared, arm)
      if (result.outcome == ExperimentNavigationTerminalOutcome.CANCELLED) break
    }
  }

  private fun runArm(
    prepared: PreparedNavigationPair,
    arm: ExperimentArmId,
  ): ExperimentNavigationSessionResult {
    verifyCurrentInputs(prepared.request.source.repoRoot, prepared.resolvedRevision, prepared.repositoryIdentity)
    pairOwner.save(
      updateNavigationLifecycle(
        payload = pairOwner.load(prepared.pairId)?.pairPayload?.toMap().orEmpty(),
        pairId = prepared.pairId,
        arm = arm,
        terminalStatus = "running",
      ),
    )
    val result = executeArm(prepared, arm)
    pairOwner.save(
      updateOutcome(
        pairOwner.load(prepared.pairId)?.pairPayload?.toMap().orEmpty(),
        prepared.pairId,
        arm,
        result,
        NavigationHiddenLabelScorer.score(
          acceptanceCriteria = prepared.criteria,
          hiddenLabels = prepared.request.evaluation.hiddenLabels,
          reads = result.readReceipts,
          deliveredPaths = result.deliveredPaths,
          annotationsExhaustive = prepared.request.evaluation.annotationsExhaustive,
        ),
      ),
    )
    recordArmObservation(
      pairId = prepared.pairId,
      arm = arm,
      workflowId = "${prepared.pairId}:${arm.wireValue}",
      result = result,
    )
    return result
  }

  private fun executeArm(
    prepared: PreparedNavigationPair,
    arm: ExperimentArmId,
  ): ExperimentNavigationSessionResult {
    var snapshot: Path? = null
    val result =
      runCatching {
        snapshot =
          createNavigationSnapshot(
            prepared.request.source.repoRoot,
            prepared.pairId,
            arm,
            prepared.resolvedRevision,
          )
        sessionRunner.runSession(
          ExperimentNavigationSessionRequest(
            pairId = prepared.pairId,
            armId = arm.wireValue,
            repoRoot = requireNotNull(snapshot),
            revision = prepared.resolvedRevision,
            frozenSpecBytes = prepared.specBytes.copyOf(),
            acceptanceCriteria = prepared.criteria.toList(),
            treatmentEnabled = arm == ExperimentArmId.TREATMENT,
          ),
        )
      }
    val cleanupFailure =
      snapshot?.let { snapshotPath ->
        runCatching {
          removeNavigationSnapshot(prepared.request.source.repoRoot, snapshotPath)
        }.exceptionOrNull()
      }
    val primaryFailure = result.exceptionOrNull()
    if (primaryFailure != null) {
      cleanupFailure?.let(primaryFailure::addSuppressed)
      pairOwner.save(
        updateNavigationLifecycle(
          payload = pairOwner.load(prepared.pairId)?.pairPayload?.toMap().orEmpty(),
          pairId = prepared.pairId,
          arm = arm,
          terminalStatus = "failed",
          failureReason =
            primaryFailure.message.orEmpty()
              .ifBlank { primaryFailure::class.simpleName.orEmpty() },
        ),
      )
      throw primaryFailure
    }
    cleanupFailure?.let { throw it }
    return result.getOrThrow()
  }

  private fun verifyPersistedInputs(
    persisted: ExperimentPairPersistedState?,
    repositoryIdentity: String,
    revision: String,
    specBytes: ByteArray,
  ) {
    if (persisted == null) return
    val frozen =
      persisted.pairPayload[ExperimentPairPayloadKeys.FROZEN_INPUT_IDENTITY] as? Map<*, *>
        ?: throw ExperimentNavigationRevisionError(
          revision = revision,
          reason = "the persisted navigation pair has no frozen input identity",
        )
    val matches =
      frozen[ExperimentPairPayloadKeys.REPOSITORY_IDENTITY]?.toString() == repositoryIdentity &&
        frozen[ExperimentPairPayloadKeys.SOURCE_COMMIT_SHA]?.toString() == revision &&
        frozen[ExperimentPairPayloadKeys.SPEC_BUNDLE_HASH]?.toString() == sha256Hex(specBytes)
    if (!matches) {
      throw ExperimentNavigationRevisionError(
        revision = revision,
        reason = "the frozen repository revision, identity, or specification changed",
      )
    }
  }

  private fun verifyCurrentInputs(
    repoRoot: Path,
    revision: String,
    repositoryIdentity: String,
  ) {
    val currentRevision = currentRevision(repoRoot, revision)
    val currentRepositoryIdentity = currentRepositoryIdentity(repoRoot, revision)
    val status = currentWorktreeStatus(repoRoot, revision)
    val dirty = status.isNotBlank()
    if (currentRevision != revision || currentRepositoryIdentity != repositoryIdentity || dirty) {
      throw ExperimentNavigationRevisionError(
        revision = revision,
        reason =
          if (dirty) {
            "the source worktree is not clean after the navigation pair was frozen"
          } else {
            "the source changed after the navigation pair was frozen"
          },
      )
    }
  }

  private fun currentRevision(
    repoRoot: Path,
    revision: String,
  ): String =
    when (val result = gitOperations.resolveCommit(repoRoot, revision)) {
      is WorkflowGitOperationResult.Ok -> result.value.trim()
      is WorkflowGitOperationResult.Failed ->
        throw ExperimentNavigationRevisionError(revision, "the revision could not be revalidated")
    }

  private fun currentRepositoryIdentity(
    repoRoot: Path,
    revision: String,
  ): String =
    when (val result = gitOperations.repositoryFingerprint(repoRoot)) {
      is WorkflowGitOperationResult.Ok -> result.value.trim()
      is WorkflowGitOperationResult.Failed ->
        throw ExperimentNavigationRevisionError(revision, "the repository identity could not be revalidated")
    }

  private fun currentWorktreeStatus(
    repoRoot: Path,
    revision: String,
  ): String =
    when (val result = gitOperations.worktreeStatus(repoRoot)) {
      is WorkflowGitOperationResult.Ok -> result.value.orEmpty()
      is WorkflowGitOperationResult.Failed ->
        throw ExperimentNavigationRevisionError(revision, "the source worktree status could not be revalidated")
    }

  private fun navigationPairPayload(
    pairId: String,
    name: String,
    repositoryIdentity: String,
    revision: String,
    specBytes: ByteArray,
  ): Map<String, Any?> =
    linkedMapOf(
      ExperimentPairPayloadKeys.CONTRACT_VERSION to EXPERIMENT_PAIR_CONTRACT_VERSION,
      ExperimentPairPayloadKeys.PAIR_ID to pairId,
      ExperimentPairPayloadKeys.EXECUTION_MODE to ExperimentExecutionMode.NAVIGATION.wireValue,
      ExperimentPairPayloadKeys.SELECTED_EXPERIMENT_NAMES to listOf(name),
      ExperimentPairPayloadKeys.ARM_ORDER to
        listOf(
          ExperimentArmId.CONTROL.wireValue,
          ExperimentArmId.TREATMENT.wireValue,
        ),
      ExperimentPairPayloadKeys.RANDOM_SEED to pairId,
      ExperimentPairPayloadKeys.DELIVERY_ARM to "undecided",
      ExperimentPairPayloadKeys.PAIR_STATUS to "running",
      ExperimentPairPayloadKeys.DELIVERY_STATUS to "not_applicable",
      ExperimentPairPayloadKeys.FROZEN_INPUT_IDENTITY to
        mapOf(
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
    outcomes +=
      mapOf(
        ExperimentPairPayloadKeys.ARM_ID to arm.wireValue,
        ExperimentPairPayloadKeys.TERMINAL_STATUS to terminalStatus(result.outcome),
        ExperimentPairPayloadKeys.DELIVERED_PATHS to result.deliveredPaths,
        ExperimentPairPayloadKeys.SHORTLISTED_PATHS to result.shortlistedPaths,
        ExperimentPairPayloadKeys.READ_RECEIPTS to
          result.readReceipts.map { receipt ->
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
    val updated = payload.toMap().toMutableMap()
    updated[ExperimentPairPayloadKeys.ARM_OUTCOMES] = outcomes
    updated[ExperimentPairPayloadKeys.PAIR_STATUS] =
      when {
        result.outcome == ExperimentNavigationTerminalOutcome.CANCELLED -> "cancelled"
        outcomes.any { it[ExperimentPairPayloadKeys.TERMINAL_STATUS] == "failed" } -> "failed"
        outcomes.size == 2 -> "completed"
        else -> "running"
      }
    return ExperimentPairPersistedState(
      pairId = pairId,
      executionMode = ExperimentExecutionMode.NAVIGATION,
      selectedNames =
        (updated[ExperimentPairPayloadKeys.SELECTED_EXPERIMENT_NAMES] as? List<*>)
          ?.map { it.toString() }.orEmpty(),
      armOrder = listOf(ExperimentArmId.CONTROL, ExperimentArmId.TREATMENT),
      randomSeed = updated[ExperimentPairPayloadKeys.RANDOM_SEED].toString(),
      pairPayload = ExperimentPairPayload(updated),
    )
  }

  private fun terminalStatus(outcome: ExperimentNavigationTerminalOutcome): String =
    when (outcome) {
      ExperimentNavigationTerminalOutcome.SEARCH_COMPLETED -> "completed"
      ExperimentNavigationTerminalOutcome.BUDGET_EXHAUSTED,
      ExperimentNavigationTerminalOutcome.INSUFFICIENT_EVIDENCE,
      ExperimentNavigationTerminalOutcome.FAILED,
      -> "failed"
      ExperimentNavigationTerminalOutcome.CANCELLED -> "skipped"
    }

  private fun createNavigationSnapshot(
    repoRoot: Path,
    pairId: String,
    arm: ExperimentArmId,
    revision: String,
  ): Path {
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

  private fun removeNavigationSnapshot(
    repoRoot: Path,
    snapshot: Path,
  ) {
    gitOperations.linkedWorktreeOperations.removeLinkedWorktree(
      LinkedWorktreeRemoveRequest(repositoryRoot = repoRoot, worktreePath = snapshot),
    )
    snapshot.parent?.let { parent -> Files.deleteIfExists(parent) }
  }

  private fun navigationMeasurements(
    result: ExperimentNavigationSessionResult?,
    provider: ExperimentArmMeasurement,
  ): List<ExperimentObservationMeasurement> =
    buildList {
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
      ExperimentObservationRecordRequest(
        pairId = pairId,
        armId = arm.wireValue,
        workflowId = workflowId,
        phaseId = "navigation",
        attempt = 1,
        recordedAt = Instant.now(clock).toString(),
        measurements = navigationMeasurements(result, measurements),
      ),
    )
  }

  private fun unavailableMeasurement(): ExperimentArmMeasurement =
    ExperimentArmMeasurement(
      setupCost = unavailableValue("provider setup cost was not recorded"),
      usage = unavailableValue("provider usage was not recorded"),
      cost = unavailableValue("provider cost was not recorded"),
    )

  private fun unavailableValue(reason: String): ExperimentMeasuredValue =
    ExperimentMeasuredValue(
      availability = TelemetryMeasurementAvailability.UNAVAILABLE_INCOMPLETE.wireValue,
      reason = reason,
    )

  private fun armOutcome(
    payload: Map<String, Any?>?,
    arm: ExperimentArmId,
  ): Map<String, Any?>? =
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
    val outcomes =
      (payload[ExperimentPairPayloadKeys.ARM_OUTCOMES] as? List<*>)
        ?.filterIsInstance<Map<*, *>>()
        ?.map { it.entries.associate { entry -> entry.key.toString() to entry.value } }
        ?.filterNot { it[ExperimentPairPayloadKeys.ARM_ID] == arm.wireValue }
        ?.toMutableList()
        ?: mutableListOf()
    outcomes +=
      mapOf(
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
      selectedNames =
        (updated[ExperimentPairPayloadKeys.SELECTED_EXPERIMENT_NAMES] as? List<*>)
          ?.map { it.toString() }.orEmpty(),
      armOrder = listOf(ExperimentArmId.CONTROL, ExperimentArmId.TREATMENT),
      randomSeed = updated[ExperimentPairPayloadKeys.RANDOM_SEED]?.toString().orEmpty(),
      pairPayload = ExperimentPairPayload(updated),
    )
  }

  private fun safePath(value: String): String = value.replace(Regex("[^A-Za-z0-9._-]"), "-")
}

private fun ExperimentPairPayload.toMap(): Map<String, Any?> =
  JsonCodec.anyToStringAnyMap(
    JsonCodec.jsonElementToValue(requireNotNull(JsonCodec.parseObjectOrNull(toJson()))),
  ) ?: error("Experiment pair payload must decode to an object.")

fun parseNavigationAcceptanceCriteria(specText: String): List<String> =
  GovernedSpecSectionParser.parseListSection(specText) {
    it.startsWith(ACCEPTANCE_CRITERIA_PREFIX)
  }
