package skillbill.infrastructure.sqlite

import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import skillbill.infrastructure.sqlite.core.inImmediateTransaction
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.nio.file.Path
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException
import java.sql.Statement
import java.util.logging.Handler
import java.util.logging.LogRecord
import java.util.logging.Logger
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
      connection.inImmediateTransaction {
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
      connection.inImmediateTransaction { }
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
        connection.inImmediateTransaction {
          error("force rollback")
        }
      }
      assertEquals(0, error.suppressed.size)
    }
  }

  @Test
  fun `rollback failure after body failure emits a bounded sqlite diagnostic`() {
    val connection = failingConnection(mapOf("ROLLBACK" to SQLException("rollback-probe-failure")))
    val logger = Logger.getLogger("skillbill.sqlite.transaction")
    val (error, messages) = capturingLogRecords(logger) {
      assertFailsWith<IllegalStateException> {
        connection.inImmediateTransaction {
          error("primary-probe-failure")
        }
      }
    }

    assertEquals("primary-probe-failure", error.message)
    assertTrue(
      messages.any { message ->
        message.contains("transaction rollback failed") && message.contains("rollback-probe-failure")
      },
      messages.toString(),
    )
  }

  @Test
  fun `session factory write transaction dual failure preserves primary`() {
    val connection = failingConnection(mapOf("ROLLBACK" to SQLException("rollback-probe-failure")))
    val method = sessionFactoryTransactionMethod("inTransaction")
    val block: () -> Any? = { throw IllegalStateException("primary-probe-failure") }

    val error = assertFailsWith<InvocationTargetException> {
      method.invoke(
        null,
        connection,
        Path.of("/tmp/unused-probe.db"),
        block,
      )
    }.cause as IllegalStateException

    assertEquals("primary-probe-failure", error.message)
    assertEquals(1, error.suppressed.size)
  }

  @Test
  fun `session factory read transaction dual failure preserves primary`() {
    val connection = failingConnection(mapOf("ROLLBACK" to SQLException("rollback-probe-failure")))
    val method = sessionFactoryTransactionMethod("inReadTransaction")
    val block: () -> Any? = { throw IllegalStateException("primary-probe-failure") }

    val error = assertFailsWith<InvocationTargetException> {
      method.invoke(
        null,
        connection,
        Path.of("/tmp/unused-probe.db"),
        block,
      )
    }.cause as IllegalStateException

    assertEquals("primary-probe-failure", error.message)
    assertEquals(1, error.suppressed.size)
  }

  private fun sessionFactoryTransactionMethod(name: String): Method {
    val owner = Class.forName("skillbill.infrastructure.sqlite.SQLiteDatabaseSessionFactoryKt")
    return owner.getDeclaredMethod(
      name,
      Connection::class.java,
      Path::class.java,
      Function0::class.java,
    ).also { it.isAccessible = true }
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
        else -> null
      }
    } as Connection
  }

  private fun <T> capturingLogRecords(logger: Logger, block: () -> T): Pair<T, List<String>> {
    val records = mutableListOf<LogRecord>()
    val handler = object : Handler() {
      override fun publish(record: LogRecord) {
        records += record
      }
      override fun flush() = Unit
      override fun close() = Unit
    }
    logger.addHandler(handler)
    return try {
      block() to records.mapNotNull(LogRecord::getMessage)
    } finally {
      logger.removeHandler(handler)
    }
  }
}
