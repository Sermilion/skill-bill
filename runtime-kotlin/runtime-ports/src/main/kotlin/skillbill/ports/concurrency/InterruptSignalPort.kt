package skillbill.ports.concurrency

fun interface InterruptSignalPort {
  fun restore()
}

object JvmInterruptSignalPort : InterruptSignalPort {
  override fun restore() {
    Thread.currentThread().interrupt()
  }
}
