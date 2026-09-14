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

fun FeatureTaskRuntimePhaseRecord.encodeWorkflowArtifact(): Any = asWorkflowArtifactEntry()

fun FeatureTaskRuntimePhaseLedgerEntry.encodeWorkflowArtifact(): Any = asWorkflowArtifactEntry()

fun FeatureTaskRuntimeGoalContinuationArtifact.encodeWorkflowArtifact(): Any = asWorkflowArtifactEntry()

fun FeatureTaskRuntimePhaseOutputRepairEvidence.encodeWorkflowArtifact(): Any = asWorkflowArtifactEntry()

fun decodePhaseRecords(artifacts: Any?) = phaseRecordsFromWorkflowArtifacts(artifacts)

fun decodePhaseLedger(artifacts: Any?) = phaseLedgerFromWorkflowArtifacts(artifacts)

fun decodeGoalContinuationFieldAdoption(artifacts: Any?) =
  goalContinuationFieldAdoptionFromWorkflowArtifacts(artifacts)

fun decodePhaseOutputRepairEvidence(json: String): FeatureTaskRuntimePhaseOutputRepairEvidence? =
  decodePhaseOutputRepairEvidenceFromArtifact(
    JsonCodec.parseObjectOrNull(json)?.let(JsonCodec::jsonElementToValue),
  )

fun FeatureTaskRuntimeProjectionMeasurement.encodeTelemetry(): Any = asTelemetryPayload()

fun FeatureTaskRuntimeSharedEvidenceMeasurement.encodeTelemetry(): Any = asTelemetryPayload()

fun FeatureTaskRuntimeRejectionMeasurement.encodeTelemetry(): Any = asTelemetryPayload()

fun FeatureTaskRuntimeDiagnosticDegradationMeasurement.encodeTelemetry(): Any = asTelemetryPayload()
