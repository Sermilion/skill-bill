package skillbill.infrastructure.sqlite.featuretask.artifact

import skillbill.contracts.JsonCodec
import skillbill.workflow.taskruntime.asTelemetryPayload
import skillbill.workflow.taskruntime.asWorkflowArtifactEntry
import skillbill.workflow.taskruntime.decodePhaseOutputRepairEvidenceFromArtifact
import skillbill.workflow.taskruntime.goalContinuationFieldAdoptionFromWorkflowArtifacts
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeDiagnosticDegradationMeasurement
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeGoalContinuationArtifact
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimePhaseLedgerEntry
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimePhaseOutputRepairEvidence
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimePhaseRecord
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeProjectionMeasurement
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeRejectionMeasurement
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeSharedEvidenceMeasurement
import skillbill.workflow.taskruntime.phaseLedgerFromWorkflowArtifacts
import skillbill.workflow.taskruntime.phaseRecordsFromWorkflowArtifacts

internal fun FeatureTaskRuntimePhaseRecord.encodeWorkflowArtifact(): Any = asWorkflowArtifactEntry()

internal fun FeatureTaskRuntimePhaseLedgerEntry.encodeWorkflowArtifact(): Any = asWorkflowArtifactEntry()

internal fun FeatureTaskRuntimeGoalContinuationArtifact.encodeWorkflowArtifact(): Any = asWorkflowArtifactEntry()

internal fun FeatureTaskRuntimePhaseOutputRepairEvidence.encodeWorkflowArtifact(): Any = asWorkflowArtifactEntry()

internal fun decodePhaseRecords(artifacts: Any?) = phaseRecordsFromWorkflowArtifacts(artifacts)

internal fun decodePhaseLedger(artifacts: Any?) = phaseLedgerFromWorkflowArtifacts(artifacts)

internal fun decodeGoalContinuationFieldAdoption(artifacts: Any?) = goalContinuationFieldAdoptionFromWorkflowArtifacts(
  artifacts,
)

internal fun decodePhaseOutputRepairEvidence(json: String): FeatureTaskRuntimePhaseOutputRepairEvidence? =
  decodePhaseOutputRepairEvidenceFromArtifact(
    JsonCodec.parseObjectOrNull(json)?.let(JsonCodec::jsonElementToValue),
  )

internal fun FeatureTaskRuntimeProjectionMeasurement.encodeTelemetry(): Any = asTelemetryPayload()

internal fun FeatureTaskRuntimeSharedEvidenceMeasurement.encodeTelemetry(): Any = asTelemetryPayload()

internal fun FeatureTaskRuntimeRejectionMeasurement.encodeTelemetry(): Any = asTelemetryPayload()

internal fun FeatureTaskRuntimeDiagnosticDegradationMeasurement.encodeTelemetry(): Any = asTelemetryPayload()
