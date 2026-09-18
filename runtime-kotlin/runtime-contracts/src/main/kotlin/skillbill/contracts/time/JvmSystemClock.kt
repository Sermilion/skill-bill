package skillbill.contracts.time

import java.time.Clock
import java.time.ZoneOffset

val JvmSystemClock: Clock = Clock.tickMillis(ZoneOffset.UTC)
