package skillbill.infrastructure.sqlite

import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import skillbill.error.DatabaseAccessError
import skillbill.infrastructure.sqlite.core.DatabaseIdentity
import skillbill.infrastructure.sqlite.core.DatabaseRuntime
import skillbill.model.EnvironmentContext
import skillbill.ports.workflow.model.FeatureTaskWorkflowMode
import skillbill.ports.workflow.model.WorkflowStateRecord
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

@Execution(ExecutionMode.SAME_THREAD)
class DatabaseWriteReadinessTest {
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

    val identityBefore = requireNotNull(DatabaseIdentity.read(dbPath))
    deleteDatabaseFiles(dbPath)
    database.transaction { unitOfWork ->
      unitOfWork.workflowStates.saveFeatureTaskRuntimeWorkflow(sampleWorkflow("wftr-readiness-3"))
    }
    val identityAfter = DatabaseIdentity.read(dbPath)
    assertNotEquals(identityBefore, identityAfter)
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

  private fun deleteDatabaseFiles(dbPath: Path) {
    Files.deleteIfExists(dbPath.resolveSibling("${dbPath.fileName}-wal"))
    Files.deleteIfExists(dbPath.resolveSibling("${dbPath.fileName}-shm"))
    assertTrue(Files.deleteIfExists(dbPath), "expected database file to delete before identity change probe")
  }

  private fun sampleWorkflow(workflowId: String) = WorkflowStateRecord(
    workflowId = workflowId,
    sessionId = "ftr-readiness",
    workflowName = "bill-feature-task",
    contractVersion = "",
    workflowStatus = "running",
    currentStepId = "implement",
    stepsJson = "[]",
    artifactsJson = "{}",
    startedAt = null,
    updatedAt = null,
    finishedAt = null,
    mode = FeatureTaskWorkflowMode.RUNTIME,
  )
}
