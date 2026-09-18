package skillbill.infrastructure.sqlite

import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import skillbill.error.DatabaseAccessError
import skillbill.error.DatabaseAccessOperation
import skillbill.infrastructure.sqlite.core.DatabaseIdentity
import skillbill.infrastructure.sqlite.core.DatabaseMigration
import skillbill.infrastructure.sqlite.core.DatabaseMigrations
import skillbill.infrastructure.sqlite.core.DatabaseWriteReadinessGate
import skillbill.ports.workflow.model.WorkflowStateRecord
import skillbill.workflow.model.FeatureTaskWorkflowMode
import skillbill.workflow.model.WorkflowStatus
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.sql.DriverManager
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@Execution(ExecutionMode.SAME_THREAD)
class DatabaseWriteReadinessTest {
  @Test
  fun `warm readiness cache avoids duplicate schema establishment work`() {
    var executions = 0
    val gate = DatabaseWriteReadinessGate { executions += 1 }
    val tempDir = Files.createTempDirectory("skillbill-write-readiness-gate-warm")
    val dbPath = tempDir.resolve("metrics.db")
    gate.ensureReady(dbPath)
    val afterFirst = executions
    gate.ensureReady(dbPath)
    assertEquals(afterFirst, executions)
  }

  @Test
  fun `repeated write acquisitions succeed on a warmed database`() {
    val tempDir = Files.createTempDirectory("skillbill-write-readiness")
    val dbPath = tempDir.resolve("metrics.db")
    val database = sqliteSessionFactoryForTests(
      userHome = tempDir,
      dbPathOverride = dbPath.toString(),
      environment = emptyMap(),
    )

    database.transaction { unitOfWork ->
      unitOfWork.workflowStates.saveFeatureTaskRuntimeWorkflow(sampleWorkflow("wftr-readiness-1"))
    }
    database.transaction { unitOfWork ->
      unitOfWork.workflowStates.saveFeatureTaskRuntimeWorkflow(sampleWorkflow("wftr-readiness-2"))
    }
    database.selfManagedWrite { unitOfWork ->
      assertNotNull(unitOfWork.workflowStates.getFeatureTaskRuntimeWorkflow("wftr-readiness-1"))
    }

    relocateDatabaseFromPath(dbPath, tempDir.resolve("metrics-archived.db"))
    database.transaction { unitOfWork ->
      unitOfWork.workflowStates.saveFeatureTaskRuntimeWorkflow(sampleWorkflow("wftr-readiness-3"))
    }
    assertNotNull(
      database.read { it.workflowStates.getFeatureTaskRuntimeWorkflow("wftr-readiness-3") },
    )
  }

  @Test
  fun `truncating database file at the same path forces schema re-establishment`() {
    var executions = 0
    val gate = DatabaseWriteReadinessGate { executions += 1 }
    val tempDir = Files.createTempDirectory("skillbill-write-readiness-truncate")
    val dbPath = tempDir.resolve("metrics.db")
    gate.ensureReady(dbPath)
    val afterInit = executions
    Files.write(dbPath, ByteArray(0))
    gate.ensureReady(dbPath)
    assertTrue(executions > afterInit)
  }

  @Test
  fun `appending a migration changes identity and re-establishes readiness once`() {
    var executions = 0
    val gate = DatabaseWriteReadinessGate { executions += 1 }
    val tempDir = Files.createTempDirectory("skillbill-write-readiness-version")
    val dbPath = tempDir.resolve("metrics.db")
    gate.ensureReady(dbPath)
    val afterInit = executions
    val initialIdentity = assertNotNull(DatabaseIdentity.read(dbPath))
    val appendedMigration = DatabaseMigration(
      version = DatabaseMigrations.migrations.maxOf { migration -> migration.version } + 1,
      name = "test-only-appended-readiness-migration",
    ) {}

    DriverManager.getConnection("jdbc:sqlite:$dbPath").use { connection ->
      DatabaseMigrations.apply(
        connection,
        migrationSet = DatabaseMigrations.migrations + appendedMigration,
      )
    }

    val appendedIdentity = assertNotNull(DatabaseIdentity.read(dbPath))
    assertTrue(appendedIdentity.userVersion > initialIdentity.userVersion)
    gate.ensureReady(dbPath)
    assertEquals(afterInit + 1, executions)
    gate.ensureReady(dbPath)
    assertEquals(afterInit + 1, executions)
  }

  @Test
  fun `concurrent readiness initialization publishes one recoverable result`() {
    val tempDir = Files.createTempDirectory("skillbill-write-readiness-concurrent")
    val dbPath = tempDir.resolve("metrics.db")
    var executions = 0
    val gate = DatabaseWriteReadinessGate { executions += 1 }
    val ready = CountDownLatch(1)
    val executor = Executors.newFixedThreadPool(2)
    val failures = mutableListOf<Throwable>()
    repeat(2) {
      executor.submit {
        ready.await()
        runCatching { gate.ensureReady(dbPath) }.exceptionOrNull()?.let { failure ->
          synchronized(failures) { failures += failure }
        }
      }
    }
    ready.countDown()
    executor.shutdown()
    assertTrue(executor.awaitTermination(30, TimeUnit.SECONDS))

    assertTrue(failures.isEmpty(), failures.joinToString { failure -> failure.toString() })
    assertEquals(1, executions)
    assertTrue(Files.isRegularFile(dbPath))
  }

  @Test
  fun `unreadable database file surfaces read DatabaseAccessError from ensureReady`() {
    val tempDir = Files.createTempDirectory("skillbill-write-readiness-unreadable")
    val dbPath = tempDir.resolve("metrics.db")
    Files.writeString(dbPath, "not-a-sqlite-database")
    var establishments = 0
    val gate = DatabaseWriteReadinessGate { establishments += 1 }

    val error = assertFailsWith<DatabaseAccessError> {
      gate.ensureReady(dbPath)
    }
    assertEquals(DatabaseAccessOperation.READ, error.operation)
    assertEquals(0, establishments)
  }

  @Test
  fun `failed readiness is not published and recovery retries initialization`() {
    val tempDir = Files.createTempDirectory("skillbill-write-readiness-failure")
    val invalidPath = tempDir.resolve("metrics.db")
    Files.createDirectory(invalidPath)
    val database = sqliteSessionFactoryForTests(
      userHome = tempDir,
      dbPathOverride = invalidPath.toString(),
      environment = emptyMap(),
    )

    assertFailsWith<DatabaseAccessError> {
      database.transaction { Unit }
    }

    Files.delete(invalidPath)
    database.transaction { unitOfWork ->
      unitOfWork.workflowStates.saveFeatureTaskRuntimeWorkflow(sampleWorkflow("wftr-recovered"))
    }
    assertNotNull(
      database.read { it.workflowStates.getFeatureTaskRuntimeWorkflow("wftr-recovered") },
    )
  }

  private fun relocateDatabaseFromPath(dbPath: Path, archivePath: Path) {
    Files.deleteIfExists(archivePath)
    Files.move(dbPath, archivePath, StandardCopyOption.ATOMIC_MOVE)
  }

  private fun sampleWorkflow(workflowId: String): WorkflowStateRecord = WorkflowStateRecord(
    workflowId = workflowId,
    sessionId = "session-$workflowId",
    workflowName = "bill-feature-task",
    contractVersion = "0.3",
    workflowStatus = WorkflowStatus.PENDING.wireValue,
    currentStepId = "plan",
    stepsJson = "[]",
    artifactsJson = "{}",
    issueKey = "SKILL-356",
    startedAt = "2026-09-18T00:00:00Z",
    updatedAt = "2026-09-18T00:00:00Z",
    finishedAt = null,
    mode = FeatureTaskWorkflowMode.RUNTIME,
  )
}
