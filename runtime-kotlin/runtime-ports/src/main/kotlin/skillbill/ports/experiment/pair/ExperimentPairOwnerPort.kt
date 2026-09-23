package skillbill.ports.experiment.pair

import skillbill.ports.experiment.pair.model.ExperimentPairPersistedState as ExperimentPairPersistedStateModel
import skillbill.ports.experiment.pair.model.ExperimentPairPayload as ExperimentPairPayloadModel

typealias ExperimentPairPersistedState = ExperimentPairPersistedStateModel
typealias ExperimentPairPayload = ExperimentPairPayloadModel

interface ExperimentPairOwnerPort {
  fun load(pairId: String): ExperimentPairPersistedState?

  fun save(state: ExperimentPairPersistedState)

  fun importObservation(payload: ExperimentPairPayload): Boolean

  fun saveReport(
    pairId: String,
    reportPayload: ExperimentPairPayload,
  ) = Unit

  fun loadReport(pairId: String): ExperimentPairPayload? = null

  fun listReports(): List<ExperimentPairPayload> = emptyList()

  fun acquireLease(
    pairId: String,
    ownerToken: String,
    nowEpochMillis: Long,
    leaseMillis: Long,
  ): Boolean = true

  fun releaseLease(
    pairId: String,
    ownerToken: String,
  ) = Unit
}
