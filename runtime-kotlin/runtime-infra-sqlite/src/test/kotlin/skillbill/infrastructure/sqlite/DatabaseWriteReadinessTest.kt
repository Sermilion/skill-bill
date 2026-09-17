package skillbill.infrastructure.sqlite

import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import skillbill.error.DatabaseAccessError
import skillbill.infrastructure.sqlite.core.DatabaseIdentity
import skillbill.infrastructure.sqlite.core.DatabaseRuntime
import skillbill.infrastructure.sqlite.core.DatabaseWriteReadinessGate
import skillbill.model.EnvironmentContext
import skillbill.ports.workflow.model.FeatureTaskWorkflowMode
import skillbill.ports.workflow.model.WorkflowStateRecord
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
import kotlin.test.assertTrue
import skillbill.workflow.model.WorkflowStatus

@Execution(ExecutionMode.SAME_THREAD)
class DatabaseWriteReadinessTest {
  @Test
  fun `warm readiness cache performs one identity observation per write acquisition`() {
    val tempDir = Files.createTempDirectory("skillbill-write-readiness-identity-count")
    val dbPath = tempDir.resolve("metrics.db")
    DatabaseRuntime.resetWriteReadinessForTests()
    DatabaseIdentity.resetIdentityReadCountForTests()
    val database = SQLiteDatabaseSessionFactory(
      EnvironmentContext(
        dbPathOverride = dbPath.toString(),
        environment = emptyMap(),
        userHome = tempDir,
      ),
    )
    database.transaction { unitOfWork ->
      unitOfWork.workflowStates.saveFeatureTaskRuntimeWorkflow(sampleWorkflow("wftr-identity-warm"))
    }
    DatabaseIdentity.resetIdentityReadCountForTests()
    database.transaction { unitOfWork ->
      unitOfWork.workflowStates.saveFeatureTaskRuntimeWorkflow(sampleWorkflow("wftr-identity-warm-2"))
    }
    assertEquals(1, DatabaseIdentity.identityReadCountForTests)
  }

  @Test
  fun `repeated write acquisitions establish schema once until database identity changes`() {
    val tempDir = Files.createTempDirectory("skillbill-write-readiness")
    val dbPath = tempDir.resolve("metrics.db")
    DatabaseRuntime.resetWriteReadinessForTests()
    val database = SQLiteDatabaseSessionFactory(
      EnvironmentContext(
        dbPathOverride = dbPath.toString(),
        environment = emptyMap(),
        userHome = tempDir,
      ),
    )

    database.transaction { unitOfWork ->
      unitOfWork.workflowStates.saveFeatureTaskRuntimeWorkflow(sampleWorkflow("wftr-readiness-1"))
    }
    val afterFirst = DatabaseRuntime.writeReadinessEstablishmentCount()
    database.transaction { unitOfWork ->
      unitOfWork.workflowStates.saveFeatureTaskRuntimeWorkflow(sampleWorkflow("wftr-readiness-2"))
    }
    database.selfManagedWrite { unitOfWork ->
      unitOfWork.workflowStates.getFeatureTaskRuntimeWorkflow("wftr-readiness-1")
    }
    assertEquals(afterFirst, DatabaseRuntime.writeReadinessEstablishmentCount())

    relocateDatabaseFromPath(dbPath, tempDir.resolve("metrics-archived.db"))
    database.transaction { unitOfWork ->
      unitOfWork.workflowStates.saveFeatureTaskRuntimeWorkflow(sampleWorkflow("wftr-readiness-3"))
    }
    assertTrue(DatabaseRuntime.writeReadinessEstablishmentCount() > afterFirst)
  }

  @Test
  fun `truncating database file at the same path forces schema re-establishment`() {
    val tempDir = Files.createTempDirectory("skillbill-write-readiness-truncate")
    val dbPath = tempDir.resolve("metrics.db")
    DatabaseRuntime.resetWriteReadinessForTests()
    val database = SQLiteDatabaseSessionFactory(
      EnvironmentContext(
        dbPathOverride = dbPath.toString(),
        environment = emptyMap(),
        userHome = tempDir,
      ),
    )
    database.transaction { unitOfWork ->
      unitOfWork.workflowStates.saveFeatureTaskRuntimeWorkflow(sampleWorkflow("wftr-truncate"))
    }
    val afterInit = DatabaseRuntime.writeReadinessEstablishmentCount()
    Files.write(dbPath, ByteArray(0))
    database.transaction { unitOfWork ->
      unitOfWork.workflowStates.saveFeatureTaskRuntimeWorkflow(sampleWorkflow("wftr-truncate-2"))
    }
    assertTrue(DatabaseRuntime.writeReadinessEstablishmentCount() > afterInit)
  }

  @Test
  fun `changing database user version at the same path forces schema re-establishment`() {
    val tempDir = Files.createTempDirectory("skillbill-write-readiness-version")
    val dbPath = tempDir.resolve("metrics.db")
    DatabaseRuntime.resetWriteReadinessForTests()
    val database = SQLiteDatabaseSessionFactory(
      EnvironmentContext(
        dbPathOverride = dbPath.toString(),
        environment = emptyMap(),
        userHome = tempDir,
      ),
    )
    database.transaction { unitOfWork ->
      unitOfWork.workflowStates.saveFeatureTaskRuntimeWorkflow(sampleWorkflow("wftr-version"))
    }
    val afterInit = DatabaseRuntime.writeReadinessEstablishmentCount()

    DriverManager.getConnection("jdbc:sqlite:$dbPath").use { connection ->
      connection.createStatement().use { statement ->
        statement.execute("PRAGMA user_version = 7")
      }
    }

    database.transaction { unitOfWork ->
      unitOfWork.workflowStates.saveFeatureTaskRuntimeWorkflow(sampleWorkflow("wftr-version-2"))
    }
    assertTrue(DatabaseRuntime.writeReadinessEstablishmentCount() > afterInit)
  }

  @Test
  fun `concurrent readiness initialization publishes one recoverable result`() {
    val tempDir = Files.createTempDirectory("skillbill-write-readiness-concurrent")
    val dbPath = tempDir.resolve("metrics.db")
    val gate = DatabaseWriteReadinessGate()
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
    assertEquals(1, gate.schemaEstablishmentExecutions)
    assertTrue(Files.isRegularFile(dbPath))
  }

  @Test
  fun `failed readiness is not published and recovery retries initialization`() {
    val tempDir = Files.createTempDirectory("skillbill-write-readiness-failure")
    val dbPath = tempDir.resolve("metrics.db")
    Files.createDirectory(dbPath)
    DatabaseRuntime.resetWriteReadinessForTests()
    val database = SQLiteDatabaseSessionFactory(
      EnvironmentContext(
        dbPathOverride = dbPath.toString(),
        environment = emptyMap(),
        userHome = tempDir,
      ),
    )

    assertFailsWith<DatabaseAccessError> {
      database.transaction { Unit }
    }
    val afterFailure = DatabaseRuntime.writeReadinessEstablishmentCount()

    Files.delete(dbPath)
    database.transaction { unitOfWork ->
      unitOfWork.workflowStates.saveFeatureTaskRuntimeWorkflow(sampleWorkflow("wftr-recovered"))
    }

    assertTrue(DatabaseRuntime.writeReadinessEstablishmentCount() > afterFailure)
  }

  private fun relocateDatabaseFromPath(dbPath: Path, archivePath: Path) {
    Files.deleteIfExists(archivePath)
    Files.move(dbPath, archivePath, StandardCopyOption.ATOMIC_MOVE)
    Files.deleteIfExists(dbPath.resolveSibling("${dbPath.fileName}-wal"))
    Files.deleteIfExists(dbPath.resolveSibling("${dbPath.fileName}-shm"))
    assertTrue(!Files.exists(dbPath), "expected database path to be absent before recreation")
  }

  private fun sampleWorkflow(workflowId: String) = WorkflowStateRecord(
    workflowId = workflowId,
    sessionId = "ftr-readiness",
    workflowName = "bill-feature-task",
    contractVersion = "",
    workflowStatus = WorkflowStatus.RUNNING.wireValue,
    currentStepId = "implement",
    stepsJson = "[]",
    artifactsJson = "{}",
    startedAt = null,
    updatedAt = null,
    finishedAt = null,
    mode = FeatureTaskWorkflowMode.RUNTIME,
  )
}