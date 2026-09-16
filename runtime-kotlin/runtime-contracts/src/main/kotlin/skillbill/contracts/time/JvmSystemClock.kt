package skillbill.contracts.time

import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

object JvmSystemClock : Clock() {
  private val liveUtc: Clock = Clock.tickMillis(ZoneOffset.UTC)

  override fun getZone(): ZoneId = ZoneOffset.UTC

  override fun withZone(zone: ZoneId): Clock = liveUtc.withZone(zone)

  override fun instant(): Instant = liveUtc.instant()
}
