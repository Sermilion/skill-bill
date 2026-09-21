package skillbill.ports.experiment.pair

import skillbill.ports.experiment.pair.model.ExperimentPairPersistedState as ExperimentPairPersistedStateModel

typealias ExperimentPairPersistedState = ExperimentPairPersistedStateModel

interface ExperimentPairOwnerPort {
  fun load(pairId: String): ExperimentPairPersistedState?
  fun save(state: ExperimentPairPersistedState)
  fun importObservation(payload: Map<String, Any?>): Boolean

  fun saveReport(pairId: String, reportPayload: Map<String, Any?>) = Unit

  fun loadReport(pairId: String): Map<String, Any?>? = null

  fun listReports(): List<Map<String, Any?>> = emptyList()

  fun acquireLease(pairId: String, ownerToken: String, nowEpochMillis: Long, leaseMillis: Long): Boolean = true

  fun releaseLease(pairId: String, ownerToken: String) = Unit
}
