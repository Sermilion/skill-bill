package skillbill.infrastructure.sqlite

import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import skillbill.infrastructure.sqlite.core.ops.inNestedWriteTransaction
import skillbill.ports.diagnostics.RuntimeDiagnostics
import java.lang.reflect.Proxy
import java.nio.file.Path
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException
import java.sql.Statement
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

@Execution(ExecutionMode.SAME_THREAD)
class ConnectionTransactionsRollbackTest {
  @Test
  fun `dual failure keeps the primary message and attaches rollback as suppressed`() {
    val primary = IllegalStateException("primary-probe-failure")
    val connection = failingConnection(mapOf("ROLLBACK" to SQLException("rollback-probe-failure")))

    val error = assertFailsWith<IllegalStateException> {
      connection.inNestedWriteTransaction {
        throw primary
      }
    }

    assertSame(primary, error)
    assertEquals(1, error.suppressed.size)
    assertTrue(error.suppressed.single().message.orEmpty().contains("rollback-probe-failure"))
  }

  @Test
  fun `commit failure remains primary when rollback also fails`() {
    val commitFailure = SQLException("commit-probe-failure")
    val connection = failingConnection(
      mapOf(
        "COMMIT" to commitFailure,
        "ROLLBACK" to SQLException("rollback-probe-failure"),
      ),
    )

    val error = assertFailsWith<SQLException> {
      connection.inNestedWriteTransaction { }
    }

    assertSame(commitFailure, error)
    assertEquals(1, error.suppressed.size)
    assertTrue(error.suppressed.single().message.orEmpty().contains("rollback-probe-failure"))
  }

  @Test
  fun `body failure with successful rollback adds no suppresseds`() {
    val dbPath = createTempDirectory("skillbill-immediate-rollback").resolve("rollback.db")
    DriverManager.getConnection("jdbc:sqlite:$dbPath").use { connection ->
      val error = assertFailsWith<IllegalStateException> {
        connection.inNestedWriteTransaction {
          error("force rollback")
        }
      }
      assertEquals(0, error.suppressed.size)
    }
  }

  @Test
  fun `rollback failure after body failure emits a bounded sqlite diagnostic`() {
    val connection = failingConnection(mapOf("ROLLBACK" to SQLException("rollback-probe-failure")))
    val diagnostics = RecordingDiagnostics()
    val error = assertFailsWith<IllegalStateException> {
      connection.inNestedWriteTransaction(diagnostics) {
        error("primary-probe-failure")
      }
    }

    assertEquals("primary-probe-failure", error.message)
    assertTrue(
      diagnostics.warnings.any { message ->
        message.contains("transaction rollback failed") && message.contains("rollback-probe-failure")
      },
      diagnostics.warnings.toString(),
    )
  }

  private fun failingConnection(failures: Map<String, SQLException>): Connection {
    val statement = Proxy.newProxyInstance(
      Statement::class.java.classLoader,
      arrayOf(Statement::class.java),
    ) { _, method, args ->
      when (method.name) {
        "execute" -> failures[args?.firstOrNull() as? String]?.let { throw it } ?: true
        "close" -> null
        else -> null
      }
    }
    return Proxy.newProxyInstance(
      Connection::class.java.classLoader,
      arrayOf(Connection::class.java),
    ) { _, method, _ ->
      when (method.name) {
        "createStatement" -> statement
        "getMetaData" -> Proxy.newProxyInstance(
          Class.forName("java.sql.DatabaseMetaData").classLoader,
          arrayOf(Class.forName("java.sql.DatabaseMetaData")),
        ) { _, metaMethod, _ ->
          when (metaMethod.name) {
            "getURL" -> "jdbc:sqlite:${Path.of("/tmp/unused-probe.db")}"
            else -> null
          }
        }
        else -> null
      }
    } as Connection
  }

  private class RecordingDiagnostics : RuntimeDiagnostics {
    val warnings = CopyOnWriteArrayList<String>()

    override fun warning(message: String, error: Throwable?) {
      warnings += message
    }

    override fun error(message: String, error: Throwable?) {
      warnings += message
    }
  }
}
