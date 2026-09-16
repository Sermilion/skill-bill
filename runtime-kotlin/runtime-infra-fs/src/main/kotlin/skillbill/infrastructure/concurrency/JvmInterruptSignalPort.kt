package skillbill.infrastructure.concurrency

import skillbill.ports.concurrency.InterruptSignalPort

object JvmInterruptSignalPort : InterruptSignalPort {
  override fun restore() {
    Thread.currentThread().interrupt()
  }
}
