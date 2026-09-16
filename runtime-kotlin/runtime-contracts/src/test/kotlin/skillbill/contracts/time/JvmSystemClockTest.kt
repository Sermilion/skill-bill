package skillbill.contracts.time

import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class JvmSystemClockTest {
  @Test
  fun `system clock uses UTC and millisecond precision`() {
    assertEquals(ZoneOffset.UTC, JvmSystemClock.zone)
    assertEquals(0, JvmSystemClock.instant().nano % 1_000_000)
  }

  @Test
  fun `withZone keeps a live time source instead of freezing at the current instant`() {
    val zone = ZoneId.of("Europe/Berlin")
    val zoned = JvmSystemClock.withZone(zone)
    assertEquals(zone, zoned.zone)
    val beforeSpin = System.currentTimeMillis()
    spinUntilMillisAdvance(beforeSpin)
    assertTrue(zoned.instant().toEpochMilli() > beforeSpin)
  }

  private fun spinUntilMillisAdvance(startMillis: Long) {
    while (System.currentTimeMillis() <= startMillis) Thread.onSpinWait()
  }
}
