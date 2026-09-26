package skillbill.ports.experiment.pair

import skillbill.ports.experiment.pair.model.ExperimentObservationImport
import skillbill.ports.experiment.pair.model.ExperimentPairPayload

object UnavailableExperimentPairRepository : ExperimentPairRepository {
  override fun loadPairPayload(pairId: String): Pair<String, ExperimentPairPayload>? =
    error("ExperimentPairRepository is unavailable in this test harness.")

  override fun upsertPairRecord(
    pairId: String,
    executionMode: String,
    payload: ExperimentPairPayload,
  ) = error("ExperimentPairRepository is unavailable in this test harness.")

  override fun insertObservationIfAbsent(observation: ExperimentObservationImport): Boolean =
    error("ExperimentPairRepository is unavailable in this test harness.")

  override fun deletePairsForWorkflowIds(workflowIds: List<String>) =
    error("ExperimentPairRepository is unavailable in this test harness.")

  override fun saveReport(
    pairId: String,
    reportPayload: ExperimentPairPayload,
  ) = error("ExperimentPairRepository is unavailable in this test harness.")

  override fun loadReport(pairId: String): ExperimentPairPayload? =
    error("ExperimentPairRepository is unavailable in this test harness.")

  override fun listReports(): List<ExperimentPairPayload> =
    error("ExperimentPairRepository is unavailable in this test harness.")

  override fun acquireLease(
    pairId: String,
    ownerToken: String,
    nowEpochMillis: Long,
    leaseMillis: Long,
  ): Boolean = error("ExperimentPairRepository is unavailable in this test harness.")

  override fun releaseLease(
    pairId: String,
    ownerToken: String,
  ) = error("ExperimentPairRepository is unavailable in this test harness.")
}
