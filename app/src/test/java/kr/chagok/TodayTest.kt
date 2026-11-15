package kr.chagok

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kr.chagok.domain.ChagokConfig
import kr.chagok.testing.MutableClock
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 화면이 쓰는 오늘 날짜: 자정을 넘기면 저절로 바뀐다. 가상 시간으로 기다리고, 시계도 같은 만큼 앞으로 돌린다.
 * 안드로이드 없이 돈다.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TodayTest {

    @Test
    fun `자정 직전에 만들면 자정을 넘긴 뒤 다음 날이 된다`() = runTest {
        val clock = MutableClock(LocalDateTime.of(2026, 10, 26, 23, 59, 59))
        val today = Today(clock, backgroundScope)
        assertEquals(LocalDate.of(2026, 10, 26), today.date.value)

        val wait = 1_000L + ChagokConfig.MIDNIGHT_MARGIN_MILLIS
        clock.advance(Duration.ofMillis(wait))
        testScheduler.advanceTimeBy(wait + 1)

        assertEquals(LocalDate.of(2026, 10, 27), today.date.value)
    }

    @Test
    fun `낮에는 정해 둔 간격마다 다시 보지만 같은 날이면 그대로다`() = runTest {
        val clock = MutableClock(LocalDateTime.of(2026, 10, 26, 9, 0))
        val today = Today(clock, backgroundScope)

        clock.advance(Duration.ofMillis(ChagokConfig.DAY_CHECK_MAX_MILLIS))
        testScheduler.advanceTimeBy(ChagokConfig.DAY_CHECK_MAX_MILLIS + 1)

        assertEquals(LocalDate.of(2026, 10, 26), today.date.value)
    }

    @Test
    fun `화면이 다시 보일 때 다시 읽으면 그사이 지난 날짜가 된다 (기기가 잠든 사이 자정)`() = runTest {
        val clock = MutableClock(LocalDateTime.of(2026, 10, 26, 22, 0))
        val today = Today(clock, backgroundScope)

        clock.advance(Duration.ofHours(10))
        today.refresh()

        assertEquals(LocalDate.of(2026, 10, 27), today.date.value)
    }
}
