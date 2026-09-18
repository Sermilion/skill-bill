package skillbill.infrastructure.sqlite.core

import java.sql.Connection
import java.util.logging.Logger

private const val ROLLBACK_FAILURE_DETAIL_LIMIT = 240

private val sqliteTransactionLogger: Logger =
  Logger.getLogger("skillbill.sqlite.transaction")

internal inline fun <T> Connection.inImmediateTransaction(block: Connection.() -> T): T {
  createStatement().use { it.execute("BEGIN IMMEDIATE") }
  var committed = false
  var primaryFailure: Throwable? = null
  return try {
    runCatching {
      val result = block()
      createStatement().use { it.execute("COMMIT") }
      committed = true
      result
    }.onFailure { primaryFailure = it }.getOrThrow()
  } finally {
    if (!committed) {
      rollbackAfterFailedTransaction(primaryFailure)
    }
  }
}

internal fun Connection.rollbackAfterFailedTransaction(primaryFailure: Throwable?) {
  val rollbackOutcome = runCatching { createStatement().use { it.execute("ROLLBACK") } }
  val rollbackFailure = rollbackOutcome.exceptionOrNull() ?: return
  if (primaryFailure != null && primaryFailure !== rollbackFailure) {
    primaryFailure.addSuppressed(rollbackFailure)
  }
  runCatching { logTransactionRollbackFailure(rollbackFailure, primaryFailure) }
}

internal fun logTransactionRollbackFailure(rollbackFailure: Throwable, primaryFailure: Throwable?) {
  val rollbackDetail = boundedTransactionFailureDetail(rollbackFailure)
  val primaryDetail = primaryFailure?.let(::boundedTransactionFailureDetail) ?: "none"
  sqliteTransactionLogger.warning(
    "skillbill sqlite: transaction rollback failed after transaction failure; " +
      "rollback=$rollbackDetail; primary=$primaryDetail",
  )
}

private fun boundedTransactionFailureDetail(failure: Throwable): String {
  val message = failure.message?.takeIf { it.isNotBlank() }
  return (message ?: failure::class.simpleName.orEmpty()).take(ROLLBACK_FAILURE_DETAIL_LIMIT)
}
