package skillbill.ports.experiment.pair

import skillbill.ports.experiment.pair.model.ExperimentObservationImport

object UnavailableExperimentPairRepository : ExperimentPairRepository {
  override fun loadPairPayload(pairId: String): Pair<String, Map<String, Any?>>? =
    error("ExperimentPairRepository is unavailable in this test harness.")

  override fun upsertPairRecord(pairId: String, executionMode: String, payload: Map<String, Any?>) =
    error("ExperimentPairRepository is unavailable in this test harness.")

  override fun insertObservationIfAbsent(observation: ExperimentObservationImport): Boolean =
    error("ExperimentPairRepository is unavailable in this test harness.")

  override fun deletePairsForWorkflowIds(workflowIds: List<String>) =
    error("ExperimentPairRepository is unavailable in this test harness.")
}
