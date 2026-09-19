package skillbill.engine.featuretask.validation
import me.tatarka.inject.annotations.Inject
import skillbill.config.model.applyValidationGateGradleWrapper
import skillbill.contracts.JsonCodec
import skillbill.contracts.SharedPayloadKeys
import skillbill.contracts.workflow.featuretask.FEATURE_TASK_RUNTIME_CONTRACT_VERSION
import skillbill.contracts.workflow.identity.evidence.ValidationEvidencePayloadKeys
import skillbill.engine.featuretask.model.core.FeatureTaskRuntimeRunEvent
import skillbill.engine.featuretask.model.phase.ValidationFindingSetProjection
import skillbill.engine.featuretask.persist.workflowArtifactEntryMap
import skillbill.engine.featuretask.phase.record.FeatureTaskRuntimePhaseRecorder
import skillbill.engine.featuretask.runloop.observability.emitFeatureTaskRuntimeEventSafely
import skillbill.engine.featuretask.validation.model.ValidationGateCyclePhase
import skillbill.engine.featuretask.validation.model.ValidationGateCycleRequest
import skillbill.engine.featuretask.validation.model.ValidationGateCycleResult
import skillbill.engine.featuretask.validation.model.ValidationGateCycleTerminalOutcome
import skillbill.engine.featuretask.validation.model.ValidationGateProgressStore
import skillbill.engine.featuretask.validation.model.ValidationGateProgressWrite
import skillbill.engine.featuretask.validation.model.ValidationGateResolution
import skillbill.error.shellcontent.InvalidFeatureTaskRuntimeValidationEvidenceSchemaError
import skillbill.ports.config.RepoLocalConfigPort
import skillbill.ports.config.model.ReadRepoLocalConfigRequest
import skillbill.ports.diagnostics.RuntimeDiagnostics
import skillbill.ports.validation.ValidationGateRunner
import skillbill.ports.validation.model.ValidationGateFinding
import skillbill.ports.validation.model.ValidationGateFindingParseMode
import skillbill.ports.validation.model.ValidationGateRunRequest
import skillbill.ports.validation.model.ValidationGateRunResult
import skillbill.scaffold.model.ValidationGateDeclaration
import skillbill.workflow.taskruntime.artifact.asWorkflowArtifactEntry
import skillbill.workflow.taskruntime.model.handoff.task.FeatureTaskRuntimePhaseOutput
import skillbill.workflow.taskruntime.model.phase.FeatureTaskRuntimeFailureDisposition
import skillbill.workflow.taskruntime.model.validation.FeatureTaskRuntimeValidationCommandResult
import skillbill.workflow.taskruntime.model.validation.FeatureTaskRuntimeValidationEvidence
import skillbill.workflow.taskruntime.model.validation.FeatureTaskRuntimeValidationGateExecutionEvidence
import skillbill.workflow.taskruntime.model.validation.FeatureTaskRuntimeValidationGateProgress
import skillbill.workflow.taskruntime.model.validation.FeatureTaskRuntimeValidationGateRepairWindowPhase
import skillbill.workflow.taskruntime.model.validation.FeatureTaskRuntimeValidationGateRunRecord
import skillbill.workflow.taskruntime.model.validation.FeatureTaskRuntimeVerdict
import skillbill.workflow.taskruntime.model.validation.ValidationGateCacheMode
import skillbill.workflow.taskruntime.model.validation.ValidationGateRunOutcome
import skillbill.workflow.taskruntime.phase.task.FeatureTaskRuntimePhaseWorkflowDefinition
import skillbill.workflow.taskruntime.validation.unparseableGateFailureMessage
import java.nio.file.Path
private const val VALIDATE_PHASE_STATUS_COMPLETED = "completed"

private data class ValidationGateCycleState(
  val cycle: ValidationGateCycleRequest,
  val measurements: MutableList<FeatureTaskRuntimeValidationGateRunRecord>,
  val onGateRunCount: (Int) -> Unit,
)

class FeatureTaskRuntimeValidationGateProgressStore private constructor(
  private val recorder: FeatureTaskRuntimePhaseRecorder?,
  private val delegate: ValidationGateProgressStore?,
) : ValidationGateProgressStore {
  @Inject
  constructor(recorder: FeatureTaskRuntimePhaseRecorder) : this(recorder, null)

  internal constructor(delegate: ValidationGateProgressStore) : this(null, delegate)

  override fun persist(workflowId: String, progress: FeatureTaskRuntimeValidationGateProgress) {
    when {
      delegate != null -> delegate.persist(workflowId, progress)
      recorder != null -> recorder.persistValidationGateProgress(workflowId, progress)
      else -> error("FeatureTaskRuntimeValidationGateProgressStore has no backing store.")
    }
  }

  override fun load(workflowId: String): FeatureTaskRuntimeValidationGateProgress? = when {
    delegate != null -> delegate.load(workflowId)
    recorder != null -> recorder.loadValidationGateProgress(workflowId)
    else -> error("FeatureTaskRuntimeValidationGateProgressStore has no backing store.")
  }
}

@Inject
class FeatureTaskRuntimeValidationGateCoordinator(
  private val resolver: ValidationGateResolver,
  private val runner: ValidationGateRunner,
  private val progressStore: FeatureTaskRuntimeValidationGateProgressStore,
  private val repoLocalConfig: RepoLocalConfigPort,
  private val diagnostics: RuntimeDiagnostics,
) {
  internal fun requiredValidationCommand(
    repoRoot: Path,
    workflowId: String,
    declaration: ValidationGateDeclaration,
  ): String {
    val wrapper = repoLocalConfig
      .readRepoLocalConfig(ReadRepoLocalConfigRequest(repoRoot))
      .config
      .validationGate
      .gradleWrapper
    return requiredValidationGateCommand(declaration, wrapper, progressStore.load(workflowId))
  }

  fun execute(cycle: ValidationGateCycleRequest, onGateRunCount: (Int) -> Unit = {}): ValidationGateCycleResult {
    return when (val resolution = resolver.resolve(cycle.changedPaths)) {
      is ValidationGateResolution.Absent -> terminalBlockedResult(ABSENT_VALIDATION_GATE_REASON)
      is ValidationGateResolution.Incompatible -> terminalBlockedResult(resolution.reason)
      is ValidationGateResolution.Declared -> checkAndRepair(cycle, resolution.declaration, onGateRunCount)
    }
  }

  private fun checkAndRepair(
    cycle: ValidationGateCycleRequest,
    declaration: ValidationGateDeclaration,
    onGateRunCount: (Int) -> Unit,
  ): ValidationGateCycleResult {
    val loaded = progressStore.load(cycle.request.workflowId)
    val measurements = loaded?.gateRuns?.toMutableList() ?: mutableListOf()
    val state = ValidationGateCycleState(
      cycle = cycle,
      measurements = measurements,
      onGateRunCount = onGateRunCount,
    )
    val initialRepairsUsed = when (loaded?.repairWindowPhase) {
      FeatureTaskRuntimeValidationGateRepairWindowPhase.FINDINGS_OPEN ->
        operatorResumeRepairTurns(loaded.repairsUsed)
      else -> 0
    }
    return agentThenVerifyLoop(
      state = state,
      declaration = declaration,
      initialRepairsUsed = initialRepairsUsed,
      openFindingsHint = when (loaded?.repairWindowPhase) {
        FeatureTaskRuntimeValidationGateRepairWindowPhase.FINDINGS_OPEN ->
          decodePersistedFindings(loaded.completeFindings)
        else -> emptyList()
      },
    )
  }

  private fun agentThenVerifyLoop(
    state: ValidationGateCycleState,
    declaration: ValidationGateDeclaration,
    initialRepairsUsed: Int,
    openFindingsHint: List<ValidationGateFinding>,
  ): ValidationGateCycleResult {
    val cycle = state.cycle
    val measurements = state.measurements
    var repairsUsed = initialRepairsUsed
    var lastFindings = openFindingsHint
    while (true) {
      if (repairsUsed >= MAX_REPAIR_TURNS) {
        val projection = persistFindingsOpen(state, lastFindings, repairsUsed)
        return terminalBlockedResult(
          FINDINGS_REMAIN_AFTER_RESTARTS_REASON,
          remainingFindings = projection,
          measurements = measurements,
        )
      }
      persistFindingsOpen(state, lastFindings, repairsUsed)
      cycle.agentRepairLauncher.launch(
        ValidationFindingSetProjection(emptyList()),
        repairsUsed + 1,
        triagePlan = null,
      )
      repairsUsed++
      val gatePhase = ValidationGateCyclePhase.POST_REPAIR_VERIFY
      val verify = runGate(cycle, declaration, gatePhase)
      lastFindings = findingsForRepairFromResult(verify)
      recordGateProgress(
        state = state,
        result = verify,
        command = commandFor(cycle, declaration, gatePhase),
        write = ValidationGateProgressWrite(
          repairWindowPhase = repairWindowPhaseFor(lastFindings),
          remainingFindings = null,
          completeFindings = lastFindings,
          repairsUsed = repairsUsed,
          capturedTriagePlan = null,
        ),
      )
      if (lastFindings.isEmpty()) {
        return terminalCompletedResult(
          cycle.repositoryCheckpoint,
          measurements,
          commandFor(cycle, declaration, gatePhase),
        )
      }
    }
  }

  private fun persistFindingsOpen(
    state: ValidationGateCycleState,
    findings: List<ValidationGateFinding>,
    repairsUsed: Int,
  ): ValidationFindingSetProjection {
    val projection = ValidationFindingSetProjection(findings = findings)
    persistProgress(
      state = state,
      write = ValidationGateProgressWrite.findingsOpen(
        completeFindings = findings,
        repairsUsed = repairsUsed,
        capturedTriagePlan = null,
        remainingFindings = projection.takeIf { findings.isNotEmpty() },
      ),
    )
    return projection
  }

  private fun repairWindowPhaseFor(
    findings: List<ValidationGateFinding>,
  ): FeatureTaskRuntimeValidationGateRepairWindowPhase = if (findings.isEmpty()) {
    FeatureTaskRuntimeValidationGateRepairWindowPhase.NONE
  } else {
    FeatureTaskRuntimeValidationGateRepairWindowPhase.FINDINGS_OPEN
  }

  private fun runGate(
    cycle: ValidationGateCycleRequest,
    declaration: ValidationGateDeclaration,
    cyclePhase: ValidationGateCyclePhase,
  ): ValidationGateRunResult {
    val packArgv = validationGateArgv(declaration, cyclePhase)
    val cacheMode = when (cyclePhase) {
      ValidationGateCyclePhase.INITIAL_DISCOVERY -> ValidationGateCacheMode.CACHE_ELIGIBLE
      ValidationGateCyclePhase.POST_REPAIR_VERIFY -> ValidationGateCacheMode.FORCED_FULL
    }
    val terminalVerifying = cyclePhase == ValidationGateCyclePhase.POST_REPAIR_VERIFY
    val findingParseMode = ValidationGateFindingParseMode.COLLECT_ALL
    val gradleWrapper = repoLocalConfig
      .readRepoLocalConfig(ReadRepoLocalConfigRequest(cycle.repoRoot))
      .config
      .validationGate
      .gradleWrapper
    return runner.run(
      ValidationGateRunRequest(
        repoRoot = cycle.repoRoot,
        argv = applyValidationGateGradleWrapper(packArgv, gradleWrapper),
        cacheMode = cacheMode,
        declaration = declaration,
        terminalVerifying = terminalVerifying,
        findingParseMode = findingParseMode,
      ),
    )
  }

  private fun commandFor(
    cycle: ValidationGateCycleRequest,
    declaration: ValidationGateDeclaration,
    cyclePhase: ValidationGateCyclePhase,
  ): String {
    val wrapper = repoLocalConfig
      .readRepoLocalConfig(ReadRepoLocalConfigRequest(cycle.repoRoot))
      .config
      .validationGate
      .gradleWrapper
    return validationGateCommand(declaration, cyclePhase, wrapper)
  }

  private fun recordGateProgress(
    state: ValidationGateCycleState,
    result: ValidationGateRunResult,
    command: String,
    write: ValidationGateProgressWrite,
  ) {
    state.measurements += FeatureTaskRuntimeValidationGateRunRecord(
      durationMs = result.durationMs,
      outcome = result.outcome,
      cacheMode = result.cacheMode,
      executedWorkUnits = result.executedWorkUnits,
      executedChecks = result.executedCheckIdentities,
      command = command,
      exitCode = result.exitCode,
    )
    persistProgress(state = state, write = write)
  }

  private fun persistProgress(state: ValidationGateCycleState, write: ValidationGateProgressWrite) {
    val progress = FeatureTaskRuntimeValidationGateProgress(
      gateRunCount = state.measurements.size,
      gateRuns = state.measurements.toList(),
      remainingFindings = write.remainingFindings?.toHandoffMaps().orEmpty(),
      completeFindings = ValidationFindingSetProjection(findings = write.completeFindings).toHandoffMaps(),
      repairWindowPhase = write.repairWindowPhase,
      repairsUsed = write.repairsUsed,
      capturedTriagePlan = write.capturedTriagePlan,
    )
    progressStore.persist(state.cycle.request.workflowId, progress)
    emitFeatureTaskRuntimeEventSafely(
      diagnostics = diagnostics,
      seam = "ValidationGateProgress event-sink emission",
    ) {
      state.cycle.request.eventSink.emit(
        FeatureTaskRuntimeRunEvent.ValidationGateProgress(
          workflowId = state.cycle.request.workflowId,
          phaseId = FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_VALIDATE,
          gateRunCount = progress.gateRunCount,
        ),
      )
    }
    state.onGateRunCount(progress.gateRunCount)
  }

  companion object {
    const val MAX_REPAIR_TURNS: Int = 3
    const val FINDINGS_REMAIN_AFTER_RESTARTS_REASON: String =
      "Validation still has findings after 3 restarts."
    const val ABSENT_VALIDATION_GATE_REASON: String =
      "No installed platform pack declares validation_gate."

    private fun operatorResumeRepairTurns(repairsUsed: Int): Int =
      if (repairsUsed >= MAX_REPAIR_TURNS) 0 else repairsUsed

    fun unparseableGateFailureFinding(result: ValidationGateRunResult): ValidationGateFinding = ValidationGateFinding(
      module = "<validation-gate>",
      ruleOrTestId = "unparseable_gate_failure",
      message = unparseableGateFailureMessage(
        gateLabel = "Validation gate",
        outcome = result.outcome.wireValue,
        exitCode = result.exitCode,
        stdout = result.stdout,
      ),
      location = null,
    )

    fun runtimeOwnedValidationOutput(
      repositoryCheckpoint: String,
      measurements: List<FeatureTaskRuntimeValidationGateRunRecord>,
      requiredCommand: String,
    ): FeatureTaskRuntimePhaseOutput {
      val evidence = measurements.mapNotNull { measurement ->
        val command = measurement.command ?: return@mapNotNull null
        val exitCode = measurement.exitCode ?: return@mapNotNull null
        FeatureTaskRuntimeValidationCommandResult(command, exitCode)
      }
      if (evidence.isEmpty()) {
        throw InvalidFeatureTaskRuntimeValidationEvidenceSchemaError(
          "validate",
          "runtime-owned validation evidence has no command results.",
        )
      }
      FeatureTaskRuntimeValidationEvidence(evidence).requireSuccessfulCommand(requiredCommand, "validate")
      val gateExecutionEvidence = FeatureTaskRuntimeValidationGateExecutionEvidence.fromGateMeasurements(measurements)
      val validationResult = linkedMapOf<String, Any?>().apply {
        putAll(workflowArtifactEntryMap(gateExecutionEvidence.asWorkflowArtifactEntry(repositoryCheckpoint)))
        put(
          ValidationEvidencePayloadKeys.VALIDATION_EVIDENCE,
          FeatureTaskRuntimeValidationEvidence(evidence).asWorkflowArtifactEntry(),
        )
      }
      val payload = JsonCodec.mapToJsonString(
        mapOf(
          SharedPayloadKeys.CONTRACT_VERSION to FEATURE_TASK_RUNTIME_CONTRACT_VERSION,
          SharedPayloadKeys.PHASE_ID to FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_VALIDATE,
          SharedPayloadKeys.STATUS to VALIDATE_PHASE_STATUS_COMPLETED,
          SharedPayloadKeys.SUMMARY to "Validation satisfied by runtime-owned gate execution.",
          SharedPayloadKeys.VERDICT to FeatureTaskRuntimeVerdict.SATISFIED.wireValue,
          SharedPayloadKeys.PRODUCED_OUTPUTS to mapOf(
            ValidationEvidencePayloadKeys.VALIDATION_RESULT to validationResult,
          ),
        ),
      )
      return FeatureTaskRuntimePhaseOutput(
        phaseId = FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_VALIDATE,
        iteration = 1,
        payload = payload,
      )
    }
  }
}

private fun findingsForRepairFromResult(result: ValidationGateRunResult): List<ValidationGateFinding> =
  if (result.outcome == ValidationGateRunOutcome.PASSED && result.exitCode == 0) {
    emptyList()
  } else {
    result.findings.ifEmpty {
      listOf(FeatureTaskRuntimeValidationGateCoordinator.unparseableGateFailureFinding(result))
    }
  }

private fun decodePersistedFindings(raw: List<Map<String, String?>>): List<ValidationGateFinding> = raw.map { map ->
  ValidationGateFinding(
    module = map["module"] ?: "",
    ruleOrTestId = map["rule_or_test_id"] ?: "",
    message = map["message"] ?: "",
    location = map["location"],
  )
}

private fun terminalCompletedResult(
  repositoryCheckpoint: String,
  measurements: List<FeatureTaskRuntimeValidationGateRunRecord>,
  requiredCommand: String,
): ValidationGateCycleResult = ValidationGateCycleResult.Terminal(
  ValidationGateCycleTerminalOutcome.Completed(
    output = FeatureTaskRuntimeValidationGateCoordinator.runtimeOwnedValidationOutput(
      repositoryCheckpoint = repositoryCheckpoint,
      measurements = measurements,
      requiredCommand = requiredCommand,
    ),
  ),
)

private fun terminalBlockedResult(
  reason: String,
  remainingFindings: ValidationFindingSetProjection? = null,
  measurements: List<FeatureTaskRuntimeValidationGateRunRecord> = emptyList(),
  failureDisposition: FeatureTaskRuntimeFailureDisposition? = null,
): ValidationGateCycleResult = ValidationGateCycleResult.Terminal(
  ValidationGateCycleTerminalOutcome.Blocked(
    reason = reason,
    remainingFindings = remainingFindings,
    measurements = measurements,
    failureDisposition = failureDisposition,
  ),
)
