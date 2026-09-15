package skillbill.telemetry

import skillbill.application.telemetry.sync.TelemetrySyncRuntime
import skillbill.infrastructure.sqlite.core.DatabaseRuntime
import skillbill.infrastructure.sqlite.telemetry.TelemetryOutboxStore
import skillbill.ports.repository.toFileLocation
import skillbill.ports.telemetry.TelemetryClient
import skillbill.ports.telemetry.TelemetryOutboxRepository
import skillbill.ports.telemetry.model.TELEMETRY_DELIVERY_ATTEMPT_BUDGET
import skillbill.ports.telemetry.model.TelemetryOutboxClaimRequest
import skillbill.ports.telemetry.model.TelemetryOutboxRecord
import skillbill.telemetry.model.RemoteStatsRequest
import skillbill.telemetry.model.TelemetryDeliveryOutcome
import skillbill.telemetry.model.TelemetryDeliveryReport
import skillbill.telemetry.model.TelemetryProxyCapabilities
import skillbill.telemetry.model.TelemetryRemoteStatsResult
import skillbill.telemetry.model.TelemetrySettings
import skillbill.telemetry.model.TelemetrySyncStatus
import java.io.IOException
import java.nio.file.Files
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

private val NOW: Instant = Instant.parse("2026-09-15T10:00:00Z")

class TelemetryDeliveryRecoveryTest {
  // SKILL-236 AC-002: the relay may hand the batch upstream and then lose the acknowledgement behind
  // a timeout or its own 502. Discarding the rows loses delivered events; re-minting their identity
  // makes the retry arrive as a fresh duplicate. The row must stay recoverable, unchanged.
  @Test
  fun `an unconfirmed delivery keeps one recoverable entry with its original identity`() {
    withOutbox { store ->
      store.enqueue(eventName = "skillbill_review_finished", payloadJson = """{"run":"r-1"}""")
      val mintedIdentity = store.listPending().single().eventUuid

      val result =
        TelemetrySyncRuntime.syncTelemetry(
          settings(),
          store,
          StubTelemetryClient(TelemetryDeliveryOutcome.UNKNOWN),
          NOW,
        )

      assertEquals(TelemetrySyncStatus.FAILED, result.status)
      val pending = store.listPending()
      assertEquals(1, pending.size, "An unconfirmed batch must stay queued exactly once.")
      assertEquals(mintedIdentity, pending.single().eventUuid, "The retry must reuse the original identity.")
      assertTrue(store.latestError().orEmpty().contains("unconfirmed"))
    }
  }

  // SKILL-236 AC-003: a send that succeeds and then fails to record locally used to leave the rows
  // unclaimed and pending, so the very next loop pass resent the whole batch, without bound.
  @Test
  fun `a failed local acknowledgement does not resend the batch without bound`() {
    withOutbox { store ->
      repeat(3) { index ->
        store.enqueue(eventName = "skillbill_goal_finished", payloadJson = """{"i":$index}""")
      }
      val client = StubTelemetryClient(TelemetryDeliveryOutcome.ACCEPTED)
      val brokenAcknowledgement =
        object : TelemetryOutboxRepository by store {
          override fun markSynced(eventIds: List<Long>) {
            throw IOException("disk full")
          }
        }

      val result =
        TelemetrySyncRuntime.syncTelemetry(settings(batchSize = 1), brokenAcknowledgement, client, NOW)

      assertEquals(TelemetrySyncStatus.FAILED, result.status)
      assertEquals(1, client.sentBatches.size, "The invocation must stop, not resend on every pass.")
      assertEquals(3, store.pendingCount(), "No row may be lost when the acknowledgement fails.")
      assertTrue(store.latestError().orEmpty().contains("local acknowledgement failed"))
    }
  }

  // SKILL-236 AC-003: autoSync runs at every CLI completion and every MCP tool call, so a few
  // offline minutes produce dozens of unconfirmed attempts. Charging them to the permanent attempt
  // budget would leave the queue blocked forever once connectivity returned, with no path back.
  @Test
  fun `an unconfirmed transport failure leaves the queue drainable once delivery recovers`() {
    withOutbox { store ->
      store.enqueue(eventName = "skillbill_goal_finished", payloadJson = "{}")
      val unreachable = StubTelemetryClient(TelemetryDeliveryOutcome.UNKNOWN)

      repeat(TELEMETRY_DELIVERY_ATTEMPT_BUDGET * 2) {
        TelemetrySyncRuntime.syncTelemetry(settings(), store, unreachable, NOW)
      }

      assertEquals(
        0,
        store.blockedCount(TELEMETRY_DELIVERY_ATTEMPT_BUDGET),
        "An unconfirmed delivery is not a receiver refusal and must not exhaust the budget.",
      )
      val recovered =
        TelemetrySyncRuntime.syncTelemetry(
          settings(),
          store,
          StubTelemetryClient(TelemetryDeliveryOutcome.ACCEPTED),
          NOW,
        )

      assertEquals(TelemetrySyncStatus.SYNCED, recovered.status)
      assertEquals(0, store.pendingCount(), "The queue must drain once delivery works again.")
    }
  }

  // SKILL-236 AC-006: recording delivery health must not feed the drain it is failing to complete.
  // Every repeated failure that enqueued a diagnostic event grew the queue it could not drain.
  @Test
  fun `repeated delivery failure enqueues no new telemetry and the drain terminates`() {
    withOutbox { store ->
      store.enqueue(eventName = "skillbill_goal_finished", payloadJson = "{}")
      val client =
        StubTelemetryClient(TelemetryDeliveryOutcome.REJECTED, detail = "HTTP 422: unknown event property")

      repeat(10) { TelemetrySyncRuntime.syncTelemetry(settings(), store, client, NOW) }

      assertEquals(1, store.pendingCount(), "The failing drain must not enqueue anything of its own.")
      assertTrue(
        client.sentBatches.size < 10,
        "Attempt accounting must stop the replay before every invocation retries it.",
      )
      assertTrue(
        store.latestError().orEmpty().contains("HTTP 422: unknown event property"),
        "A blocked row must carry the receiver's refusal, not only that one happened.",
      )
    }
  }

  // SKILL-236 AC-003/AC-006: a client failure other than a transport IOException escaped the drain
  // with the batch still claimed and no recorded cause, so the rows were undeliverable until the
  // five-minute lease expired and autoSync's broad catch swallowed the reason.
  @Test
  fun `an unexpected client failure is recorded and releases the batch instead of escaping`() {
    withOutbox { store ->
      store.enqueue(eventName = "skillbill_goal_finished", payloadJson = "{}")
      val throwingClient =
        object : TelemetryClient by StubTelemetryClient(TelemetryDeliveryOutcome.ACCEPTED) {
          override fun sendBatch(
            settings: TelemetrySettings,
            rows: List<TelemetryOutboxRecord>,
          ): TelemetryDeliveryReport = error("relay handshake blew up")
        }

      val result =
        TelemetrySyncRuntime.autoSyncTelemetry(settings(), store, throwingClient, NOW)

      assertEquals(TelemetrySyncStatus.FAILED, result?.status, "The failure must be reported, not swallowed.")
      assertEquals(1, store.pendingCount(), "The queue must not grow from its own failure.")
      assertTrue(store.latestError().orEmpty().contains("relay handshake blew up"))
      assertEquals(
        1,
        store.claimPending(
          TelemetryOutboxClaimRequest(
            claimToken = "next-drain",
            limit = 10,
            claimedAt = NOW,
            reclaimBefore = NOW,
          ),
        ).size,
        "The batch must be released for the next drain rather than held until the lease expires.",
      )
    }
  }

  // SKILL-236 AC-003: with every queued row held by a concurrent drain's live claim, this drain
  // delivered nothing yet reported `synced` with zero events, so a caller keying off status alone
  // read a stalled drain as a clean one.
  @Test
  fun `a drain that claims nothing while rows stay queued does not report a clean sync`() {
    withOutbox { store ->
      store.enqueue(eventName = "skillbill_goal_finished", payloadJson = "{}")
      store.claimPending(
        TelemetryOutboxClaimRequest(
          claimToken = "concurrent-drain",
          limit = 10,
          claimedAt = NOW,
          reclaimBefore = NOW,
        ),
      )
      val client = StubTelemetryClient(TelemetryDeliveryOutcome.ACCEPTED)

      val result = TelemetrySyncRuntime.syncTelemetry(settings(), store, client, NOW)

      assertNotEquals(TelemetrySyncStatus.SYNCED, result.status)
      assertEquals(1, result.pendingEvents, "The queued row must stay visible as pending.")
      assertTrue(client.sentBatches.isEmpty(), "A claimed row must not be sent twice.")
    }
  }

  private fun withOutbox(block: (TelemetryOutboxStore) -> Unit) {
    val dbPath = Files.createTempDirectory("telemetry-delivery-recovery").resolve("metrics.db")
    DatabaseRuntime.ensureDatabase(dbPath).use { connection ->
      block(TelemetryOutboxStore(connection))
    }
  }

  private fun settings(batchSize: Int = 50): TelemetrySettings = TelemetrySettings(
    configPath = Files.createTempFile("telemetry-recovery", ".json").toFileLocation(),
    level = "anonymous",
    enabled = true,
    installId = "test-install-id",
    proxyUrl = "https://telemetry.example.dev/ingest",
    customProxyUrl = "https://telemetry.example.dev/ingest",
    batchSize = batchSize,
  )
}

private class StubTelemetryClient(
  private val outcome: TelemetryDeliveryOutcome,
  private val detail: String = "",
) : TelemetryClient {
  val sentBatches = mutableListOf<List<String>>()

  override fun sendBatch(settings: TelemetrySettings, rows: List<TelemetryOutboxRecord>): TelemetryDeliveryReport {
    sentBatches += rows.map { it.eventUuid }
    return TelemetryDeliveryReport(outcome, detail)
  }

  override fun fetchProxyCapabilities(settings: TelemetrySettings): TelemetryProxyCapabilities =
    error("Unexpected fetchProxyCapabilities")

  override fun fetchRemoteStats(settings: TelemetrySettings, request: RemoteStatsRequest): TelemetryRemoteStatsResult =
    error("Unexpected fetchRemoteStats")
}
