package skillbill.ports.experiment.pair

import skillbill.ports.experiment.pair.model.ExperimentObservationImport

interface ExperimentPairRepository {
  fun loadPairPayload(pairId: String): Pair<String, Map<String, Any?>>?

  fun upsertPairRecord(pairId: String, executionMode: String, payload: Map<String, Any?>)

  fun insertObservationIfAbsent(observation: ExperimentObservationImport): Boolean

  fun deletePairsForWorkflowIds(workflowIds: List<String>)

  fun saveReport(pairId: String, reportPayload: Map<String, Any?>) = Unit

  fun loadReport(pairId: String): Map<String, Any?>? = null

  fun listReports(): List<Map<String, Any?>> = emptyList()

  fun acquireLease(pairId: String, ownerToken: String, nowEpochMillis: Long, leaseMillis: Long): Boolean = true

  fun releaseLease(pairId: String, ownerToken: String) = Unit
}
