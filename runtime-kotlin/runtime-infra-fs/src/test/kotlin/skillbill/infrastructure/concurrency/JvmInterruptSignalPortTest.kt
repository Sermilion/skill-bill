package skillbill.infrastructure.concurrency

import kotlin.test.Test
import kotlin.test.assertTrue

class JvmInterruptSignalPortTest {
  @Test
  fun `restore re-asserts the thread interrupt flag`() {
    Thread.interrupted()
    JvmInterruptSignalPort.restore()
    assertTrue(Thread.interrupted())
    Thread.interrupted()
  }
}
