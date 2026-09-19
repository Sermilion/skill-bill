package skillbill.ports.db

object ReviewMetricsDatabasePolicy {
  const val BUSY_TIMEOUT_MILLIS: Int = 5000

  const val SELF_MANAGED_WRITE_BUSY_ATTEMPTS: Int = 3
}
