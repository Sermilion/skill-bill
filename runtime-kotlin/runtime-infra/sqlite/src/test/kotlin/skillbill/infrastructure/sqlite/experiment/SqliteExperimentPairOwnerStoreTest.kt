package skillbill.infrastructure.sqlite.experiment

import org.junit.jupiter.api.Test
import skillbill.contracts.JsonCodec
import skillbill.contracts.experiment.EXPERIMENT_OBSERVATION_CONTRACT_VERSION
import skillbill.contracts.experiment.EXPERIMENT_PAIR_CONTRACT_VERSION
import skillbill.contracts.experiment.ExperimentObservationPayloadKeys
import skillbill.contracts.experiment.ExperimentPairPayloadKeys
import skillbill.contracts.experiment.ExperimentReportPayloadKeys
import skillbill.experiment.model.ExperimentArmId
import skillbill.experiment.model.ExperimentExecutionMode
import skillbill.infrastructure.sqlite.core.schema.DatabaseRuntime
import skillbill.infrastructure.sqlite.sqliteDatabaseSessionFactory
import skillbill.ports.experiment.pair.model.ExperimentPairPayload
import skillbill.ports.experiment.pair.model.ExperimentPairPersistedState
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SqliteExperimentPairOwnerStoreTest {
  @Test
  fun `duplicate observation import is a no-op`() {
    val tempDir = Files.createTempDirectory("experiment-pair-store")
    val dbPath = tempDir.resolve("metrics.db")
    DatabaseRuntime.ensureDatabase(dbPath).close()
    val factory =
      sqliteDatabaseSessionFactory(userHome = tempDir, dbPathOverride = dbPath.toString(), environment = emptyMap())
    val store = SqliteExperimentPairOwnerStore(factory, null)
    store.save(pairState("pair-1"))
    val payload = observationPayload("obs-1")
    assertTrue(store.importObservation(ExperimentPairPayload(payload)))
    assertFalse(store.importObservation(ExperimentPairPayload(payload)))
  }

  @Test
  fun `same event identity does not double count when receipt id changes`() {
    val tempDir = Files.createTempDirectory("experiment-pair-event-identity")
    val dbPath = tempDir.resolve("metrics.db")
    DatabaseRuntime.ensureDatabase(dbPath).close()
    val factory =
      sqliteDatabaseSessionFactory(userHome = tempDir, dbPathOverride = dbPath.toString(), environment = emptyMap())
    val store = SqliteExperimentPairOwnerStore(factory, null)
    store.save(pairState("pair-1"))
    val first = observationPayload("obs-1")
    val retry =
      first +
        (ExperimentObservationPayloadKeys.OBSERVATION_ID to "obs-2") +
        (
          ExperimentObservationPayloadKeys.EVENT_IDENTITY to
            linkedMapOf(
              ExperimentObservationPayloadKeys.EVENT_KIND to "usage",
              ExperimentObservationPayloadKeys.ATTEMPT to 1,
              ExperimentObservationPayloadKeys.PHASE_ID to "implement",
              ExperimentObservationPayloadKeys.WORKFLOW_ID to "wf-1",
            )
        )

    assertTrue(store.importObservation(ExperimentPairPayload(first)))
    assertFalse(store.importObservation(ExperimentPairPayload(retry)))
  }

  @Test
  fun `reload preserves arm outcomes and committed observations for resume reconciliation`() {
    val tempDir = Files.createTempDirectory("experiment-pair-recovery")
    val dbPath = tempDir.resolve("metrics.db")
    DatabaseRuntime.ensureDatabase(dbPath).close()
    val factory =
      sqliteDatabaseSessionFactory(userHome = tempDir, dbPathOverride = dbPath.toString(), environment = emptyMap())
    seedRecoveryData(SqliteExperimentPairOwnerStore(factory, null))

    val reloadedStore =
      SqliteExperimentPairOwnerStore(
        sqliteDatabaseSessionFactory(
          userHome = tempDir,
          dbPathOverride = dbPath.toString(),
          environment = emptyMap(),
        ),
        null,
      )
    val loaded = requireNotNull(reloadedStore.load("pair-recovery"))
    val outcomes = loaded.pairPayload[ExperimentPairPayloadKeys.ARM_OUTCOMES] as List<*>
    assertEquals(
      setOf("control", "treatment"),
      outcomes.map { (it as Map<*, *>)[ExperimentPairPayloadKeys.ARM_ID] }.toSet(),
    )
    assertEquals(
      "completed",
      (outcomes.single { (it as Map<*, *>)[ExperimentPairPayloadKeys.ARM_ID] == "control" } as Map<*, *>)
        [ExperimentPairPayloadKeys.TERMINAL_STATUS],
    )
    val observations = loaded.pairPayload[ExperimentPairPayloadKeys.OBSERVATION_LEDGER] as List<*>
    assertEquals(1, observations.size)
    assertEquals(
      12.0,
      (
        (
          ((observations.single() as Map<*, *>)[ExperimentObservationPayloadKeys.MEASUREMENTS] as List<*>)
            .single() as Map<*, *>
        )[ExperimentObservationPayloadKeys.QUANTITY] as Number
      ).toDouble(),
    )
  }

  @Test
  fun `purging a workflow removes only its experiment pair resources`() {
    val tempDir = Files.createTempDirectory("experiment-pair-purge")
    val dbPath = tempDir.resolve("metrics.db")
    DatabaseRuntime.ensureDatabase(dbPath).close()
    val factory =
      sqliteDatabaseSessionFactory(userHome = tempDir, dbPathOverride = dbPath.toString(), environment = emptyMap())
    val store = SqliteExperimentPairOwnerStore(factory, null)
    store.save(
      ExperimentPairPersistedState(
        pairId = "pair-1",
        executionMode = ExperimentExecutionMode.GOAL_PAIR,
        selectedNames = emptyList(),
        armOrder = listOf(ExperimentArmId.CONTROL, ExperimentArmId.TREATMENT),
        randomSeed = "seed",
        pairPayload = pairPayload("pair-1"),
      ),
    )

    factory.transaction { unitOfWork ->
      unitOfWork.experimentPairs.deletePairsForWorkflowIds(listOf("workflow-1"))
    }

    assertNull(store.load("pair-1"))
  }

  @Test
  fun `report projection is durable and replaces the prior projection`() {
    val tempDir = Files.createTempDirectory("experiment-report-store")
    val dbPath = tempDir.resolve("metrics.db")
    DatabaseRuntime.ensureDatabase(dbPath).close()
    val factory =
      sqliteDatabaseSessionFactory(userHome = tempDir, dbPathOverride = dbPath.toString(), environment = emptyMap())
    val store = SqliteExperimentPairOwnerStore(factory, null)
    store.save(
      ExperimentPairPersistedState(
        pairId = "pair-report",
        executionMode = ExperimentExecutionMode.GOAL_PAIR,
        selectedNames = listOf("fixture-goal"),
        armOrder = listOf(ExperimentArmId.CONTROL, ExperimentArmId.TREATMENT),
        randomSeed = "seed",
        pairPayload =
          ExperimentPairPayload(
            pairPayload("pair-report").toMap() +
              (ExperimentPairPayloadKeys.SELECTED_EXPERIMENT_NAMES to listOf("fixture-goal")),
          ),
      ),
    )
    val report =
      mapOf(
        ExperimentReportPayloadKeys.CONTRACT_VERSION to "0.1",
        ExperimentReportPayloadKeys.PAIR_ID to "pair-report",
        ExperimentReportPayloadKeys.COHORT to "goal",
        ExperimentReportPayloadKeys.COMPLETENESS to "incomplete",
        ExperimentReportPayloadKeys.SELECTED_EXPERIMENT_NAMES to listOf("fixture-goal"),
        ExperimentReportPayloadKeys.DELIVERY_ARM to "control",
      )

    store.saveReport("pair-report", ExperimentPairPayload(report))

    assertEquals(report, store.loadReport("pair-report")?.toMap())
  }

  @Test
  fun `pair lease fences a second owner until expiry or release`() {
    val tempDir = Files.createTempDirectory("experiment-pair-lease")
    val dbPath = tempDir.resolve("metrics.db")
    DatabaseRuntime.ensureDatabase(dbPath).close()
    val factory =
      sqliteDatabaseSessionFactory(userHome = tempDir, dbPathOverride = dbPath.toString(), environment = emptyMap())
    val store = SqliteExperimentPairOwnerStore(factory, null)

    assertTrue(store.acquireLease("pair-lease", "owner-a", 1000L, 100L))
    assertFalse(store.acquireLease("pair-lease", "owner-b", 1050L, 100L))
    assertTrue(store.acquireLease("pair-lease", "owner-b", 1200L, 100L))
    store.releaseLease("pair-lease", "owner-b")
    assertTrue(store.acquireLease("pair-lease", "owner-a", 1300L, 100L))
  }

  private fun seedRecoveryData(store: SqliteExperimentPairOwnerStore) {
    store.save(
      ExperimentPairPersistedState(
        pairId = "pair-recovery",
        executionMode = ExperimentExecutionMode.GOAL_PAIR,
        selectedNames = listOf("fixture-goal"),
        armOrder = listOf(ExperimentArmId.CONTROL, ExperimentArmId.TREATMENT),
        randomSeed = "seed",
        pairPayload =
          ExperimentPairPayload(
            pairPayload("pair-recovery").toMap() +
              mapOf(
                ExperimentPairPayloadKeys.SELECTED_EXPERIMENT_NAMES to listOf("fixture-goal"),
                ExperimentPairPayloadKeys.ARM_OUTCOMES to
                  listOf(
                    mapOf(
                      ExperimentPairPayloadKeys.ARM_ID to "control",
                      ExperimentPairPayloadKeys.WORKFLOW_ID to "control-workflow",
                      ExperimentPairPayloadKeys.TERMINAL_STATUS to "completed",
                      ExperimentPairPayloadKeys.DEFERRED_PUBLICATION to true,
                    ),
                    mapOf(
                      ExperimentPairPayloadKeys.ARM_ID to "treatment",
                      ExperimentPairPayloadKeys.WORKFLOW_ID to "treatment-workflow",
                      ExperimentPairPayloadKeys.TERMINAL_STATUS to "running",
                      ExperimentPairPayloadKeys.DEFERRED_PUBLICATION to true,
                    ),
                  ),
              ),
          ),
      ),
    )
    assertTrue(
      store.importObservation(
        ExperimentPairPayload(
          observationPayload("recovery-observation").plus(
            mapOf(
              ExperimentObservationPayloadKeys.PAIR_ID to "pair-recovery",
              ExperimentObservationPayloadKeys.MEASUREMENTS to
                listOf(
                  mapOf(
                    ExperimentObservationPayloadKeys.METRIC_ID to "cost",
                    ExperimentObservationPayloadKeys.AVAILABILITY to "measured",
                    ExperimentObservationPayloadKeys.QUANTITY to 12.0,
                  ),
                ),
            ),
          ),
        ),
      ),
    )
  }

  private fun observationPayload(observationId: String): Map<String, Any?> =
    mapOf(
      ExperimentObservationPayloadKeys.CONTRACT_VERSION to EXPERIMENT_OBSERVATION_CONTRACT_VERSION,
      ExperimentObservationPayloadKeys.OBSERVATION_ID to observationId,
      ExperimentObservationPayloadKeys.PAIR_ID to "pair-1",
      ExperimentObservationPayloadKeys.ARM_ID to "control",
      ExperimentObservationPayloadKeys.RECORDED_AT to "2026-09-21T00:00:00Z",
      ExperimentObservationPayloadKeys.EVENT_IDENTITY to
        mapOf(
          ExperimentObservationPayloadKeys.WORKFLOW_ID to "wf-1",
          ExperimentObservationPayloadKeys.PHASE_ID to "implement",
          ExperimentObservationPayloadKeys.ATTEMPT to 1,
          ExperimentObservationPayloadKeys.EVENT_KIND to "usage",
        ),
      ExperimentObservationPayloadKeys.MEASUREMENTS to emptyList<Any>(),
    )

  private fun pairState(pairId: String): ExperimentPairPersistedState =
    ExperimentPairPersistedState(
      pairId = pairId,
      executionMode = ExperimentExecutionMode.GOAL_PAIR,
      selectedNames = emptyList(),
      armOrder = listOf(ExperimentArmId.CONTROL, ExperimentArmId.TREATMENT),
      randomSeed = "seed",
      pairPayload = pairPayload(pairId),
    )

  private fun pairPayload(pairId: String): ExperimentPairPayload =
    ExperimentPairPayload(
      mapOf(
        ExperimentPairPayloadKeys.CONTRACT_VERSION to EXPERIMENT_PAIR_CONTRACT_VERSION,
        ExperimentPairPayloadKeys.PAIR_ID to pairId,
        ExperimentPairPayloadKeys.EXECUTION_MODE to "goal_pair",
        ExperimentPairPayloadKeys.SELECTED_EXPERIMENT_NAMES to emptyList<String>(),
        ExperimentPairPayloadKeys.ARM_ORDER to listOf("control", "treatment"),
        ExperimentPairPayloadKeys.RANDOM_SEED to "seed",
        ExperimentPairPayloadKeys.DELIVERY_ARM to "control",
        ExperimentPairPayloadKeys.PAIR_STATUS to "failed",
        ExperimentPairPayloadKeys.FROZEN_INPUT_IDENTITY to
          mapOf(
            ExperimentPairPayloadKeys.REPOSITORY_IDENTITY to "repo",
            ExperimentPairPayloadKeys.SOURCE_COMMIT_SHA to "commit",
            ExperimentPairPayloadKeys.SOURCE_TREE_SHA to "tree",
            ExperimentPairPayloadKeys.SPEC_BUNDLE_HASH to "spec",
            ExperimentPairPayloadKeys.EFFECTIVE_CONFIG_HASH to "config",
            ExperimentPairPayloadKeys.SKILL_BILL_VERSION to "version",
          ),
        ExperimentPairPayloadKeys.ARM_OUTCOMES to
          listOf(
            mapOf(
              ExperimentPairPayloadKeys.ARM_ID to "control",
              ExperimentPairPayloadKeys.WORKFLOW_ID to "workflow-1",
              ExperimentPairPayloadKeys.TERMINAL_STATUS to "failed",
              ExperimentPairPayloadKeys.DEFERRED_PUBLICATION to true,
            ),
          ),
        ExperimentPairPayloadKeys.DELIVERY_STATUS to "deferred",
      ),
    )
}

private fun ExperimentPairPayload.toMap(): Map<String, Any?> =
  JsonCodec.anyToStringAnyMap(
    JsonCodec.jsonElementToValue(requireNotNull(JsonCodec.parseObjectOrNull(toJson()))),
  ) ?: error("Experiment pair payload must decode to an object.")
