package skillbill.infrastructure.sqlite

import skillbill.infrastructure.sqlite.sqliteDatabaseSessionFactory

import skillbill.contracts.JsonCodec
import skillbill.contracts.SharedPayloadKeys
import skillbill.contracts.workflow.ValidationEvidencePayloadKeys
import skillbill.infrastructure.sqlite.core.DatabaseRuntime
import skillbill.model.EnvironmentContext
import skillbill.ports.featuretask.model.FeatureTaskPhaseSettlement
import skillbill.ports.featuretask.model.FeatureTaskPhaseSettlementKind
import skillbill.workflow.taskruntime.asWorkflowArtifactEntry
import skillbill.workflow.taskruntime.decodeValidationGateExecutionEvidenceFromArtifact
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeValidationGateExecutionEvidence
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeValidationGateRunRecord
import skillbill.workflow.taskruntime.model.ValidationGateCacheMode
import skillbill.workflow.taskruntime.model.ValidationGateRunOutcome
import java.nio.file.Files
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
class SqliteValidationGateEvidenceRoundTripTest {
  @Test
  fun `validation gate execution evidence survives sqlite round trip`() {
    val repo = repository()
    persistEvidence(repo, gateEvidence())
    val decoded = readEvidence(repo)

    assertRoundTrip(decoded)
  }

  private fun repository(): SqliteFeatureTaskPhaseSettlementRepository {
    val tempDir = Files.createTempDirectory("validation-gate-evidence")
    val dbPath = tempDir.resolve("metrics.db")
    DatabaseRuntime.ensureDatabase(dbPath).close()
    return SqliteFeatureTaskPhaseSettlementRepository(
      sqliteDatabaseSessionFactory(userHome = tempDir, dbPathOverride = dbPath.toString(), environment = emptyMap()),
    )
  }

  private fun gateEvidence(): FeatureTaskRuntimeValidationGateExecutionEvidence =
    FeatureTaskRuntimeValidationGateExecutionEvidence.fromGateMeasurements(
      listOf(
        FeatureTaskRuntimeValidationGateRunRecord(
          durationMs = 4,
          outcome = ValidationGateRunOutcome.FAILED,
          cacheMode = ValidationGateCacheMode.CACHE_ELIGIBLE,
          executedWorkUnits = 2,
          executedChecks = listOf("runtime-engine|compileKotlin"),
        ),
        FeatureTaskRuntimeValidationGateRunRecord(
          durationMs = 6,
          outcome = ValidationGateRunOutcome.PASSED,
          cacheMode = ValidationGateCacheMode.FORCED_FULL,
          executedWorkUnits = 2,
          executedChecks = listOf("runtime-engine|compileKotlin", "runtime-engine|test"),
        ),
      ),
    )

  private fun persistEvidence(
    repo: SqliteFeatureTaskPhaseSettlementRepository,
    gateEvidence: FeatureTaskRuntimeValidationGateExecutionEvidence,
  ) {
    val envelopeJson = JsonCodec.mapToJsonString(
      mapOf(
        SharedPayloadKeys.STATUS to "completed",
        SharedPayloadKeys.PRODUCED_OUTPUTS to mapOf(
          ValidationEvidencePayloadKeys.VALIDATION_RESULT to
            gateEvidence.asWorkflowArtifactEntry("checkpoint"),
        ),
      ),
    )
    repo.upsert(
      FeatureTaskPhaseSettlement(
        workflowId = "wftr-gate-evidence",
        phaseId = "validate",
        attempt = 1,
        kind = FeatureTaskPhaseSettlementKind.Complete,
        envelopeJson = envelopeJson,
        recordedAt = Instant.now().toString(),
      ),
    )
  }

  private fun readEvidence(
    repo: SqliteFeatureTaskPhaseSettlementRepository,
  ): FeatureTaskRuntimeValidationGateExecutionEvidence {
    val stored = requireNotNull(repo.find("wftr-gate-evidence", "validate", 1))
    val validationResult = JsonCodec.anyToStringAnyMap(
      JsonCodec.anyToStringAnyMap(
        JsonCodec.parseObjectOrNull(stored.envelopeJson)
          ?.let(JsonCodec::jsonElementToValue)
          ?.let(JsonCodec::anyToStringAnyMap)
          ?.get(SharedPayloadKeys.PRODUCED_OUTPUTS),
      )?.get(ValidationEvidencePayloadKeys.VALIDATION_RESULT),
    ) ?: error("validation_result missing")
    return requireNotNull(decodeValidationGateExecutionEvidenceFromArtifact(validationResult, "validate"))
  }

  private fun assertRoundTrip(decoded: FeatureTaskRuntimeValidationGateExecutionEvidence) {
    assertEquals(listOf("runtime-engine|compileKotlin", "runtime-engine|test"), decoded.checks)
    assertEquals(2, decoded.gateRunCount)
    assertEquals(
      listOf(ValidationGateCacheMode.CACHE_ELIGIBLE, ValidationGateCacheMode.FORCED_FULL),
      decoded.gateRuns.map { it.cacheMode },
    )
    assertEquals(
      listOf(ValidationGateRunOutcome.FAILED, ValidationGateRunOutcome.PASSED),
      decoded.gateRuns.map { it.outcome },
    )
    assertEquals(listOf(2, 2), decoded.gateRuns.map { it.executedWorkUnits })
    assertEquals(
      listOf(
        listOf("runtime-engine|compileKotlin"),
        listOf("runtime-engine|compileKotlin", "runtime-engine|test"),
      ),
      decoded.gateRuns.map { it.executedChecks },
    )
  }
}
