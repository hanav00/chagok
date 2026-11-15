package kr.chagok.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

/** 분류별 거리 합계 [DistanceTotals] (서식 ⑪~⑬) */
class DistanceTotalsTest {

    @Test
    fun `⑫업무용 사용거리 = ⑧출·퇴근 + ⑨일반 업무 (개인·분류 대기는 빠짐)`() {
        val totals = DistanceTotals(total = 100.0, commute = 20.0, business = 50.0, personal = 20.0, pending = 10.0)

        assertEquals(70.0, totals.businessUseKm, 0.0)
    }

    @Test
    fun `⑬업무사용비율 = 업무용 사용거리 ÷ ⑪총주행거리`() {
        val totals = DistanceTotals(total = 100.0, commute = 20.0, business = 50.0, personal = 30.0)

        assertEquals(0.7, totals.businessRatio, 1e-9)
    }

    @Test
    fun `총주행거리가 0이면 비율은 0`() {
        assertEquals(0.0, DistanceTotals().businessRatio, 0.0)
    }
}
