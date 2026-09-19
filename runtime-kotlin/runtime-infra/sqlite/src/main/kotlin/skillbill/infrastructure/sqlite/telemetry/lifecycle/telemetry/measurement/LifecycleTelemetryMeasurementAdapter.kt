package skillbill.infrastructure.sqlite.telemetry.lifecycle.telemetry.measurement
import skillbill.contracts.JsonCodec
import skillbill.contracts.SharedPayloadKeys
import skillbill.contracts.review.ReviewVerificationSignalKeys
import skillbill.contracts.telemetry.SqliteLifecycleTelemetryMaterializationPayloadKeys
import skillbill.infrastructure.sqlite.telemetry.lifecycle.telemetry.emit.enqueueTelemetry
import skillbill.infrastructure.sqlite.telemetry.lifecycle.telemetry.emit.reviewStageDegradationExists
import skillbill.ports.telemetry.lifecycle.FeatureTaskRuntimeTelemetryMeasurementRepository
import skillbill.ports.telemetry.lifecycle.ReviewStageTelemetryMeasurementRepository
import skillbill.review.model.REVIEW_STAGE_DEGRADATION_CONTRACT_VERSION
import skillbill.review.model.REVIEW_STAGE_DEGRADATION_EVENT_NAME
import skillbill.review.model.ReviewStageDegradationMeasurement
import skillbill.workflow.taskruntime.artifact.asTelemetryPayload
import skillbill.workflow.taskruntime.model.handoff.task.FeatureTaskRuntimeDiagnosticDegradationMeasurement
import skillbill.workflow.taskruntime.model.handoff.task.FeatureTaskRuntimeProjectionMeasurement
import skillbill.workflow.taskruntime.model.handoff.task.FeatureTaskRuntimeRejectionMeasurement
import skillbill.workflow.taskruntime.model.handoff.task.FeatureTaskRuntimeSharedEvidenceMeasurement
import java.sql.Connection

internal class LifecycleTelemetryMeasurementAdapter(
  private val connection: Connection,
) : FeatureTaskRuntimeTelemetryMeasurementRepository,
  ReviewStageTelemetryMeasurementRepository {
  override fun featureTaskRuntimeProjectionMeasurement(record: FeatureTaskRuntimeProjectionMeasurement) {
    enqueueTelemetry(
      connection,
      "skillbill_feature_task_runtime_projection_measurement",
      JsonCodec.anyToStringAnyMap(record.asTelemetryPayload()) ?: emptyMap(),
    )
  }

  override fun featureTaskRuntimeSharedEvidence(record: FeatureTaskRuntimeSharedEvidenceMeasurement) {
    enqueueTelemetry(
      connection,
      "skillbill_feature_task_runtime_shared_evidence",
      JsonCodec.anyToStringAnyMap(record.asTelemetryPayload()) ?: emptyMap(),
    )
  }

  override fun featureTaskRuntimeRejection(record: FeatureTaskRuntimeRejectionMeasurement) {
    enqueueTelemetry(
      connection,
      "skillbill_feature_task_runtime_rejection",
      JsonCodec.anyToStringAnyMap(record.asTelemetryPayload()) ?: emptyMap(),
    )
  }

  override fun featureTaskRuntimeDiagnosticDegradation(record: FeatureTaskRuntimeDiagnosticDegradationMeasurement) {
    enqueueTelemetry(
      connection,
      "skillbill_feature_task_runtime_diagnostic_degradation",
      JsonCodec.anyToStringAnyMap(record.asTelemetryPayload()) ?: emptyMap(),
    )
  }

  override fun reviewStageDegradation(record: ReviewStageDegradationMeasurement) {
    if (reviewStageDegradationExists(connection, record)) return
    enqueueTelemetry(connection, REVIEW_STAGE_DEGRADATION_EVENT_NAME, record.toStageDegradationPayload())
  }
}

private fun ReviewStageDegradationMeasurement.toStageDegradationPayload(): Map<String, Any?> = linkedMapOf(
  SqliteLifecycleTelemetryMaterializationPayloadKeys.EVENT_NAME to REVIEW_STAGE_DEGRADATION_EVENT_NAME,
  SharedPayloadKeys.CONTRACT_VERSION to REVIEW_STAGE_DEGRADATION_CONTRACT_VERSION,
  ReviewVerificationSignalKeys.REVIEW_RUN_ID to reviewRunId,
  SqliteLifecycleTelemetryMaterializationPayloadKeys.SEAM to seam,
  SqliteLifecycleTelemetryMaterializationPayloadKeys.EXPECTED to expected,
  SqliteLifecycleTelemetryMaterializationPayloadKeys.ACTUAL to actual,
  SqliteLifecycleTelemetryMaterializationPayloadKeys.REASON to reason.wireValue,
)
