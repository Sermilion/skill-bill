package skillbill.ports.experiment.pair

import skillbill.ports.experiment.pair.model.ExperimentPairPayload
import skillbill.ports.experiment.pair.model.ExperimentPairPersistedState

interface ExperimentPairOwnerPort {
  fun load(pairId: String): ExperimentPairPersistedState?

  fun save(state: ExperimentPairPersistedState)

  fun importObservation(payload: ExperimentPairPayload): Boolean

  fun saveReport(
    pairId: String,
    reportPayload: ExperimentPairPayload,
  )

  fun loadReport(pairId: String): ExperimentPairPayload?

  fun listReports(): List<ExperimentPairPayload>

  fun acquireLease(
    pairId: String,
    ownerToken: String,
    nowEpochMillis: Long,
    leaseMillis: Long,
  ): Boolean

  fun releaseLease(
    pairId: String,
    ownerToken: String,
  )
}
