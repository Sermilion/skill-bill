package skillbill.workflow.taskruntime

import skillbill.contracts.JsonCodec
import skillbill.workflow.decomposition.model.SpecSource
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeDiagnosticDegradationMeasurement
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeDiagnosticSignal
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeFindingVerificationDisposition
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimePhaseOutputRepairEvidence
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeProjectionMeasurement
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeRejectionMeasurement
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeSharedEvidenceMeasurement
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeVerificationBoundaryHeadingProvenance
import skillbill.workflow.taskruntime.model.NormalizedFeatureTaskRuntimePhaseOutput
import skillbill.workflow.taskruntime.model.PhaseHandoffProjectionDeclaration
import skillbill.workflow.taskruntime.model.featureTaskRuntimeDecomposePlanOutcomeOrNull
import skillbill.workflow.taskruntime.model.featureTaskRuntimeDiagnosticSignalsFromWire
import skillbill.workflow.taskruntime.model.featureTaskRuntimeIsDecompositionPackage
import skillbill.workflow.taskruntime.model.toArtifactMap

fun FeatureTaskRuntimeFindingVerificationDisposition.asWorkflowArtifactEntry(): Any = toArtifactMap()

fun decodeFindingVerificationDispositionFromArtifact(
  raw: Any?,
  path: String = "finding_verification",
): FeatureTaskRuntimeFindingVerificationDisposition? =
  JsonCodec.anyToStringAnyMap(raw)?.let { FeatureTaskRuntimeFindingVerificationDisposition.fromArtifactMap(it, path) }

internal fun decodeFindingVerificationDispositionFromArtifact(
  raw: Map<String, Any?>,
  path: String = "finding_verification",
): FeatureTaskRuntimeFindingVerificationDisposition =
  FeatureTaskRuntimeFindingVerificationDisposition.fromArtifactMap(raw, path)

fun FeatureTaskRuntimeDiagnosticSignal.asWorkflowArtifactEntry(): Any = toArtifactMap()

fun decodeDiagnosticSignalsFromArtifact(raw: Any?): List<FeatureTaskRuntimeDiagnosticSignal> =
  featureTaskRuntimeDiagnosticSignalsFromWire(raw)

fun FeatureTaskRuntimePhaseOutputRepairEvidence.asWorkflowArtifactEntry(): Any = toArtifactMap()

fun decodePhaseOutputRepairEvidenceFromArtifact(raw: Any?): FeatureTaskRuntimePhaseOutputRepairEvidence? =
  JsonCodec.anyToStringAnyMap(raw)?.let(FeatureTaskRuntimePhaseOutputRepairEvidence::fromArtifactMap)

internal fun decodePhaseOutputRepairEvidenceFromArtifact(
  raw: Map<String, Any?>,
): FeatureTaskRuntimePhaseOutputRepairEvidence = FeatureTaskRuntimePhaseOutputRepairEvidence.fromArtifactMap(raw)

fun FeatureTaskRuntimeVerificationBoundaryHeadingProvenance.asWorkflowArtifactEntry(): Any = toArtifactMap()

fun decodeVerificationBoundaryHeadingProvenanceFromArtifact(
  raw: Any?,
  path: String,
): FeatureTaskRuntimeVerificationBoundaryHeadingProvenance? = JsonCodec.anyToStringAnyMap(raw)?.let {
  FeatureTaskRuntimeVerificationBoundaryHeadingProvenance.fromArtifactMap(it, path)
}

internal fun decodeVerificationBoundaryHeadingProvenanceFromArtifact(
  raw: Map<String, Any?>,
  path: String,
): FeatureTaskRuntimeVerificationBoundaryHeadingProvenance =
  FeatureTaskRuntimeVerificationBoundaryHeadingProvenance.fromArtifactMap(raw, path)

fun PhaseHandoffProjectionDeclaration.asWorkflowArtifactEntry(): Any = toArtifactMap()

fun decodePhaseHandoffProjectionDeclarationFromArtifact(
  raw: Any?,
  foundationValidator: FeatureTaskRuntimeWireArtifactValidator,
): PhaseHandoffProjectionDeclaration? =
  JsonCodec.anyToStringAnyMap(raw)?.let { PhaseHandoffProjectionDeclaration.fromArtifactMap(it, foundationValidator) }

internal fun decodePhaseHandoffProjectionDeclarationFromArtifact(
  raw: Map<String, Any?>,
  foundationValidator: FeatureTaskRuntimeWireArtifactValidator,
): PhaseHandoffProjectionDeclaration = PhaseHandoffProjectionDeclaration.fromArtifactMap(raw, foundationValidator)

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
