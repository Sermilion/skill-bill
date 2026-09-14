package skillbill.workflow.taskruntime

import skillbill.contracts.JsonCodec
import skillbill.error.InvalidFeatureTaskRuntimeRepairReceiptError
import skillbill.error.InvalidWorkflowStateSchemaError
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeCheckpointIdentity
import skillbill.workflow.taskruntime.FeatureTaskRuntimeHandoffFoundationValidator
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeDecomposeTerminal
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeDeliveredProjectionRecord
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeDiagnosticSignal
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeFindingVerificationDisposition
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeGoalContinuationArtifact
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeGoalContinuationFieldAdoption
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeGoalPlanningImport
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeHandoffEnvelope
import skillbill.workflow.taskruntime.model.NormalizedFeatureTaskRuntimePhaseOutput
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeHandoffProjection
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeImplementationAttempt
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimePhaseLedgerEntry
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimePhaseOutputRepairEvidence
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimePhaseRecord
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeQuarantineEntry
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeRepairLedgerEntry
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeRepairReceipt
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeRepairReceiptDecodeObservations
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeRepositoryCheckpoint
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeResolvedBranch
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeRunInvariants
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeValidationEvidence
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeValidationGateExecutionEvidence
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeValidationGateProgress
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeValidationGateRunRecord
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeVerificationBoundaryHeadingProvenance
import skillbill.workflow.taskruntime.model.PhaseHandoffProjectionDeclaration
import skillbill.workflow.taskruntime.model.featureTaskRuntimeCheckpointIdentitiesFromArtifact
import skillbill.workflow.taskruntime.model.featureTaskRuntimeCheckpointIdentitiesToArtifact
import skillbill.workflow.taskruntime.model.featureTaskRuntimeDecomposePlanOutcomeOrNull
import skillbill.workflow.taskruntime.model.featureTaskRuntimeDiagnosticSignalsFromWire
import skillbill.workflow.taskruntime.model.featureTaskRuntimeImplementationAttemptRecordToWire
import skillbill.workflow.taskruntime.model.featureTaskRuntimeImplementationAttemptsFromWire
import skillbill.workflow.taskruntime.model.featureTaskRuntimeIsDecompositionPackage
import skillbill.workflow.taskruntime.model.featureTaskRuntimeQuarantineEntriesFromWire
import skillbill.workflow.taskruntime.model.featureTaskRuntimeQuarantineRecordToWire
import skillbill.workflow.taskruntime.model.featureTaskRuntimeRunInvariantsFromArtifactMap
import skillbill.workflow.taskruntime.model.toArtifactMap
import skillbill.workflow.decomposition.model.SpecSource
import skillbill.workflow.taskruntime.phaseartifacts.decomposeTerminalFrom
import skillbill.workflow.taskruntime.phaseartifacts.goalContinuationFieldAdoptionFrom
import skillbill.workflow.taskruntime.phaseartifacts.operatorBlockRetryFrom
import skillbill.workflow.taskruntime.phaseartifacts.phaseLedgerFrom
import skillbill.workflow.taskruntime.phaseartifacts.phaseRecordsFrom
import skillbill.workflow.taskruntime.phaseartifacts.resolvedBranchFrom
import skillbill.workflow.taskruntime.phaseartifacts.reviewGenerationFrom
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeOperatorBlockRetry
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeProjectionMeasurement
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeRejectionMeasurement
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeSharedEvidenceMeasurement
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeDiagnosticDegradationMeasurement

private fun artifactsMap(artifacts: Any?): Map<String, Any?> =
  when {
    artifacts == null -> emptyMap()
    else -> JsonCodec.anyToStringAnyMap(artifacts)
      ?: throw InvalidWorkflowStateSchemaError(
        "Feature-task-runtime workflow artifacts must decode to an object.",
      )
  }

class FeatureTaskRuntimeWorkflowArtifactMap private constructor(
  private val delegate: Map<String, Any?>,
) : Map<String, Any?> by delegate {
  internal companion object {
    fun from(raw: Any?): FeatureTaskRuntimeWorkflowArtifactMap =
      FeatureTaskRuntimeWorkflowArtifactMap(
        JsonCodec.anyToStringAnyMap(raw)
          ?: throw InvalidWorkflowStateSchemaError(
            "Feature-task-runtime workflow artifact entry must decode to an object.",
          ),
      )
  }
}

fun phaseRecordsFromWorkflowArtifacts(artifacts: Any?): Map<String, FeatureTaskRuntimePhaseRecord> =
  phaseRecordsFrom(artifactsMap(artifacts))

fun resolvedBranchFromWorkflowArtifacts(artifacts: Any?): FeatureTaskRuntimeResolvedBranch? =
  resolvedBranchFrom(artifactsMap(artifacts))

fun reviewGenerationFromWorkflowArtifacts(artifacts: Any?): Int =
  reviewGenerationFrom(artifactsMap(artifacts))

fun operatorBlockRetryFromWorkflowArtifacts(artifacts: Any?): FeatureTaskRuntimeOperatorBlockRetry? =
  operatorBlockRetryFrom(artifactsMap(artifacts))

fun goalContinuationFieldAdoptionFromWorkflowArtifacts(artifacts: Any?): FeatureTaskRuntimeGoalContinuationFieldAdoption? =
  goalContinuationFieldAdoptionFrom(artifactsMap(artifacts))

fun decomposeTerminalFromWorkflowArtifacts(artifacts: Any?): FeatureTaskRuntimeDecomposeTerminal? =
  decomposeTerminalFrom(artifactsMap(artifacts))

fun phaseLedgerFromWorkflowArtifacts(artifacts: Any?): List<FeatureTaskRuntimePhaseLedgerEntry> =
  phaseLedgerFrom(artifactsMap(artifacts))

fun FeatureTaskRuntimeValidationGateProgress.asWorkflowArtifactEntry(): Any = toArtifactMap()

fun FeatureTaskRuntimeValidationGateRunRecord.asWorkflowArtifactEntry(): Any = toArtifactMap()

fun FeatureTaskRuntimeValidationGateRunRecord.presentationWireMap(): FeatureTaskRuntimeWorkflowArtifactMap =
  FeatureTaskRuntimeWorkflowArtifactMap.from(asWorkflowArtifactEntry())

fun FeatureTaskRuntimeRepairLedgerEntry.projectionWireMap(): FeatureTaskRuntimeWorkflowArtifactMap =
  FeatureTaskRuntimeWorkflowArtifactMap.from(toProjectionMap())

fun decodeValidationGateProgressFromArtifact(raw: Any?): FeatureTaskRuntimeValidationGateProgress? =
  JsonCodec.anyToStringAnyMap(raw)?.let(FeatureTaskRuntimeValidationGateProgress::fromArtifactMap)

fun FeatureTaskRuntimeValidationGateExecutionEvidence.asWorkflowArtifactEntry(repositoryCheckpoint: String): Any =
  toArtifactMap(repositoryCheckpoint)

fun decodeValidationGateExecutionEvidenceFromArtifact(
  raw: Any?,
  sourceLabel: String,
): FeatureTaskRuntimeValidationGateExecutionEvidence? =
  JsonCodec.anyToStringAnyMap(raw)?.let {
    FeatureTaskRuntimeValidationGateExecutionEvidence.fromArtifactMap(it, sourceLabel)
  }

fun FeatureTaskRuntimeValidationEvidence.asWorkflowArtifactEntry(): Any = toArtifactMap()

fun decodeValidationEvidenceFromArtifact(raw: Any?, sourceLabel: String): FeatureTaskRuntimeValidationEvidence? =
  JsonCodec.anyToStringAnyMap(raw)?.let { FeatureTaskRuntimeValidationEvidence.fromArtifactMap(it, sourceLabel) }

fun FeatureTaskRuntimeRepairReceipt.asWorkflowArtifactEntry(): Any = toArtifactMap()

fun decodeRepairReceiptFromArtifact(raw: Any?, sourceLabel: String): FeatureTaskRuntimeRepairReceipt? =
  JsonCodec.anyToStringAnyMap(raw)?.let { FeatureTaskRuntimeRepairReceipt.fromArtifactMap(it, sourceLabel) }

fun decodeRepairReceiptFromArtifact(
  raw: Any?,
  sourceLabel: String,
  observations: FeatureTaskRuntimeRepairReceiptDecodeObservations,
): FeatureTaskRuntimeRepairReceipt? =
  JsonCodec.anyToStringAnyMap(raw)?.let { FeatureTaskRuntimeRepairReceipt.fromArtifactMap(it, sourceLabel, observations) }

fun validateRepairReceiptWireEntries(raw: Any, path: String) {
  FeatureTaskRuntimeRepairReceipt.validateEntries(
    JsonCodec.anyToStringAnyMap(raw)
      ?: throw InvalidFeatureTaskRuntimeRepairReceiptError(
        fieldPath = path,
        reason = "must be an object.",
        payloadFreeReason = "$path must be an object.",
      ),
    path,
  )
}

fun List<FeatureTaskRuntimeQuarantineEntry>.asQuarantineWorkflowArtifactEntry(): Any =
  featureTaskRuntimeQuarantineRecordToWire(this)

fun decodeQuarantineEntriesFromArtifact(raw: Any?): List<FeatureTaskRuntimeQuarantineEntry> =
  featureTaskRuntimeQuarantineEntriesFromWire(raw)

fun List<FeatureTaskRuntimeCheckpointIdentity>.asCheckpointIdentitiesArtifactEntry(): Any =
  featureTaskRuntimeCheckpointIdentitiesToArtifact(this)

fun decodeCheckpointIdentitiesFromArtifact(raw: Any?): List<FeatureTaskRuntimeCheckpointIdentity> =
  featureTaskRuntimeCheckpointIdentitiesFromArtifact(raw)

fun FeatureTaskRuntimeHandoffEnvelope.asWorkflowArtifactEntry(): Any = toEnvelopeMap()

fun decodeHandoffEnvelopeFromArtifact(raw: Any?): FeatureTaskRuntimeHandoffEnvelope? =
  JsonCodec.anyToStringAnyMap(raw)?.let(FeatureTaskRuntimeHandoffEnvelope::fromEnvelopeMap)

fun FeatureTaskRuntimeHandoffProjection.asWorkflowArtifactEntry(): Any = toEnvelopeMap()

fun FeatureTaskRuntimeRepositoryCheckpoint.asWorkflowArtifactEntry(): Any = toEnvelopeMap()

fun FeatureTaskRuntimePhaseRecord.asWorkflowArtifactEntry(): Any = toArtifactMap()

fun decodePhaseRecordFromArtifact(raw: Any?): FeatureTaskRuntimePhaseRecord? =
  JsonCodec.anyToStringAnyMap(raw)?.let(FeatureTaskRuntimePhaseRecord::fromArtifactMap)

fun FeatureTaskRuntimePhaseLedgerEntry.asWorkflowArtifactEntry(): Any = toArtifactMap()

fun decodePhaseLedgerEntryFromArtifact(raw: Any?): FeatureTaskRuntimePhaseLedgerEntry? =
  JsonCodec.anyToStringAnyMap(raw)?.let(FeatureTaskRuntimePhaseLedgerEntry::fromArtifactMap)

fun FeatureTaskRuntimeImplementationAttempt.asWorkflowArtifactEntry(): Any = toArtifactMap()

fun decodeImplementationAttemptFromArtifact(raw: Any?): FeatureTaskRuntimeImplementationAttempt? =
  JsonCodec.anyToStringAnyMap(raw)?.let(FeatureTaskRuntimeImplementationAttempt::fromArtifactMap)

fun decodeImplementationAttemptsFromArtifact(raw: Any?): List<FeatureTaskRuntimeImplementationAttempt> =
  featureTaskRuntimeImplementationAttemptsFromWire(raw)

fun Any.toWorkflowArtifactMap(): FeatureTaskRuntimeWorkflowArtifactMap =
  FeatureTaskRuntimeWorkflowArtifactMap.from(this)

fun implementationAttemptRecordWorkflowArtifact(attempts: List<FeatureTaskRuntimeImplementationAttempt>): Any =
  featureTaskRuntimeImplementationAttemptRecordToWire(attempts)

fun FeatureTaskRuntimeRunInvariants.asWorkflowArtifactEntry(): Any = toArtifactMap()

fun decodeRunInvariantsFromArtifact(raw: Any?): FeatureTaskRuntimeRunInvariants? =
  JsonCodec.anyToStringAnyMap(raw)?.let { featureTaskRuntimeRunInvariantsFromArtifactMap(it) }

fun FeatureTaskRuntimeResolvedBranch.asWorkflowArtifactEntry(): Any = toArtifactMap()

fun decodeResolvedBranchFromArtifact(raw: Any?): FeatureTaskRuntimeResolvedBranch? =
  JsonCodec.anyToStringAnyMap(raw)?.let(FeatureTaskRuntimeResolvedBranch::fromArtifactMap)

fun FeatureTaskRuntimeDecomposeTerminal.asWorkflowArtifactEntry(): Any = toArtifactMap()

fun decodeDecomposeTerminalFromArtifact(raw: Any?): FeatureTaskRuntimeDecomposeTerminal? =
  JsonCodec.anyToStringAnyMap(raw)?.let(FeatureTaskRuntimeDecomposeTerminal::fromArtifactMap)

fun FeatureTaskRuntimeGoalContinuationArtifact.asWorkflowArtifactEntry(): Any = toArtifactMap()

fun decodeGoalContinuationArtifactFromArtifact(raw: Any?): FeatureTaskRuntimeGoalContinuationArtifact? =
  JsonCodec.anyToStringAnyMap(raw)?.let(FeatureTaskRuntimeGoalContinuationArtifact::fromArtifactMap)

fun FeatureTaskRuntimeGoalContinuationFieldAdoption.asWorkflowArtifactEntry(): Any = toArtifactMap()

fun FeatureTaskRuntimeGoalPlanningImport.asWorkflowArtifactEntry(): Any = toArtifactMap()

fun decodeGoalContinuationFieldAdoptionFromArtifact(raw: Any?): FeatureTaskRuntimeGoalContinuationFieldAdoption? =
  JsonCodec.anyToStringAnyMap(raw)?.let(FeatureTaskRuntimeGoalContinuationFieldAdoption::fromArtifactMap)

fun FeatureTaskRuntimeDeliveredProjectionRecord.asWorkflowArtifactEntry(): Any = toArtifactMap()

fun decodeDeliveredProjectionRecordFromArtifact(raw: Any?): FeatureTaskRuntimeDeliveredProjectionRecord? =
  JsonCodec.anyToStringAnyMap(raw)?.let(FeatureTaskRuntimeDeliveredProjectionRecord::fromArtifactMap)

fun FeatureTaskRuntimeFindingVerificationDisposition.asWorkflowArtifactEntry(): Any = toArtifactMap()

fun decodeFindingVerificationDispositionFromArtifact(
  raw: Any?,
  path: String = "finding_verification",
): FeatureTaskRuntimeFindingVerificationDisposition? =
  JsonCodec.anyToStringAnyMap(raw)?.let { FeatureTaskRuntimeFindingVerificationDisposition.fromArtifactMap(it, path) }

fun FeatureTaskRuntimeDiagnosticSignal.asWorkflowArtifactEntry(): Any = toArtifactMap()

fun decodeDiagnosticSignalsFromArtifact(raw: Any?): List<FeatureTaskRuntimeDiagnosticSignal> =
  featureTaskRuntimeDiagnosticSignalsFromWire(raw)

fun FeatureTaskRuntimePhaseOutputRepairEvidence.asWorkflowArtifactEntry(): Any = toArtifactMap()

fun decodePhaseOutputRepairEvidenceFromArtifact(raw: Any?): FeatureTaskRuntimePhaseOutputRepairEvidence? =
  JsonCodec.anyToStringAnyMap(raw)?.let(FeatureTaskRuntimePhaseOutputRepairEvidence::fromArtifactMap)

fun FeatureTaskRuntimeVerificationBoundaryHeadingProvenance.asWorkflowArtifactEntry(): Any = toArtifactMap()

fun decodeVerificationBoundaryHeadingProvenanceFromArtifact(
  raw: Any?,
  path: String,
): FeatureTaskRuntimeVerificationBoundaryHeadingProvenance? =
  JsonCodec.anyToStringAnyMap(raw)?.let {
    FeatureTaskRuntimeVerificationBoundaryHeadingProvenance.fromArtifactMap(it, path)
  }

fun PhaseHandoffProjectionDeclaration.asWorkflowArtifactEntry(): Any = toArtifactMap()

fun decodePhaseHandoffProjectionDeclarationFromArtifact(
  raw: Any?,
  foundationValidator: FeatureTaskRuntimeHandoffFoundationValidator,
): PhaseHandoffProjectionDeclaration? =
  JsonCodec.anyToStringAnyMap(raw)?.let { PhaseHandoffProjectionDeclaration.fromArtifactMap(it, foundationValidator) }

fun phaseOutputEnvelopeFromArtifact(raw: Any?): Any? = JsonCodec.anyToStringAnyMap(raw)

fun NormalizedFeatureTaskRuntimePhaseOutput.envelopeWireMap(): FeatureTaskRuntimeWorkflowArtifactMap =
  FeatureTaskRuntimeWorkflowArtifactMap.from(envelopePayload())

fun isDecompositionPackagePhaseOutput(phaseOutput: Any?): Boolean =
  JsonCodec.anyToStringAnyMap(phaseOutput)?.let { featureTaskRuntimeIsDecompositionPackage(it) } == true

fun decomposePlanOutcomeFromPhaseOutput(phaseOutput: Any?, specSource: SpecSource) =
  JsonCodec.anyToStringAnyMap(phaseOutput)?.let {
    featureTaskRuntimeDecomposePlanOutcomeOrNull(it, specSource)
  }

fun FeatureTaskRuntimeProjectionMeasurement.asTelemetryPayload(): Any = toTelemetryMap()

fun FeatureTaskRuntimeSharedEvidenceMeasurement.asTelemetryPayload(): Any = toTelemetryMap()

fun FeatureTaskRuntimeRejectionMeasurement.asTelemetryPayload(): Any = toTelemetryMap()

fun FeatureTaskRuntimeDiagnosticDegradationMeasurement.asTelemetryPayload(): Any = toTelemetryMap()
