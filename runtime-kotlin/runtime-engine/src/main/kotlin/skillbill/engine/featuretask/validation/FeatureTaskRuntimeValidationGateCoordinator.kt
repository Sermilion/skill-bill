package skillbill.engine.featuretask.validation
import me.tatarka.inject.annotations.Inject
import skillbill.contracts.JsonCodec
import skillbill.contracts.SharedPayloadKeys
import skillbill.contracts.workflow.featuretask.FEATURE_TASK_RUNTIME_CONTRACT_VERSION
import skillbill.contracts.workflow.identity.evidence.ValidationEvidencePayloadKeys
import skillbill.engine.featuretask.model.phase.ValidationFindingSetProjection
import skillbill.engine.featuretask.persist.workflowArtifactEntryMap
import skillbill.engine.featuretask.phase.record.FeatureTaskRuntimePhaseRecorder
import skillbill.engine.featuretask.validation.model.ValidationGateAgentRepairResult
import skillbill.engine.featuretask.validation.model.ValidationGateCycleRequest
import skillbill.engine.featuretask.validation.model.ValidationGateCycleResult
import skillbill.engine.featuretask.validation.model.ValidationGateCycleTerminalOutcome
import skillbill.engine.featuretask.validation.model.ValidationGateProgressStore
import skillbill.error.shellcontent.InvalidFeatureTaskRuntimeValidationEvidenceSchemaError
import skillbill.workflow.taskruntime.artifact.asWorkflowArtifactEntry
import skillbill.workflow.taskruntime.model.handoff.task.FeatureTaskRuntimePhaseOutput
import skillbill.workflow.taskruntime.model.phase.FeatureTaskRuntimeFailureDisposition
import skillbill.workflow.taskruntime.model.validation.FeatureTaskRuntimeValidationCommandResult
import skillbill.workflow.taskruntime.model.validation.FeatureTaskRuntimeValidationEvidence
import skillbill.workflow.taskruntime.model.validation.FeatureTaskRuntimeValidationGateExecutionEvidence
import skillbill.workflow.taskruntime.model.validation.FeatureTaskRuntimeValidationGateProgress
import skillbill.workflow.taskruntime.model.validation.FeatureTaskRuntimeValidationGateRunRecord
import skillbill.workflow.taskruntime.model.validation.FeatureTaskRuntimeVerdict
import skillbill.workflow.taskruntime.phase.task.FeatureTaskRuntimePhaseWorkflowDefinition
private const val VALIDATE_PHASE_STATUS_COMPLETED = "completed"

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
class FeatureTaskRuntimeValidationGateCoordinator {
  fun execute(cycle: ValidationGateCycleRequest): ValidationGateCycleResult = when (
    val result = cycle.agentRepairLauncher.launch(ValidationFindingSetProjection(emptyList()), 1, null)
  ) {
    is ValidationGateAgentRepairResult.Paused -> ValidationGateCycleResult.Terminal(
      ValidationGateCycleTerminalOutcome.Paused(result.reason),
    )
    is ValidationGateAgentRepairResult.Completed -> ValidationGateCycleResult.Terminal(
      ValidationGateCycleTerminalOutcome.Completed(result.output),
    )
    is ValidationGateAgentRepairResult.Blocked -> terminalBlockedResult(
      result.reason,
      failureDisposition = result.failureDisposition,
    )
  }

  companion object {
    const val ABSENT_VALIDATION_GATE_REASON: String =
      "No installed platform pack declares validation_gate."

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
            SharedPayloadKeys.VALUE to "The runtime confirmed the pack validation gate passed.",
            ValidationEvidencePayloadKeys.VALIDATION_PASSED to true,
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
