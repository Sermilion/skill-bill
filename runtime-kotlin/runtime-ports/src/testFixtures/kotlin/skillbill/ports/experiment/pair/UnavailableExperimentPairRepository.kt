package skillbill.ports.experiment.pair

object UnavailableExperimentPairRepository : ExperimentPairRepository {
  override fun loadPairPayload(pairId: String): Pair<String, Map<String, Any?>>? =
    error("ExperimentPairRepository is unavailable in this test harness.")

  override fun upsertPairRecord(pairId: String, executionMode: String, payload: Map<String, Any?>) =
    error("ExperimentPairRepository is unavailable in this test harness.")

  override fun insertObservationIfAbsent(
    observationId: String,
    pairId: String,
    armId: String,
    eventIdentityJson: String,
    payloadJson: String,
    recordedAt: String,
  ): Boolean = error("ExperimentPairRepository is unavailable in this test harness.")

  override fun deletePairsForWorkflowIds(workflowIds: List<String>) =
    error("ExperimentPairRepository is unavailable in this test harness.")
}
