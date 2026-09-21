package skillbill.engine.experiment.codegraph

import skillbill.contracts.experiment.ExperimentObservationPayloadKeys
import skillbill.contracts.telemetry.TelemetryMeasurementAvailability
import skillbill.experiment.model.ExperimentArmId
import skillbill.ports.experiment.codegraph.CodeGraphUsageLedgerPort
import skillbill.ports.experiment.codegraph.model.CodeGraphPairUsageSnapshot
import skillbill.ports.experiment.measurement.ExperimentArmMeasurement
import skillbill.ports.experiment.measurement.ExperimentArmMeasurementPort
import skillbill.ports.experiment.measurement.ExperimentMeasuredValue

class CodeGraphExperimentArmMeasurement(
  private val usageLedger: CodeGraphUsageLedgerPort,
) : ExperimentArmMeasurementPort {
  override fun measure(pairId: String, armId: String, workflowId: String): ExperimentArmMeasurement {
    if (armId != ExperimentArmId.TREATMENT.wireValue) {
      return unavailable("control arm does not incur CodeGraph setup")
    }
    val snapshot = usageLedger.snapshot(pairId)
    if (snapshot.notExercised) {
      return ExperimentArmMeasurement(
        setupCost = measured(snapshot.installDurationMs.toDouble()),
        usage = unavailableValue("CodeGraph was not exercised"),
        cost = unavailableValue("CodeGraph usage cost is unavailable when not exercised"),
        additionalMeasurements = additionalMeasurements(snapshot),
      )
    }
    if (snapshot.degraded) {
      return ExperimentArmMeasurement(
        setupCost = measured(
          snapshot.installDurationMs.toDouble() +
            snapshot.indexDurationMs.toDouble() +
            snapshot.syncDurationMs.toDouble(),
        ),
        usage = measured(snapshot.queryCount.toDouble()),
        cost = unavailableValue(snapshot.degradationReason ?: "CodeGraph treatment was degraded"),
        additionalMeasurements = additionalMeasurements(snapshot),
      )
    }
    val setupMs = snapshot.installDurationMs + snapshot.indexDurationMs + snapshot.syncDurationMs
    return ExperimentArmMeasurement(
      setupCost = measured(setupMs.toDouble()),
      usage = measured(snapshot.queryCount.toDouble()),
      cost = measured(snapshot.evidenceConsumedBytes.toDouble()),
      additionalMeasurements = additionalMeasurements(snapshot),
    )
  }

  private fun additionalMeasurements(snapshot: CodeGraphPairUsageSnapshot): Map<String, ExperimentMeasuredValue> {
    if (snapshot.notExercised) {
      val unavailable = unavailableValue("CodeGraph was not exercised")
      return listOf(
        ExperimentObservationPayloadKeys.CODEGRAPH_INSTALL_DURATION_METRIC_ID,
        ExperimentObservationPayloadKeys.CODEGRAPH_INDEX_DURATION_METRIC_ID,
        ExperimentObservationPayloadKeys.CODEGRAPH_SYNC_DURATION_METRIC_ID,
        ExperimentObservationPayloadKeys.CODEGRAPH_QUERY_COUNT_METRIC_ID,
        ExperimentObservationPayloadKeys.CODEGRAPH_EVIDENCE_BYTES_METRIC_ID,
      ).associateWith { unavailable }
    }
    return mapOf(
      ExperimentObservationPayloadKeys.CODEGRAPH_INSTALL_DURATION_METRIC_ID to
        measured(snapshot.installDurationMs.toDouble()),
      ExperimentObservationPayloadKeys.CODEGRAPH_INDEX_DURATION_METRIC_ID to
        measured(snapshot.indexDurationMs.toDouble()),
      ExperimentObservationPayloadKeys.CODEGRAPH_SYNC_DURATION_METRIC_ID to
        measured(snapshot.syncDurationMs.toDouble()),
      ExperimentObservationPayloadKeys.CODEGRAPH_QUERY_COUNT_METRIC_ID to
        measured(snapshot.queryCount.toDouble()),
      ExperimentObservationPayloadKeys.CODEGRAPH_EVIDENCE_BYTES_METRIC_ID to
        measured(snapshot.evidenceConsumedBytes.toDouble()),
    )
  }

  private fun measured(quantity: Double): ExperimentMeasuredValue = ExperimentMeasuredValue(
    quantity = quantity,
    availability = TelemetryMeasurementAvailability.MEASURED.wireValue,
  )

  private fun unavailable(reason: String): ExperimentArmMeasurement = ExperimentArmMeasurement(
    setupCost = unavailableValue(reason),
    usage = unavailableValue(reason),
    cost = unavailableValue(reason),
  )

  private fun unavailableValue(reason: String): ExperimentMeasuredValue = ExperimentMeasuredValue(
    availability = TelemetryMeasurementAvailability.UNAVAILABLE_INCOMPLETE.wireValue,
    reason = reason,
  )
}
