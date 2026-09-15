package dev.skillbill.intellij.domain

import java.time.Instant


fun interface StatusClock {
    fun now(): Instant

    companion object {
        fun system(): StatusClock = StatusClock { Instant.now() }

        fun fixed(instant: Instant): StatusClock = StatusClock { instant }
    }
}
