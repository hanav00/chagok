package kr.chagok.domain.model

import kr.chagok.testing.TripFixtures.trip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 운행 분류 [Category] */
class CategoryTest {

    @Test
    fun `출퇴근과 일반 업무는 업무용 사용거리, 개인만 아니다`() {
        assertTrue(Category.COMMUTE.isBusinessUse)
        assertTrue(Category.BUSINESS.isBusinessUse)
        assertFalse(Category.PERSONAL.isBusinessUse)
    }

    @Test
    fun `운행 상세의 분류 버튼은 업무 · 출퇴근 · 개인 순서`() {
        assertEquals(listOf(Category.BUSINESS, Category.COMMUTE, Category.PERSONAL), Category.choicesFor(trip()))
    }

    @Test
    fun `기록 안 된 주행은 출퇴근인지 알 수 없어 업무 · 개인만 고른다`() {
        val gap = trip(category = null, isGap = true, personId = null)

        assertEquals(listOf(Category.BUSINESS, Category.PERSONAL), Category.choicesFor(gap))
    }

    @Test
    fun `기본 분류·다음 출발 토글에는 출퇴근이 없다 (집·회사로 저절로 나뉜다)`() {
        assertEquals(listOf(Category.BUSINESS, Category.PERSONAL), Category.DEPARTURE_CHOICES)
    }
}
