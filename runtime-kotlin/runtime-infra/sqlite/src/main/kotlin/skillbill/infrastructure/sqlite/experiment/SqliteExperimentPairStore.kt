package skillbill.infrastructure.sqlite.experiment
import skillbill.contracts.JsonCodec
import skillbill.contracts.experiment.EXPERIMENT_OBSERVATION_CONTRACT_VERSION
import skillbill.contracts.experiment.EXPERIMENT_PAIR_CONTRACT_VERSION
import skillbill.contracts.experiment.ExperimentObservationPayloadKeys
import skillbill.contracts.experiment.ExperimentPairPayloadKeys
import skillbill.error.shellcontent.ExperimentIsolationCapabilityRefusalError
import skillbill.infrastructure.sqlite.core.ops.bindAll
import skillbill.ports.experiment.pair.ExperimentPairRepository
import skillbill.ports.experiment.pair.model.ExperimentPairPayload
import skillbill.ports.experiment.pair.model.ExperimentObservationImport
import java.sql.Connection

internal class SqliteExperimentPairStore(
  private val connection: Connection,
) : ExperimentPairRepository {
  override fun loadPairPayload(pairId: String): Pair<String, ExperimentPairPayload>? =
    connection.prepareStatement(
      """
      SELECT pair_id, execution_mode, selected_experiment_names_json, arm_order_json,
        random_seed, delivery_arm, pair_status, frozen_input_identity_json, delivery_status
      FROM experiment_pairs WHERE pair_id = ?
      """.trimIndent(),
    ).use { statement ->
      statement.bindAll(pairId)
      statement.executeQuery().use { rows ->
        if (!rows.next()) return null
        val mode = rows.getString("execution_mode")
        val payload = JsonCodec.parseObjectOrNull(rows.getString("frozen_input_identity_json")) ?: return null
        val map = (JsonCodec.anyToStringAnyMap(JsonCodec.jsonElementToValue(payload)) ?: emptyMap()).toMutableMap()
        map[ExperimentPairPayloadKeys.PAIR_ID] = rows.getString("pair_id")
        map[ExperimentPairPayloadKeys.EXECUTION_MODE] = mode
        map.putAll(jsonObjectMap(rows.getString("selected_experiment_names_json")))
        map.putAll(jsonObjectMap(rows.getString("arm_order_json")))
        map[ExperimentPairPayloadKeys.RANDOM_SEED] = rows.getString("random_seed")
        map[ExperimentPairPayloadKeys.DELIVERY_ARM] = rows.getString("delivery_arm")
        map[ExperimentPairPayloadKeys.PAIR_STATUS] = rows.getString("pair_status")
        map[ExperimentPairPayloadKeys.DELIVERY_STATUS] = rows.getString("delivery_status")
        map[ExperimentPairPayloadKeys.ARM_OUTCOMES] = loadArmOutcomes(pairId)
        map[ExperimentPairPayloadKeys.OBSERVATION_LEDGER] = loadObservationLedger(pairId)
        mode to ExperimentPairPayload(map)
      }
    }

  private fun loadArmOutcomes(pairId: String): List<Map<String, Any?>> =
    connection.prepareStatement(
      """
      SELECT arm_id, workflow_id, terminal_status, worktree_path, deferred_publication, outcome_json
      FROM experiment_arm_outcomes WHERE pair_id = ? ORDER BY arm_id
      """.trimIndent(),
    ).use { statement ->
      statement.bindAll(pairId)
      statement.executeQuery().use { rows ->
        buildList {
          while (rows.next()) {
            val persisted =
              rows.getString("outcome_json")
                ?.let(::jsonObjectMap)
                ?.toMutableMap()
                ?: mutableMapOf()
            persisted[ExperimentPairPayloadKeys.ARM_ID] = rows.getString("arm_id")
            persisted[ExperimentPairPayloadKeys.WORKFLOW_ID] = rows.getString("workflow_id")
            persisted[ExperimentPairPayloadKeys.TERMINAL_STATUS] = rows.getString("terminal_status")
            persisted[ExperimentPairPayloadKeys.WORKTREE_PATH] = rows.getString("worktree_path")
            persisted[ExperimentPairPayloadKeys.DEFERRED_PUBLICATION] =
              rows.getInt("deferred_publication") != 0
            add(persisted.filterValues { it != null })
          }
        }
      }
    }

  private fun loadObservationLedger(pairId: String): List<Map<String, Any?>> =
    connection.prepareStatement(
      """
      SELECT observation_id, arm_id, event_identity_json, recorded_at, measurements_json
      FROM experiment_observations WHERE pair_id = ? ORDER BY recorded_at, observation_id
      """.trimIndent(),
    ).use { statement ->
      statement.bindAll(pairId)
      statement.executeQuery().use { rows ->
        buildList {
          while (rows.next()) {
            val measurements =
              jsonObjectMap(rows.getString("measurements_json"))
                .get(ExperimentObservationPayloadKeys.MEASUREMENTS)
            add(
              mapOf(
                ExperimentObservationPayloadKeys.CONTRACT_VERSION to EXPERIMENT_OBSERVATION_CONTRACT_VERSION,
                ExperimentObservationPayloadKeys.OBSERVATION_ID to rows.getString("observation_id"),
                ExperimentObservationPayloadKeys.PAIR_ID to pairId,
                ExperimentObservationPayloadKeys.ARM_ID to rows.getString("arm_id"),
                ExperimentObservationPayloadKeys.EVENT_IDENTITY to
                  jsonObjectMap(
                    rows.getString("event_identity_json"),
                  ),
                ExperimentObservationPayloadKeys.RECORDED_AT to rows.getString("recorded_at"),
                ExperimentObservationPayloadKeys.MEASUREMENTS to measurements,
              ),
            )
          }
        }
      }
    }

  override fun upsertPairRecord(
    pairId: String,
    executionMode: String,
    payload: ExperimentPairPayload,
  ) {
    val payloadMap = payload.toMap()
    val namesJson =
      JsonCodec.mapToJsonString(
        mapOf(
          ExperimentPairPayloadKeys.SELECTED_EXPERIMENT_NAMES to
            payloadMap[ExperimentPairPayloadKeys.SELECTED_EXPERIMENT_NAMES],
        ),
      )
    val armOrderJson =
      JsonCodec.mapToJsonString(
        mapOf(ExperimentPairPayloadKeys.ARM_ORDER to payloadMap[ExperimentPairPayloadKeys.ARM_ORDER]),
      )
    connection.prepareStatement(
      """
      INSERT INTO experiment_pairs(
        pair_id, contract_version, execution_mode, selected_experiment_names_json, arm_order_json,
        random_seed, delivery_arm, pair_status, frozen_input_identity_json, delivery_status
      ) VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
      ON CONFLICT(pair_id) DO UPDATE SET
        execution_mode = excluded.execution_mode,
        selected_experiment_names_json = excluded.selected_experiment_names_json,
        arm_order_json = excluded.arm_order_json,
        random_seed = excluded.random_seed,
        delivery_arm = excluded.delivery_arm,
        pair_status = excluded.pair_status,
        frozen_input_identity_json = excluded.frozen_input_identity_json,
        delivery_status = excluded.delivery_status,
        updated_at = CURRENT_TIMESTAMP
      """.trimIndent(),
    ).use { statement ->
      statement.bindAll(
        pairId,
        EXPERIMENT_PAIR_CONTRACT_VERSION,
        executionMode,
        namesJson,
        armOrderJson,
        payloadMap[ExperimentPairPayloadKeys.RANDOM_SEED]?.toString(),
        payloadMap[ExperimentPairPayloadKeys.DELIVERY_ARM]?.toString() ?: "control",
        payloadMap[ExperimentPairPayloadKeys.PAIR_STATUS]?.toString() ?: "pending",
        JsonCodec.mapToJsonString(payloadMap),
        payloadMap[ExperimentPairPayloadKeys.DELIVERY_STATUS]?.toString() ?: "deferred",
      )
      statement.executeUpdate()
    }
    persistArmOutcomes(pairId, payloadMap)
  }

  private fun persistArmOutcomes(
    pairId: String,
    payload: Map<String, Any?>,
  ) {
    val outcomes =
      (payload[ExperimentPairPayloadKeys.ARM_OUTCOMES] as? List<*>)
        ?.mapNotNull { value ->
          (value as? Map<*, *>)?.entries?.associate { entry -> entry.key.toString() to entry.value }
        }
        .orEmpty()
    connection.prepareStatement("DELETE FROM experiment_arm_outcomes WHERE pair_id = ?").use { statement ->
      statement.bindAll(pairId)
      statement.executeUpdate()
    }
    outcomes.forEach { outcome ->
      connection.prepareStatement(
        """
        INSERT INTO experiment_arm_outcomes(
          pair_id, arm_id, workflow_id, terminal_status, worktree_path, deferred_publication,
          outcome_json
        ) VALUES(?, ?, ?, ?, ?, ?, ?)
        """.trimIndent(),
      ).use { statement ->
        statement.bindAll(
          pairId,
          outcome[ExperimentPairPayloadKeys.ARM_ID]?.toString(),
          outcome[ExperimentPairPayloadKeys.WORKFLOW_ID]?.toString(),
          outcome[ExperimentPairPayloadKeys.TERMINAL_STATUS]?.toString() ?: "pending",
          outcome[ExperimentPairPayloadKeys.WORKTREE_PATH]?.toString(),
          if (outcome[ExperimentPairPayloadKeys.DEFERRED_PUBLICATION] == false) 0 else 1,
          JsonCodec.mapToJsonString(outcome),
        )
        statement.executeUpdate()
      }
    }
  }

  override fun insertObservationIfAbsent(observation: ExperimentObservationImport): Boolean {
    val payloadMap =
      JsonCodec.parseObjectOrNull(observation.payloadJson)?.let { element ->
        JsonCodec.anyToStringAnyMap(JsonCodec.jsonElementToValue(element))
      }
    val measurementsJson =
      JsonCodec.mapToJsonString(
        mapOf(
          ExperimentObservationPayloadKeys.MEASUREMENTS to
            payloadMap?.get(ExperimentObservationPayloadKeys.MEASUREMENTS),
        ),
      )
    return connection.prepareStatement(
      """
      INSERT OR IGNORE INTO experiment_observations(
        observation_id, pair_id, arm_id, event_identity_json, recorded_at, measurements_json
      ) VALUES(?, ?, ?, ?, ?, ?)
      """.trimIndent(),
    ).use { statement ->
      statement.bindAll(
        observation.observationId,
        observation.pairId,
        observation.armId,
        observation.eventIdentityJson,
        observation.recordedAt,
        measurementsJson,
      )
      statement.executeUpdate() > 0
    }
  }

  override fun deletePairsForWorkflowIds(workflowIds: List<String>) {
    val ids = workflowIds.filter(String::isNotBlank).distinct()
    if (ids.isEmpty()) return
    val placeholders = ids.joinToString(", ") { "?" }
    val pairIds =
      connection.prepareStatement(
        "SELECT DISTINCT pair_id FROM experiment_arm_outcomes WHERE workflow_id IN ($placeholders)",
      ).use { statement ->
        statement.bindAll(ids)
        statement.executeQuery().use { rows ->
          buildList {
            while (rows.next()) add(rows.getString("pair_id"))
          }
        }
      }
    if (pairIds.isEmpty()) return
    val pairPlaceholders = pairIds.joinToString(", ") { "?" }
    val liveLease =
      connection.prepareStatement(
        "SELECT 1 FROM experiment_pair_leases WHERE pair_id IN ($pairPlaceholders) AND expires_at > ? LIMIT 1",
      ).use { statement ->
        statement.bindAll(pairIds + System.currentTimeMillis())
        statement.executeQuery().use { rows -> rows.next() }
      }
    if (liveLease) {
      throw ExperimentIsolationCapabilityRefusalError("Experiment purge refused while an arm lease is live.")
    }
    listOf(
      "DELETE FROM experiment_observations WHERE pair_id IN ($pairPlaceholders)",
      "DELETE FROM experiment_arm_outcomes WHERE pair_id IN ($pairPlaceholders)",
      "DELETE FROM experiment_reports WHERE pair_id IN ($pairPlaceholders)",
      "DELETE FROM experiment_pair_leases WHERE pair_id IN ($pairPlaceholders)",
      "DELETE FROM experiment_pairs WHERE pair_id IN ($pairPlaceholders)",
    ).forEach { sql ->
      connection.prepareStatement(sql).use { statement ->
        statement.bindAll(pairIds)
        statement.executeUpdate()
      }
    }
  }

  override fun saveReport(
    pairId: String,
    reportPayload: ExperimentPairPayload,
  ) {
    connection.prepareStatement(
      """
      INSERT INTO experiment_reports(pair_id, report_json, updated_at)
      VALUES(?, ?, CURRENT_TIMESTAMP)
      ON CONFLICT(pair_id) DO UPDATE SET
        report_json = excluded.report_json,
        updated_at = CURRENT_TIMESTAMP
      """.trimIndent(),
    ).use { statement ->
      statement.bindAll(pairId, JsonCodec.mapToJsonString(reportPayload.toMap()))
      statement.executeUpdate()
    }
  }

  override fun loadReport(pairId: String): ExperimentPairPayload? =
    connection.prepareStatement(
      "SELECT report_json FROM experiment_reports WHERE pair_id = ?",
    ).use { statement ->
      statement.bindAll(pairId)
      statement.executeQuery().use { rows ->
        if (!rows.next()) return null
        ExperimentPairPayload(jsonObjectMap(rows.getString("report_json")))
      }
    }

  override fun listReports(): List<ExperimentPairPayload> =
    connection.prepareStatement(
      "SELECT report_json FROM experiment_reports ORDER BY updated_at, pair_id",
    ).use { statement ->
      statement.executeQuery().use { rows ->
        buildList {
          while (rows.next()) add(ExperimentPairPayload(jsonObjectMap(rows.getString("report_json"))))
        }
      }
    }

  override fun acquireLease(
    pairId: String,
    ownerToken: String,
    nowEpochMillis: Long,
    leaseMillis: Long,
  ): Boolean {
    val expiresAt = nowEpochMillis + leaseMillis
    return connection.prepareStatement(
      """
      INSERT INTO experiment_pair_leases(pair_id, owner_token, generation, expires_at)
      VALUES(?, ?, 1, ?)
      ON CONFLICT(pair_id) DO UPDATE SET
        owner_token = excluded.owner_token,
        generation = experiment_pair_leases.generation + 1,
        expires_at = excluded.expires_at
      WHERE experiment_pair_leases.expires_at <= ? OR experiment_pair_leases.owner_token = ?
      """.trimIndent(),
    ).use { statement ->
      statement.bindAll(pairId, ownerToken, expiresAt, nowEpochMillis, ownerToken)
      statement.executeUpdate() > 0
    }
  }

  override fun releaseLease(
    pairId: String,
    ownerToken: String,
  ) {
    connection.prepareStatement(
      "DELETE FROM experiment_pair_leases WHERE pair_id = ? AND owner_token = ?",
    ).use { statement ->
      statement.bindAll(pairId, ownerToken)
      statement.executeUpdate()
    }
  }

  private fun jsonObjectMap(json: String): Map<String, Any?> {
    val element = JsonCodec.parseObjectOrNull(json) ?: return emptyMap()
    return JsonCodec.anyToStringAnyMap(JsonCodec.jsonElementToValue(element)).orEmpty()
  }
}

private fun ExperimentPairPayload.toMap(): Map<String, Any?> =
  JsonCodec.anyToStringAnyMap(
    JsonCodec.jsonElementToValue(requireNotNull(JsonCodec.parseObjectOrNull(toJson()))),
  ) ?: error("Experiment pair payload must decode to an object.")
