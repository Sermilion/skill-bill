package skillbill.workflow.taskruntime

import skillbill.contracts.JsonCodec
import skillbill.workflow.decomposition.model.SpecSource
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeDecomposeTerminal
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeDeliveredProjectionRecord
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeDiagnosticDegradationMeasurement
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeDiagnosticSignal
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeFindingVerificationDisposition
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeGoalContinuationArtifact
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeGoalContinuationFieldAdoption
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeGoalPlanningImport
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeImplementationAttempt
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimePhaseOutputRepairEvidence
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeProjectionMeasurement
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeRejectionMeasurement
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeResolvedBranch
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeRunInvariants
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeSharedEvidenceMeasurement
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeVerificationBoundaryHeadingProvenance
import skillbill.workflow.taskruntime.model.NormalizedFeatureTaskRuntimePhaseOutput
import skillbill.workflow.taskruntime.model.PhaseHandoffProjectionDeclaration
import skillbill.workflow.taskruntime.model.featureTaskRuntimeDecomposePlanOutcomeOrNull
import skillbill.workflow.taskruntime.model.featureTaskRuntimeDiagnosticSignalsFromWire
import skillbill.workflow.taskruntime.model.featureTaskRuntimeImplementationAttemptRecordToWire
import skillbill.workflow.taskruntime.model.featureTaskRuntimeImplementationAttemptsFromWire
import skillbill.workflow.taskruntime.model.featureTaskRuntimeIsDecompositionPackage
import skillbill.workflow.taskruntime.model.featureTaskRuntimeRunInvariantsFromArtifactMap
import skillbill.workflow.taskruntime.model.toArtifactMap

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
): FeatureTaskRuntimeVerificationBoundaryHeadingProvenance? = JsonCodec.anyToStringAnyMap(raw)?.let {
  FeatureTaskRuntimeVerificationBoundaryHeadingProvenance.fromArtifactMap(it, path)
}

fun PhaseHandoffProjectionDeclaration.asWorkflowArtifactEntry(): Any = toArtifactMap()

fun decodePhaseHandoffProjectionDeclarationFromArtifact(
  raw: Any?,
  foundationValidator: FeatureTaskRuntimeWireArtifactValidator,
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
