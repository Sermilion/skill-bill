package skillbill.infrastructure.sqlite

import skillbill.model.EnvironmentContext
import skillbill.ports.workflow.model.FeatureTaskWorkflowMode
import skillbill.ports.workflow.model.WorkflowStateRecord
import skillbill.infrastructure.sqlite.core.DatabaseRuntime
import java.nio.file.Files
import java.sql.Connection
import java.sql.Driver
import java.sql.DriverManager
import java.sql.DriverPropertyInfo
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Proxy
import java.util.Properties
import java.util.logging.Logger
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DatabaseWriteMaintenanceRegressionTest {
  @Test
  fun `initialized database write round trip records maintenance only during readiness`() {
    val tempDir = Files.createTempDirectory("skillbill-write-maintenance")
    val dbPath = tempDir.resolve("metrics.db")
    DatabaseRuntime.resetWriteReadinessForTests()
    val database = SQLiteDatabaseSessionFactory(
      EnvironmentContext(
        dbPathOverride = dbPath.toString(),
        environment = emptyMap(),
        userHome = tempDir,
      ),
    )

    val delegate = DriverManager.getDriver("jdbc:sqlite:$dbPath")
    val recordingDriver = RecordingJdbcDriver(delegate)
    DriverManager.deregisterDriver(delegate)
    DriverManager.registerDriver(recordingDriver)
    DriverManager.registerDriver(delegate)
    try {
      database.transaction { unitOfWork ->
        unitOfWork.workflowStates.saveFeatureTaskRuntimeWorkflow(sampleWorkflow("wftr-maintenance-1"))
      }
      val before = recordingDriver.categories.toSet()
      recordingDriver.clear()

      database.transaction { unitOfWork ->
        unitOfWork.workflowStates.saveFeatureTaskRuntimeWorkflow(sampleWorkflow("wftr-maintenance-2"))
      }
      database.selfManagedWrite { unitOfWork ->
        unitOfWork.workflowStates.saveFeatureTaskRuntimeWorkflow(sampleWorkflow("wftr-maintenance-3"))
      }
      val after = recordingDriver.categories.toSet()

      assertTrue(
        before.containsAll(setOf("schema_maintenance", "repair_scan")),
        "before=$before",
      )
      assertFalse("schema_maintenance" in after, "before=$before after=$after")
      assertFalse("repair_scan" in after, "before=$before after=$after")
      assertTrue("connection_pragmas" in after, "before=$before after=$after")
      assertTrue("transaction_control" in after, "before=$before after=$after")
      assertTrue("application_work" in after, "before=$before after=$after")
    } finally {
      DriverManager.deregisterDriver(recordingDriver)
      DriverManager.deregisterDriver(delegate)
      DriverManager.registerDriver(delegate)
    }
  }

  private fun sampleWorkflow(workflowId: String) = WorkflowStateRecord(
    workflowId = workflowId,
    sessionId = "ftr-maintenance",
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

private class RecordingJdbcDriver(
  private val delegate: Driver,
) : Driver {
  val categories = mutableListOf<String>()

  fun clear() {
    categories.clear()
  }

  override fun connect(url: String?, properties: Properties?): Connection? {
    val connection = delegate.connect(url, properties) ?: return null
    return Proxy.newProxyInstance(
      Connection::class.java.classLoader,
      arrayOf(Connection::class.java),
    ) { _, method, args ->
      val value = invokeRaw(method, connection, args)
      when {
        value is java.sql.PreparedStatement && method.name == "prepareStatement" ->
          recordStatement(value, args?.firstOrNull() as? String, prepared = true)
        value is java.sql.Statement && method.name == "createStatement" ->
          recordStatement(value, null, prepared = false)
        else -> value
      }
    } as Connection
  }

  private fun recordStatement(
    statement: java.sql.Statement,
    preparedSql: String?,
    prepared: Boolean,
  ): java.sql.Statement = Proxy.newProxyInstance(
    java.sql.Statement::class.java.classLoader,
    arrayOf(if (prepared) java.sql.PreparedStatement::class.java else java.sql.Statement::class.java),
  ) { _, method, args ->
    if (method.name in setOf("execute", "executeQuery", "executeUpdate", "executeLargeUpdate", "executeBatch")) {
      categories += statementCategory(preparedSql ?: (args?.firstOrNull() as? String).orEmpty())
    }
    invokeRaw(method, statement, args)
  } as java.sql.Statement

  private fun statementCategory(sql: String): String {
    val normalized = sql.replace(Regex("\\s+"), " ").trim().uppercase()
    return when {
      normalized.startsWith("PRAGMA BUSY_TIMEOUT") ||
        normalized.startsWith("PRAGMA JOURNAL_MODE") ||
        normalized.startsWith("PRAGMA FOREIGN_KEYS") -> "connection_pragmas"
      normalized.startsWith("PRAGMA") -> "readiness_signal"
      normalized.startsWith("BEGIN") ||
        normalized.startsWith("COMMIT") ||
        normalized.startsWith("ROLLBACK") -> "transaction_control"
      normalized.contains("SCHEMA_MIGRATIONS") ||
        normalized.startsWith("CREATE TABLE") ||
        normalized.startsWith("CREATE INDEX") ||
        normalized.startsWith("ALTER TABLE") ||
        normalized.startsWith("DROP TABLE") ||
        normalized.startsWith("DROP INDEX") -> "schema_maintenance"
      normalized.contains("SQLITE_MASTER") ||
        normalized.contains("PRAGMA_TABLE_INFO") ||
        (normalized.contains("STATE_ENTERED_AT_ESTIMATED") && !normalized.contains("EXCLUDED")) ->
        "repair_scan"
      else -> "application_work"
    }
  }

  override fun acceptsURL(url: String?): Boolean = delegate.acceptsURL(url)

  override fun getPropertyInfo(url: String?, properties: Properties?): Array<DriverPropertyInfo> =
    delegate.getPropertyInfo(url, properties)

  override fun getMajorVersion(): Int = delegate.majorVersion

  override fun getMinorVersion(): Int = delegate.minorVersion

  override fun jdbcCompliant(): Boolean = delegate.jdbcCompliant()

  override fun getParentLogger(): Logger = delegate.parentLogger

  private fun invokeRaw(
    method: java.lang.reflect.Method,
    target: Any,
    args: Array<out Any?>?,
  ): Any? = try {
    method.invoke(target, *(args ?: emptyArray()))
  } catch (error: InvocationTargetException) {
    throw error.targetException
  }
}
