package skillbill.infrastructure.sqlite.featuretask.artifact

import skillbill.contracts.JsonCodec
import skillbill.workflow.taskruntime.artifact.asTelemetryPayload
import skillbill.workflow.taskruntime.artifact.asWorkflowArtifactEntry
import skillbill.workflow.taskruntime.artifact.decodePhaseOutputRepairEvidenceFromArtifact
import skillbill.workflow.taskruntime.artifact.goalContinuationFieldAdoptionFromWorkflowArtifacts
import skillbill.workflow.taskruntime.artifact.phaseLedgerFromWorkflowArtifacts
import skillbill.workflow.taskruntime.artifact.phaseRecordsFromWorkflowArtifacts
import skillbill.workflow.taskruntime.model.handoff.task.FeatureTaskRuntimeDiagnosticDegradationMeasurement
import skillbill.workflow.taskruntime.model.handoff.task.FeatureTaskRuntimeProjectionMeasurement
import skillbill.workflow.taskruntime.model.handoff.task.FeatureTaskRuntimeRejectionMeasurement
import skillbill.workflow.taskruntime.model.handoff.task.FeatureTaskRuntimeSharedEvidenceMeasurement
import skillbill.workflow.taskruntime.model.persistence.task.runtime.goal.FeatureTaskRuntimeGoalContinuationArtifact
import skillbill.workflow.taskruntime.model.phase.FeatureTaskRuntimePhaseLedgerEntry
import skillbill.workflow.taskruntime.model.phase.FeatureTaskRuntimePhaseOutputRepairEvidence
import skillbill.workflow.taskruntime.model.phase.FeatureTaskRuntimePhaseRecord
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
