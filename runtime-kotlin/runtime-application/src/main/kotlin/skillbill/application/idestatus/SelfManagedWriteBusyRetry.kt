package skillbill.application.idestatus

import skillbill.application.rethrowIfCooperativeCancellationOrInterruption
import skillbill.ports.db.DatabaseSessionFactory
import skillbill.ports.db.ReviewMetricsDatabasePolicy
import skillbill.ports.persistence.UnitOfWork

internal fun <T> DatabaseSessionFactory.selfManagedWriteWithBusyRetry(block: (UnitOfWork) -> T): T {
  var lastError: Throwable? = null
  repeat(ReviewMetricsDatabasePolicy.SELF_MANAGED_WRITE_BUSY_ATTEMPTS) { attempt ->
    val outcome = runCatching { selfManagedWrite(block) }
    outcome.exceptionOrNull()?.rethrowIfCooperativeCancellationOrInterruption()
    val error = outcome.exceptionOrNull()
    if (error == null) {
      return outcome.getOrThrow()
    }
    lastError = error
    if (!error.isSqliteBusy() || attempt == ReviewMetricsDatabasePolicy.SELF_MANAGED_WRITE_BUSY_ATTEMPTS - 1) {
      throw error
    }
  }
  throw checkNotNull(lastError)
}

private fun Throwable.isSqliteBusy(): Boolean {
  var current: Throwable? = this
  while (current != null) {
    if (current.message?.contains("SQLITE_BUSY", ignoreCase = true) == true ||
      current.message?.contains("database is locked", ignoreCase = true) == true
    ) {
      return true
    }
    current = current.cause
  }
  return false
}
