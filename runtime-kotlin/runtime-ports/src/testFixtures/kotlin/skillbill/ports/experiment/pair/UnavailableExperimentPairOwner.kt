package skillbill.ports.experiment.pair

import skillbill.ports.experiment.pair.model.ExperimentPairPayload
import skillbill.ports.experiment.pair.model.ExperimentPairPersistedState

object UnavailableExperimentPairOwner : ExperimentPairOwnerPort {
  override fun load(pairId: String): ExperimentPairPersistedState? =
    error("ExperimentPairOwnerPort is unavailable in this test harness.")

  override fun save(state: ExperimentPairPersistedState) =
    error("ExperimentPairOwnerPort is unavailable in this test harness.")

  override fun importObservation(payload: ExperimentPairPayload): Boolean =
    error("ExperimentPairOwnerPort is unavailable in this test harness.")

  override fun saveReport(
    pairId: String,
    reportPayload: ExperimentPairPayload,
  ) = error("ExperimentPairOwnerPort is unavailable in this test harness.")

  override fun loadReport(pairId: String): ExperimentPairPayload? =
    error("ExperimentPairOwnerPort is unavailable in this test harness.")

  override fun listReports(): List<ExperimentPairPayload> =
    error("ExperimentPairOwnerPort is unavailable in this test harness.")

  override fun acquireLease(
    pairId: String,
    ownerToken: String,
    nowEpochMillis: Long,
    leaseMillis: Long,
  ): Boolean = error("ExperimentPairOwnerPort is unavailable in this test harness.")

  override fun releaseLease(
    pairId: String,
    ownerToken: String,
  ) = error("ExperimentPairOwnerPort is unavailable in this test harness.")
}
