package kr.chagok.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 잔량 값 하나로 주유·충전을 알아채기 [RefuelRules.next] */
class RefuelRulesTest {
    private val parkedAt30 = EnergyMark(percent = 30, odometerKm = 1_000.0)

    @Test
    fun `처음 받은 값은 기준점이 될 뿐이다`() {
        val (mark, fill) = RefuelRules.next(null, percent = 30, odometerKm = 1_000.0, isParked = true)

        assertEquals(parkedAt30, mark)
        assertNull(fill)
    }

    @Test
    fun `서 있는 자리에서 5%p 이상 오르면 주유다`() {
        val (mark, fill) = RefuelRules.next(parkedAt30, percent = 80, odometerKm = 1_000.0, isParked = true)

        assertEquals(Fill(fromPercent = 30, toPercent = 80), fill)
        assertEquals(parkedAt30.copy(isFilling = true), mark)
    }

    @Test
    fun `4%p 오른 것은 센서 흔들림으로 보고 넘긴다`() {
        val (mark, fill) = RefuelRules.next(parkedAt30, percent = 34, odometerKm = 1_000.0, isParked = true)

        assertNull(fill)
        assertEquals(parkedAt30, mark)
    }

    @Test
    fun `주유 중 더 오르면 처음 잔량부터 지금까지 같은 주유다`() {
        val filling = parkedAt30.copy(isFilling = true)

        val (mark, fill) = RefuelRules.next(filling, percent = 95, odometerKm = 1_000.0, isParked = true)

        assertEquals(Fill(fromPercent = 30, toPercent = 95), fill)
        assertEquals(filling, mark)
    }

    @Test
    fun `주유 뒤 차가 움직이면 기준점을 지금 잔량으로 새로 잡는다`() {
        val filling = parkedAt30.copy(isFilling = true)

        val (mark, fill) = RefuelRules.next(filling, percent = 94, odometerKm = 1_000.3, isParked = false)

        assertNull(fill)
        assertEquals(EnergyMark(percent = 94, odometerKm = 1_000.3), mark)
    }

    @Test
    fun `달리며 오른 값(회생 제동)은 주유가 아니다`() {
        val (mark, fill) = RefuelRules.next(parkedAt30, percent = 36, odometerKm = 1_002.0, isParked = false)

        assertNull(fill)
        assertEquals(EnergyMark(percent = 36, odometerKm = 1_002.0), mark)
    }

    @Test
    fun `앱이 꺼진 사이 달리고 넣었으면 주차 중에 받은 값으로 알아챈다`() {
        val (_, fill) = RefuelRules.next(parkedAt30, percent = 70, odometerKm = 1_012.0, isParked = true)

        assertEquals(Fill(fromPercent = 30, toPercent = 70), fill)
    }

    @Test
    fun `달리며 줄어들면 기준점도 따라 내려간다`() {
        val (mark, fill) = RefuelRules.next(parkedAt30, percent = 28, odometerKm = 1_005.0, isParked = false)

        assertNull(fill)
        assertEquals(EnergyMark(percent = 28, odometerKm = 1_005.0), mark)
    }
}
