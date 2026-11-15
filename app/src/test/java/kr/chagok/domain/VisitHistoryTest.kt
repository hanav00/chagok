package kr.chagok.domain

import kr.chagok.domain.model.Category
import kr.chagok.testing.TripFixtures.CLIENT
import kr.chagok.testing.TripFixtures.MART
import kr.chagok.testing.TripFixtures.TODAY
import kr.chagok.testing.TripFixtures.trip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 다시 가는 곳의 지난 방문 [VisitHistory.lastNoted]: 마지막 메모와 마지막으로 간 날 */
class VisitHistoryTest {
    @Test
    fun `같은 곳에 남긴 메모 중 마지막 것을 고르고, 날짜는 메모 없이 간 날까지 쳐서 마지막 날`() {
        val trips = listOf(
            trip(destination = CLIENT, date = TODAY.minusDays(30), note = "첫 미팅"),
            trip(destination = CLIENT, date = TODAY.minusDays(21), note = "신제품 견적 요청"),
            trip(destination = CLIENT, date = TODAY.minusDays(7)),
        )

        val visit = VisitHistory.lastNoted(trips, CLIENT)

        assertEquals(PastVisit(lastDate = TODAY.minusDays(7), note = "신제품 견적 요청"), visit)
    }

    @Test
    fun `간 적은 있어도 메모가 없으면 말할 것이 없다`() {
        val trips = listOf(trip(destination = CLIENT, note = " "))

        assertNull(VisitHistory.lastNoted(trips, CLIENT))
    }

    @Test
    fun `다른 곳에 남긴 메모와 기록 안 된 주행은 보지 않는다`() {
        val trips = listOf(
            trip(destination = MART, note = "단가 협상"),
            trip(isGap = true, category = null, destination = null, note = "메모"),
        )

        assertNull(VisitHistory.lastNoted(trips, CLIENT))
    }

    @Test
    fun `개인 운행에 남은 말은 읽지 않는다`() {
        val trips = listOf(trip(category = Category.PERSONAL, destination = CLIENT, note = "병원"))

        assertNull(VisitHistory.lastNoted(trips, CLIENT))
    }
}
