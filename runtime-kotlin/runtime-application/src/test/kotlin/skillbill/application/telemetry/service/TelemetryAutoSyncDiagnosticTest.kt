package skillbill.application.telemetry.service

import skillbill.ports.concurrency.InterruptSignalPort
import skillbill.ports.db.DatabaseSessionFactory
import skillbill.ports.diagnostics.RuntimeDiagnostics
import skillbill.ports.goalrunner.EmptyGoalPlanningPreparationRepository
import skillbill.ports.goalrunner.EmptyGoalRunnerControlRepository
import skillbill.ports.learning.LearningRepository
import skillbill.ports.persistence.UnitOfWork
import skillbill.ports.persistence.UnitOfWorkDefaults
import skillbill.ports.repository.toFileLocation
import skillbill.ports.review.repository.ReviewRepository
import skillbill.ports.telemetry.lifecycle.LifecycleTelemetryRepository
import skillbill.ports.telemetry.model.TelemetryOutboxClaimRequest
import skillbill.ports.telemetry.model.TelemetryOutboxRecord
import skillbill.ports.telemetry.model.TelemetryOutboxSettlementResult
import skillbill.ports.telemetry.model.TelemetryReconciliationRequest
import skillbill.ports.telemetry.model.TelemetryReconciliationResult
import skillbill.ports.telemetry.transport.TelemetryClient
import skillbill.ports.telemetry.transport.TelemetryConfigStore
import skillbill.ports.telemetry.transport.TelemetryOutboxRepository
import skillbill.ports.telemetry.transport.TelemetryReconciliationRepository
import skillbill.ports.telemetry.transport.TelemetrySettingsProvider
import skillbill.ports.work.EmptyWorkListRepository
import skillbill.ports.workflow.WorkflowStateRepository
import skillbill.telemetry.model.RemoteStatsRequest
import skillbill.telemetry.model.TelemetryConfigDocument
import skillbill.telemetry.model.TelemetryDeliveryReport
import skillbill.telemetry.model.TelemetryOpenDocument
import skillbill.telemetry.model.TelemetryProxyCapabilities
import skillbill.telemetry.model.TelemetryRemoteStatsResult
import skillbill.telemetry.model.TelemetrySettings
import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class TelemetryAutoSyncDiagnosticTest {
  @Test
  fun `manual sync propagates cancellation from stale-session reconciliation`() {
    val service =
      telemetryService(
        diagnostics = RecordingDiagnostics(),
        failingEnqueue = false,
        reconciliationFailure = CancellationException("reconciliation-cancelled"),
      )

    assertFailsWith<CancellationException> {
      service.sync()
    }
  }

  @Test
  fun `manual sync propagates interruption from stale-session reconciliation`() {
    val interruptSignal = RecordingInterruptSignalPort()
    val service =
      telemetryService(
        diagnostics = RecordingDiagnostics(),
        failingEnqueue = false,
        reconciliationFailure = InterruptedException("reconciliation-interrupted"),
        interruptSignal = interruptSignal,
      )

    assertFailsWith<InterruptedException> {
      service.sync()
    }
    assertEquals(1, interruptSignal.restoreCount)
  }

  @Test
  fun `auto sync records a payload free warning when sync fails`() {
    val diagnostics = RecordingDiagnostics()
    val service = telemetryService(diagnostics, failingEnqueue = false)

    service.autoSync()

    assertTrue(
      diagnostics.warnings.any { it.first == TELEMETRY_BACKGROUND_SYNC_FAILURE_SIGNATURE },
      diagnostics.warnings.toString(),
    )
    assertTrue(diagnostics.warnings.none { it.first.contains("skillbill_") })
  }

  @Test
  fun `auto sync still records the bounded warning when outbox enqueue fails`() {
    val diagnostics = RecordingDiagnostics()
    val service = telemetryService(diagnostics, failingEnqueue = true)

    service.autoSync()

    assertTrue(diagnostics.warnings.count { it.first == TELEMETRY_BACKGROUND_SYNC_FAILURE_SIGNATURE } >= 2)
  }

  private fun telemetryService(
    diagnostics: RuntimeDiagnostics,
    failingEnqueue: Boolean,
    reconciliationFailure: Throwable? = null,
    interruptSignal: InterruptSignalPort = NoopInterruptSignalPort,
  ): TelemetryService {
    val outbox = PendingOutbox(failingEnqueue)
    val database = DiagnosticDatabaseSessionFactory(outbox, reconciliationFailure)
    val settingsProvider = EnabledSettingsProvider()
    return TelemetryService(
      database = database,
      settingsProvider = settingsProvider,
      telemetryClient = FailingTelemetryRelay(),
      clock = Clock.fixed(Instant.parse("2026-09-15T10:00:00Z"), ZoneOffset.UTC),
      levelMutationService =
        TelemetryLevelMutationService(
          database = database,
          settingsProvider = settingsProvider,
          configStore = DiagnosticTelemetryConfigStore(),
        ),
      diagnostics = diagnostics,
      interruptSignal = interruptSignal,
    )
  }
}

private class RecordingDiagnostics : RuntimeDiagnostics {
  val warnings = mutableListOf<Pair<String, Throwable?>>()

  override fun warning(
    message: String,
    error: Throwable?,
  ) {
    warnings += message to error
  }

  override fun error(
    message: String,
    error: Throwable?,
  ) = Unit
}

private class FailingTelemetryRelay : TelemetryClient {
  override fun sendBatch(
    settings: TelemetrySettings,
    rows: List<TelemetryOutboxRecord>,
  ): TelemetryDeliveryReport = error("relay handshake blew up")

  override fun fetchProxyCapabilities(settings: TelemetrySettings): TelemetryProxyCapabilities = error("unexpected")

  override fun fetchRemoteStats(
    settings: TelemetrySettings,
    request: RemoteStatsRequest,
  ): TelemetryRemoteStatsResult = error("unexpected")
}

private class EnabledSettingsProvider : TelemetrySettingsProvider {
  override fun load(materialize: Boolean): TelemetrySettings =
    TelemetrySettings(
      configPath = Files.createTempFile("auto-sync", ".json").toFileLocation(),
      level = "anonymous",
      enabled = true,
      installId = "install",
      proxyUrl = "https://telemetry.example.dev/ingest",
      customProxyUrl = "https://telemetry.example.dev/ingest",
      batchSize = 50,
    )
}

private class DiagnosticTelemetryConfigStore : TelemetryConfigStore {
  override fun stateDir(): Path = Path.of("/fake")

  override fun configPath(): Path = Path.of("/fake/config.json")

  override fun read(): TelemetryConfigDocument? = null

  override fun ensure(): TelemetryConfigDocument =
    TelemetryConfigDocument(
      TelemetryOpenDocument.from(
        mapOf(
          "install_id" to "install",
          "telemetry" to mapOf("level" to "anonymous", "proxy_url" to "", "batch_size" to 50),
        ),
      ),
    )

  override fun write(document: TelemetryConfigDocument) = Unit
}

private class PendingOutbox(
  private val failingEnqueue: Boolean,
) : TelemetryOutboxRepository {
  override fun enqueue(
    eventName: String,
    payloadJson: String,
  ): Long {
    if (failingEnqueue && eventName == RUNTIME_EXCEPTION_EVENT) {
      error("outbox unavailable")
    }
    return 1L
  }

  override fun claimPending(request: TelemetryOutboxClaimRequest): List<TelemetryOutboxRecord> =
    listOf(
      TelemetryOutboxRecord(
        id = 1L,
        eventName = "skillbill_goal_finished",
        payloadJson = "{}",
        createdAt = "2026-09-15T10:00:00Z",
        syncedAt = null,
        lastError = "",
      ),
    )

  override fun pendingCount(): Int = 1

  override fun blockedCount(attemptBudget: Int): Int = 0

  override fun latestError(): String? = null

  override fun lastSyncedAt(): String? = null

  override fun markSynced(
    eventIds: List<Long>,
    claimToken: String,
  ): TelemetryOutboxSettlementResult = TelemetryOutboxSettlementResult.forRequest(eventIds, updatedRows = eventIds.size)

  override fun markFailed(
    eventIds: List<Long>,
    claimToken: String,
    lastError: String,
  ): TelemetryOutboxSettlementResult = TelemetryOutboxSettlementResult.forRequest(eventIds, updatedRows = eventIds.size)

  override fun markUnconfirmed(
    eventIds: List<Long>,
    claimToken: String,
    lastError: String,
  ): TelemetryOutboxSettlementResult = TelemetryOutboxSettlementResult.forRequest(eventIds, updatedRows = eventIds.size)

  override fun clear(): Int = 0
}

private class DiagnosticDatabaseSessionFactory(
  private val outbox: TelemetryOutboxRepository,
  private val reconciliationFailure: Throwable? = null,
) : DatabaseSessionFactory {
  override fun resolveDbPath(): Path = Path.of("diagnostic.db")

  override fun databaseExists(): Boolean = true

  override fun <T> read(block: (UnitOfWork) -> T): T = block(unitOfWork())

  override fun <T> selfManagedWrite(block: (UnitOfWork) -> T): T = transaction(block)

  override fun <T> transaction(block: (UnitOfWork) -> T): T = block(unitOfWork())

  private fun unitOfWork(): UnitOfWork =
    object : UnitOfWorkDefaults() {
      override val dbPath: Path = Path.of("diagnostic.db")
      override val telemetryOutbox: TelemetryOutboxRepository = outbox
      override val telemetryReconciliation: TelemetryReconciliationRepository =
        object : TelemetryReconciliationRepository {
          override fun reconcileStaleSessions(request: TelemetryReconciliationRequest): TelemetryReconciliationResult {
            reconciliationFailure?.let { throw it }
            return TelemetryReconciliationResult.Empty
          }
        }
      override val workflowStates: WorkflowStateRepository
        get() = error("not exercised")
      override val workList = EmptyWorkListRepository
      override val learnings: LearningRepository
        get() = error("not exercised")
      override val reviews: ReviewRepository
        get() = error("not exercised")
      override val lifecycleTelemetry: LifecycleTelemetryRepository
        get() = error("not exercised")
      override val goalPlanningPreparations = EmptyGoalPlanningPreparationRepository
      override val goalRunnerControls = EmptyGoalRunnerControlRepository
    }
}

private object NoopInterruptSignalPort : InterruptSignalPort {
  override fun restore() = Unit
}

private class RecordingInterruptSignalPort : InterruptSignalPort {
  var restoreCount = 0

  override fun restore() {
    restoreCount++
  }
}
