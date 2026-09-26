package skillbill.engine.experiment.observation

import skillbill.contracts.experiment.EXPERIMENT_OBSERVATION_CONTRACT_VERSION
import skillbill.contracts.experiment.ExperimentObservationPayloadKeys
import skillbill.ports.experiment.pair.ExperimentPairOwnerPort
import skillbill.ports.experiment.pair.model.ExperimentPairPayload
import java.security.MessageDigest

data class ExperimentObservationMeasurement(
  val metricId: String,
  val quantity: Double?,
  val availability: String,
  val reason: String? = null,
)

data class ExperimentObservationRecordRequest(
  val pairId: String,
  val armId: String,
  val workflowId: String,
  val phaseId: String,
  val attempt: Int,
  val recordedAt: String,
  val measurements: List<ExperimentObservationMeasurement>,
)

class ExperimentObservationRecorder(
  private val pairOwner: ExperimentPairOwnerPort,
) {
  fun record(request: ExperimentObservationRecordRequest): Boolean {
    val pairId = request.pairId
    val armId = request.armId
    val workflowId = request.workflowId
    val phaseId = request.phaseId
    val attempt = request.attempt
    val recordedAt = request.recordedAt
    val measurements = request.measurements
    val eventIdentity =
      mapOf(
        ExperimentObservationPayloadKeys.WORKFLOW_ID to workflowId,
        ExperimentObservationPayloadKeys.PHASE_ID to phaseId,
        ExperimentObservationPayloadKeys.ATTEMPT to attempt,
        ExperimentObservationPayloadKeys.EVENT_KIND to "phase_measurement",
      )
    val observationId =
      sha256(
        "$pairId|$armId|$workflowId|$phaseId|$attempt|phase_measurement".encodeToByteArray(),
      )
    return pairOwner.importObservation(
      ExperimentPairPayload(
        mapOf(
          ExperimentObservationPayloadKeys.CONTRACT_VERSION to EXPERIMENT_OBSERVATION_CONTRACT_VERSION,
          ExperimentObservationPayloadKeys.OBSERVATION_ID to observationId,
          ExperimentObservationPayloadKeys.PAIR_ID to pairId,
          ExperimentObservationPayloadKeys.ARM_ID to armId,
          ExperimentObservationPayloadKeys.EVENT_IDENTITY to eventIdentity,
          ExperimentObservationPayloadKeys.RECORDED_AT to recordedAt,
          ExperimentObservationPayloadKeys.MEASUREMENTS to
            measurements.map { measurement ->
              mapOf(
                ExperimentObservationPayloadKeys.METRIC_ID to measurement.metricId,
                ExperimentObservationPayloadKeys.AVAILABILITY to measurement.availability,
                ExperimentObservationPayloadKeys.QUANTITY to measurement.quantity,
                ExperimentObservationPayloadKeys.REASON to measurement.reason,
              ).filterValues { it != null }
            },
        ),
      ),
    )
  }

  private fun sha256(value: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(value).joinToString("") { byte -> "%02x".format(byte) }
}
