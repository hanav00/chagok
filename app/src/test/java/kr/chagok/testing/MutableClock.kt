package kr.chagok.testing

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/** 테스트가 시각을 정하고 앞으로 돌리는 시계 (기본: 2026-10-26 09:00) */
class MutableClock(
    start: LocalDateTime = LocalDateTime.of(2026, 10, 26, 9, 0),
    private val zone: ZoneId = ZoneId.of("Asia/Seoul"),
) : Clock() {
    private var now: Instant = start.atZone(zone).toInstant()

    override fun getZone(): ZoneId = zone

    override fun withZone(zone: ZoneId): Clock = MutableClock(LocalDateTime.ofInstant(now, zone), zone)

    override fun instant(): Instant = now

    fun advance(duration: Duration) {
        now = now.plus(duration)
    }
}
