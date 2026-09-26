package skillbill.ports.experiment.pair

import skillbill.ports.experiment.pair.model.ExperimentObservationImport
import skillbill.ports.experiment.pair.model.ExperimentPairPayload

interface ExperimentPairRepository {
  fun loadPairPayload(pairId: String): Pair<String, ExperimentPairPayload>?

  fun upsertPairRecord(
    pairId: String,
    executionMode: String,
    payload: ExperimentPairPayload,
  )

  fun insertObservationIfAbsent(observation: ExperimentObservationImport): Boolean

  fun deletePairsForWorkflowIds(workflowIds: List<String>)

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
