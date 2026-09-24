package skillbill.infrastructure.sqlite.experiment

import me.tatarka.inject.annotations.Inject
import skillbill.contracts.JsonCodec
import skillbill.contracts.experiment.ExperimentObservationPayloadKeys
import skillbill.contracts.experiment.ExperimentPairPayloadKeys
import skillbill.experiment.model.ExperimentArmId
import skillbill.experiment.model.ExperimentExecutionMode
import skillbill.ports.db.DatabaseSessionFactory
import skillbill.ports.experiment.pair.ExperimentPairOwnerPort
import skillbill.ports.experiment.pair.ExperimentPairPayload
import skillbill.ports.experiment.pair.ExperimentPairPersistedState
import skillbill.ports.experiment.pair.model.ExperimentObservationImport
import skillbill.ports.experiment.validation.ExperimentPayloadValidationPort

@Inject
class SqliteExperimentPairOwnerStore(
  private val database: DatabaseSessionFactory,
  private val payloadValidation: ExperimentPayloadValidationPort?,
) : ExperimentPairOwnerPort {
  override fun load(pairId: String): ExperimentPairPersistedState? =
    database.read { session ->
      val loaded = session.experimentPairs.loadPairPayload(pairId) ?: return@read null
      val (modeWire, payload) = loaded
      val mode = ExperimentExecutionMode.fromWire(modeWire) ?: return@read null
      val names =
        (payload[ExperimentPairPayloadKeys.SELECTED_EXPERIMENT_NAMES] as? List<*>)?.map { it.toString() }
          ?: emptyList()
      val armOrder =
        (payload[ExperimentPairPayloadKeys.ARM_ORDER] as? List<*>)?.mapNotNull {
          ExperimentArmId.fromWire(it.toString())
        } ?: listOf(ExperimentArmId.CONTROL, ExperimentArmId.TREATMENT)
      val seed = payload[ExperimentPairPayloadKeys.RANDOM_SEED]?.toString() ?: ""
      ExperimentPairPersistedState(pairId, mode, names, armOrder, seed, payload)
    }

  override fun save(state: ExperimentPairPersistedState) {
    payloadValidation?.validatePair(state.pairPayload, "experiment-pair:${state.pairId}")
    database.transaction { session ->
      session.experimentPairs.upsertPairRecord(
        state.pairId,
        state.executionMode.wireValue,
        state.pairPayload,
      )
    }
  }

  override fun importObservation(payload: ExperimentPairPayload): Boolean {
    payloadValidation?.validateObservation(payload, "experiment-observation-import")
    val observation = observationImport(payload.toMap()) ?: return false
    return database.transaction { session ->
      session.experimentPairs.insertObservationIfAbsent(observation)
    }
  }

  private fun observationImport(payload: Map<String, Any?>): ExperimentObservationImport? {
    val observationId = payload[ExperimentObservationPayloadKeys.OBSERVATION_ID]?.toString()
    val pairId = payload[ExperimentObservationPayloadKeys.PAIR_ID]?.toString()
    val armId = payload[ExperimentObservationPayloadKeys.ARM_ID]?.toString()
    val recordedAt = payload[ExperimentObservationPayloadKeys.RECORDED_AT]?.toString()
    val eventIdentity = payload[ExperimentObservationPayloadKeys.EVENT_IDENTITY] as? Map<*, *>
    return if (!hasRequiredObservationFields(observationId, pairId, armId, recordedAt, eventIdentity)) {
      null
    } else {
      ExperimentObservationImport(
        observationId = requireNotNull(observationId),
        pairId = requireNotNull(pairId),
        armId = requireNotNull(armId),
        eventIdentityJson =
          JsonCodec.mapToJsonString(
            JsonCodec.anyToStringAnyMap(eventIdentity)?.toSortedMap().orEmpty(),
          ),
        payloadJson = JsonCodec.mapToJsonString(payload),
        recordedAt = requireNotNull(recordedAt),
      )
    }
  }

  private fun hasRequiredObservationFields(
    observationId: String?,
    pairId: String?,
    armId: String?,
    recordedAt: String?,
    eventIdentity: Map<*, *>?,
  ): Boolean = listOf(observationId, pairId, armId, recordedAt, eventIdentity).all { it != null }

  override fun saveReport(
    pairId: String,
    reportPayload: ExperimentPairPayload,
  ) {
    payloadValidation?.validateReport(reportPayload, "experiment-report:$pairId")
    database.transaction { session ->
      session.experimentPairs.saveReport(pairId, reportPayload)
    }
  }

  override fun loadReport(pairId: String): ExperimentPairPayload? =
    database.read { session -> session.experimentPairs.loadReport(pairId) }

  override fun listReports(): List<ExperimentPairPayload> =
    database.read { session -> session.experimentPairs.listReports() }

  override fun acquireLease(
    pairId: String,
    ownerToken: String,
    nowEpochMillis: Long,
    leaseMillis: Long,
  ): Boolean =
    database.transaction { session ->
      session.experimentPairs.acquireLease(pairId, ownerToken, nowEpochMillis, leaseMillis)
    }

  override fun releaseLease(
    pairId: String,
    ownerToken: String,
  ) {
    database.transaction { session ->
      session.experimentPairs.releaseLease(pairId, ownerToken)
    }
  }
}

private fun ExperimentPairPayload.toMap(): Map<String, Any?> =
  JsonCodec.anyToStringAnyMap(
    JsonCodec.jsonElementToValue(requireNotNull(JsonCodec.parseObjectOrNull(toJson()))),
  ) ?: error("Experiment pair payload must decode to an object.")
