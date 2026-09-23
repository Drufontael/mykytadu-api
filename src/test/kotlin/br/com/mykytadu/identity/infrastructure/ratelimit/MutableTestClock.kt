package br.com.mykytadu.identity.infrastructure.ratelimit

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

internal class MutableTestClock(private var current: Instant) : Clock() {

    override fun getZone(): ZoneId = ZoneOffset.UTC

    override fun withZone(zone: ZoneId): Clock = checkNotNull(takeIf { zone == ZoneOffset.UTC }) {
        "Mutable test clock supports UTC only"
    }

    override fun instant(): Instant = current

    fun advance(duration: Duration) {
        current = current.plus(duration)
    }
}
