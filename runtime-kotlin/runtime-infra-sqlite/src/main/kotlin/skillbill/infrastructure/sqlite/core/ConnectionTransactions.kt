package skillbill.infrastructure.sqlite.core

import java.sql.Connection

internal inline fun <T> Connection.inImmediateTransaction(block: Connection.() -> T): T {
  createStatement().use { it.execute("BEGIN IMMEDIATE") }
  var committed = false
  return try {
    val result = block()
    createStatement().use { it.execute("COMMIT") }
    committed = true
    result
  } finally {
    if (!committed) {
      rollbackImmediateTransaction()
    }
  }
}

private fun Connection.rollbackImmediateTransaction() {
  runCatching { createStatement().use { it.execute("ROLLBACK") } }
}
