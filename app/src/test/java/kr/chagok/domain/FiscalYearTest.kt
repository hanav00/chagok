package kr.chagok.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** 사업연도 [FiscalYear] */
class FiscalYearTest {

    @Test
    fun `기본 사업연도는 1월 1일부터 12월 31일까지`() {
        val year = FiscalYear.of(LocalDate.of(2026, 10, 26))

        assertEquals(FiscalYear(2026, startMonth = 1), year)
        assertEquals(LocalDate.of(2026, 1, 1), year.start)
        assertEquals(LocalDate.of(2026, 12, 31), year.end)
    }

    @Test
    fun `1월 시작이면 1월 1일과 12월 31일은 같은 해 사업연도`() {
        assertEquals(2026, FiscalYear.of(LocalDate.of(2026, 1, 1), startMonth = 1).year)
        assertEquals(2026, FiscalYear.of(LocalDate.of(2026, 12, 31), startMonth = 1).year)
    }

    @Test
    fun `첫날과 마지막 날은 들어가고 그 앞뒤 날은 빠진다`() {
        val year = FiscalYear(2026, startMonth = 1)

        assertTrue(LocalDate.of(2026, 1, 1) in year)
        assertTrue(LocalDate.of(2026, 12, 31) in year)
        assertFalse(LocalDate.of(2025, 12, 31) in year)
        assertFalse(LocalDate.of(2027, 1, 1) in year)
    }

    @Test
    fun `3월 시작이면 2월은 앞해 사업연도, 3월 1일부터 그해 사업연도`() {
        assertEquals(FiscalYear(2025, startMonth = 3), FiscalYear.of(LocalDate.of(2026, 2, 28), startMonth = 3))
        assertEquals(FiscalYear(2026, startMonth = 3), FiscalYear.of(LocalDate.of(2026, 3, 1), startMonth = 3))
        assertEquals(FiscalYear(2025, startMonth = 3), FiscalYear.of(LocalDate.of(2026, 1, 1), startMonth = 3))
    }

    @Test
    fun `3월 시작 사업연도는 다음 해 2월 말일에 끝난다 (윤년이면 29일)`() {
        assertEquals(LocalDate.of(2027, 2, 28), FiscalYear(2026, startMonth = 3).end)
        assertEquals(LocalDate.of(2028, 2, 29), FiscalYear(2027, startMonth = 3).end)
    }

    @Test
    fun `3월 시작 사업연도의 경계`() {
        val year = FiscalYear(2026, startMonth = 3)

        assertTrue(LocalDate.of(2026, 3, 1) in year)
        assertTrue(LocalDate.of(2027, 2, 28) in year)
        assertFalse(LocalDate.of(2026, 2, 28) in year)
        assertFalse(LocalDate.of(2027, 3, 1) in year)
    }

    @Test
    fun `어느 날이든 of 로 구한 사업연도에 그날이 들어간다`() {
        val days = generateSequence(LocalDate.of(2026, 1, 1)) { it.plusDays(1) }.takeWhile { it.year < 2028 }

        for (startMonth in 1..12) {
            days.forEach { day -> assertTrue("$day / $startMonth 월 시작", day in FiscalYear.of(day, startMonth)) }
        }
    }
}
