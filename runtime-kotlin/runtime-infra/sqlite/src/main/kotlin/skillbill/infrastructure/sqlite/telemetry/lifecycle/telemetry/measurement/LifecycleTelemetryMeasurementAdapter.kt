package skillbill.infrastructure.sqlite.telemetry.lifecycle.telemetry.measurement

import skillbill.contracts.JsonCodec
import skillbill.contracts.SharedPayloadKeys
import skillbill.contracts.review.ReviewVerificationSignalKeys
import skillbill.contracts.telemetry.SqliteLifecycleTelemetryMaterializationPayloadKeys
import skillbill.contracts.telemetry.TelemetryOutboxEvent
import skillbill.infrastructure.sqlite.telemetry.lifecycle.telemetry.emit.enqueueTelemetry
import skillbill.infrastructure.sqlite.telemetry.lifecycle.telemetry.emit.reviewStageDegradationExists
import skillbill.ports.telemetry.lifecycle.FeatureTaskRuntimeTelemetryMeasurementRepository
import skillbill.ports.telemetry.lifecycle.ReviewStageTelemetryMeasurementRepository
import skillbill.review.model.REVIEW_STAGE_DEGRADATION_CONTRACT_VERSION
import skillbill.review.model.ReviewStageDegradationMeasurement
import skillbill.workflow.taskruntime.artifact.asTelemetryPayload
import skillbill.workflow.taskruntime.model.handoff.task.FeatureTaskRuntimeDiagnosticDegradationMeasurement
import skillbill.workflow.taskruntime.model.handoff.task.FeatureTaskRuntimeProjectionMeasurement
import skillbill.workflow.taskruntime.model.handoff.task.FeatureTaskRuntimeRejectionMeasurement
import skillbill.workflow.taskruntime.model.handoff.task.FeatureTaskRuntimeSharedEvidenceMeasurement
import java.sql.Connection

internal class LifecycleTelemetryMeasurementAdapter(
  private val connection: Connection,
  private val runtimeVersion: String,
) : FeatureTaskRuntimeTelemetryMeasurementRepository,
  ReviewStageTelemetryMeasurementRepository {
  override fun featureTaskRuntimeProjectionMeasurement(record: FeatureTaskRuntimeProjectionMeasurement) {
    enqueueTelemetry(
      connection,
      runtimeVersion,
      TelemetryOutboxEvent.FEATURE_TASK_RUNTIME_PROJECTION_MEASUREMENT,
      JsonCodec.anyToStringAnyMap(record.asTelemetryPayload()) ?: emptyMap(),
    )
  }

  override fun featureTaskRuntimeSharedEvidence(record: FeatureTaskRuntimeSharedEvidenceMeasurement) {
    enqueueTelemetry(
      connection,
      runtimeVersion,
      TelemetryOutboxEvent.FEATURE_TASK_RUNTIME_SHARED_EVIDENCE,
      JsonCodec.anyToStringAnyMap(record.asTelemetryPayload()) ?: emptyMap(),
    )
  }

  override fun featureTaskRuntimeRejection(record: FeatureTaskRuntimeRejectionMeasurement) {
    enqueueTelemetry(
      connection,
      runtimeVersion,
      TelemetryOutboxEvent.FEATURE_TASK_RUNTIME_REJECTION,
      JsonCodec.anyToStringAnyMap(record.asTelemetryPayload()) ?: emptyMap(),
    )
  }

  override fun featureTaskRuntimeDiagnosticDegradation(record: FeatureTaskRuntimeDiagnosticDegradationMeasurement) {
    enqueueTelemetry(
      connection,
      runtimeVersion,
      TelemetryOutboxEvent.FEATURE_TASK_RUNTIME_DIAGNOSTIC_DEGRADATION,
      JsonCodec.anyToStringAnyMap(record.asTelemetryPayload()) ?: emptyMap(),
    )
  }

  override fun reviewStageDegradation(record: ReviewStageDegradationMeasurement) {
    if (reviewStageDegradationExists(connection, record)) return
    enqueueTelemetry(
      connection,
      runtimeVersion,
      TelemetryOutboxEvent.REVIEW_STAGE_DEGRADATION,
      record.toStageDegradationPayload(),
    )
  }
}

private fun ReviewStageDegradationMeasurement.toStageDegradationPayload(): Map<String, Any?> =
  linkedMapOf(
    SqliteLifecycleTelemetryMaterializationPayloadKeys.EVENT_NAME to
      TelemetryOutboxEvent.REVIEW_STAGE_DEGRADATION.wireValue,
    SharedPayloadKeys.CONTRACT_VERSION to REVIEW_STAGE_DEGRADATION_CONTRACT_VERSION,
    ReviewVerificationSignalKeys.REVIEW_RUN_ID to reviewRunId,
    SqliteLifecycleTelemetryMaterializationPayloadKeys.SEAM to seam,
    SqliteLifecycleTelemetryMaterializationPayloadKeys.EXPECTED to expected,
    SqliteLifecycleTelemetryMaterializationPayloadKeys.ACTUAL to actual,
    SqliteLifecycleTelemetryMaterializationPayloadKeys.REASON to reason.wireValue,
  )
