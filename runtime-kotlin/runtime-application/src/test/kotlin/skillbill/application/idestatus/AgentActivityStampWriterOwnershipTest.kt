package skillbill.application.idestatus

import skillbill.idestatus.model.AgentActivityLabel
import skillbill.infrastructure.sqlite.SQLiteDatabaseSessionFactory
import skillbill.model.EnvironmentContext
import skillbill.ports.db.DatabaseSessionFactory
import skillbill.ports.diagnostics.RuntimeDiagnostics
import skillbill.ports.persistence.UnitOfWork
import java.nio.file.Files
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AgentActivityStampWriterOwnershipTest {
  @Test
  fun `independent writers with the same workflow id publish to separate database boundaries`() {
    val instant = Instant.parse("2026-09-16T10:00:00Z")
    val clock = Clock.fixed(instant, ZoneOffset.UTC)
    val workflowId = "wfl-shared"
    val firstDb = sqliteFactory("activity-owner-a")
    val secondDb = sqliteFactory("activity-owner-b")
    val firstWriter = AgentActivityStampWriter(firstDb, clock, NoopDiagnostics)
    val secondWriter = AgentActivityStampWriter(secondDb, clock, NoopDiagnostics)
    firstWriter.sink(workflowId, null).stamp(AgentActivityLabel.STDOUT)
    secondWriter.sink(workflowId, null).stamp(AgentActivityLabel.STDOUT)
    firstDb.read { unitOfWork ->
      assertNotNull(unitOfWork.agentActivityStamps.read(workflowId))
    }
    secondDb.read { unitOfWork ->
      assertNotNull(unitOfWork.agentActivityStamps.read(workflowId))
    }
  }

  @Test
  fun `failed write stays eligible for retry and does not erase a newer acknowledgement`() {
    val start = Instant.parse("2026-09-16T10:00:00Z")
    val clock = object : Clock() {
      private var current = start
      override fun getZone() = ZoneOffset.UTC
      override fun withZone(zone: ZoneId) = this
      override fun instant(): Instant {
        current = current.plusMillis(300)
        return current
      }
    }
    val base = sqliteFactory("activity-retry")
    val attempts = AtomicInteger(0)
    val database = object : DatabaseSessionFactory {
      override fun resolveDbPath() = base.resolveDbPath()
      override fun databaseExists() = base.databaseExists()
      override fun <T> read(block: (UnitOfWork) -> T) = base.read(block)
      override fun <T> transaction(block: (UnitOfWork) -> T) = base.transaction(block)
      override fun <T> selfManagedWrite(block: (UnitOfWork) -> T): T {
        if (attempts.getAndIncrement() == 0) {
          throw IllegalStateException("x".repeat(4096))
        }
        return base.selfManagedWrite(block)
      }
    }
    val diagnostics = RecordingDiagnostics()
    val writer = AgentActivityStampWriter(database, clock, diagnostics)
    val workflowId = "wfl-retry"
    writer.sink(workflowId, null).stamp(AgentActivityLabel.STDOUT)
    base.read { unitOfWork ->
      assertEquals(null, unitOfWork.agentActivityStamps.read(workflowId))
    }
    writer.sink(workflowId, null).stamp(AgentActivityLabel.DURABLE_PROGRESS)
    assertEquals(1, diagnostics.warnings.size)
    assertTrue(diagnostics.warnings.single().length <= 512)
    base.read { unitOfWork ->
      val stamp = unitOfWork.agentActivityStamps.read(workflowId)
      assertEquals(AgentActivityLabel.DURABLE_PROGRESS, stamp?.label)
    }
  }

  @Test
  fun `same-label activity remains debounced while evidence reads publish`() {
    val database = CountingDatabase(sqliteFactory("activity-debounce"))
    val writer = AgentActivityStampWriter(
      database,
      StepClock(Instant.parse("2026-09-16T10:00:00Z"), stepMillis = 1),
      NoopDiagnostics,
    )
    val sink = writer.sink("wfl-debounce", null)

    sink.stamp(AgentActivityLabel.STDOUT)
    sink.stamp(AgentActivityLabel.STDOUT)
    writer.recordEvidenceRead("wfl-debounce", null)

    assertEquals(2, database.writes.get())
    database.factory.read { unitOfWork ->
      assertEquals(
        AgentActivityLabel.EVIDENCE_READ,
        unitOfWork.agentActivityStamps.read("wfl-debounce")?.label,
      )
    }
  }

  @Test
  fun `concurrent completion preserves timestamp ordering and parent publication`() {
    val instant = Instant.parse("2026-09-16T10:00:00Z")
    val clock = StepClock(instant)
    val database = ControlledDatabase(sqliteFactory("activity-concurrency"))
    database.failFirstWrite = true
    val writer = AgentActivityStampWriter(database, clock, NoopDiagnostics)
    val workflowId = "wfl-child"
    val parentWorkflowId = "wfl-parent"
    val pool = Executors.newFixedThreadPool(2)
    val first = pool.submit {
      writer.sink(workflowId, parentWorkflowId).stamp(AgentActivityLabel.STDOUT)
    }
    assertTrue(database.firstWriteStarted.await(5, TimeUnit.SECONDS))
    val second = pool.submit {
      writer.recordEvidenceRead(workflowId, parentWorkflowId)
    }
    assertTrue(database.secondWriteFinished.await(5, TimeUnit.SECONDS))
    database.releaseFirstWrite.countDown()
    first.get(5, TimeUnit.SECONDS)
    second.get(5, TimeUnit.SECONDS)
    pool.shutdown()
    database.factory.read { unitOfWork ->
      val childStamp = assertNotNull(unitOfWork.agentActivityStamps.read(workflowId))
      val parentStamp = assertNotNull(unitOfWork.agentActivityStamps.read(parentWorkflowId))
      assertEquals(AgentActivityLabel.EVIDENCE_READ, childStamp.label)
      assertEquals(AgentActivityLabel.EVIDENCE_READ, parentStamp.label)
      assertEquals(instant.plusMillis(300), childStamp.recordedAt)
      assertEquals(instant.plusMillis(300), parentStamp.recordedAt)
    }
  }

  private fun sqliteFactory(name: String): SQLiteDatabaseSessionFactory {
    val tempDir = Files.createTempDirectory(name)
    return SQLiteDatabaseSessionFactory(EnvironmentContext(userHome = tempDir))
  }
}

private object NoopDiagnostics : RuntimeDiagnostics {
  override fun warning(message: String, error: Throwable?) = Unit

  override fun error(message: String, error: Throwable?) = Unit
}

private class RecordingDiagnostics : RuntimeDiagnostics {
  val warnings = mutableListOf<String>()

  override fun warning(message: String, error: Throwable?) {
    warnings += message
  }

  override fun error(message: String, error: Throwable?) = Unit
}

private class ControlledDatabase(val factory: SQLiteDatabaseSessionFactory) : DatabaseSessionFactory by factory {
  val firstWriteStarted = CountDownLatch(1)
  val releaseFirstWrite = CountDownLatch(1)
  val secondWriteFinished = CountDownLatch(1)
  var failFirstWrite = false
  private val writeCount = AtomicInteger(0)

  override fun <T> selfManagedWrite(block: (UnitOfWork) -> T): T {
    val writeNumber = writeCount.incrementAndGet()
    if (writeNumber == 1) {
      firstWriteStarted.countDown()
      check(releaseFirstWrite.await(5, TimeUnit.SECONDS))
      if (failFirstWrite) throw IllegalStateException("older activity write failed")
    }
    val result = factory.selfManagedWrite(block)
    if (writeNumber == 2) secondWriteFinished.countDown()
    return result
  }
}

private class CountingDatabase(val factory: SQLiteDatabaseSessionFactory) : DatabaseSessionFactory by factory {
  val writes = AtomicInteger(0)

  override fun <T> selfManagedWrite(block: (UnitOfWork) -> T): T {
    writes.incrementAndGet()
    return factory.selfManagedWrite(block)
  }
}

private class StepClock(
  private val start: Instant,
  private val stepMillis: Long = 300L,
) : Clock() {
  private val calls = AtomicInteger(0)

  override fun getZone() = ZoneOffset.UTC

  override fun withZone(zone: ZoneId) = this

  override fun instant(): Instant = start.plusMillis(calls.getAndIncrement() * stepMillis)
}
