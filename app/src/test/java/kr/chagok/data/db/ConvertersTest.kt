package kr.chagok.data.db

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** Room 시간 변환. 저장된 값이 바뀌면 이미 쌓인 기록을 못 읽으므로 저장 형식도 못 박아 둔다 */
class ConvertersTest {
    private val converters = Converters()

    @Test
    fun `날짜는 1970-01-01 부터 센 날수로 저장하고 그대로 돌아온다`() {
        val day = LocalDate.of(2026, 10, 26)

        assertEquals(0L, converters.dateToEpochDay(LocalDate.of(1970, 1, 1)))
        assertEquals(20_752L, converters.dateToEpochDay(day))
        assertEquals(day, converters.epochDayToDate(converters.dateToEpochDay(day)))
    }

    @Test
    fun `시각은 자정부터 센 초로 저장하고 초 아래는 버린다`() {
        assertEquals(34_200, converters.timeToSecondOfDay(LocalTime.of(9, 30)))
        assertEquals(
            LocalTime.of(23, 59, 59),
            converters.secondOfDayToTime(converters.timeToSecondOfDay(LocalTime.of(23, 59, 59))),
        )
        assertEquals(
            LocalTime.of(9, 30, 15),
            converters.secondOfDayToTime(converters.timeToSecondOfDay(LocalTime.of(9, 30, 15, 999_000_000))),
        )
    }

    @Test
    fun `고친 시각(날짜와 시각)은 ISO 글자로 저장하고 초 아래까지 그대로 돌아온다`() {
        val editedAt = LocalDateTime.of(2026, 10, 26, 9, 30, 15, 123_000_000)

        assertEquals("2026-10-26T09:30", converters.dateTimeToText(LocalDateTime.of(2026, 10, 26, 9, 30)))
        assertEquals(editedAt, converters.textToDateTime(converters.dateTimeToText(editedAt)))
    }

    @Test
    fun `빈 값(null)은 어느 쪽으로도 null 이다 (시각을 모르는 기록 안 된 주행)`() {
        assertNull(converters.dateToEpochDay(null))
        assertNull(converters.epochDayToDate(null))
        assertNull(converters.timeToSecondOfDay(null))
        assertNull(converters.secondOfDayToTime(null))
        assertNull(converters.dateTimeToText(null))
        assertNull(converters.textToDateTime(null))
    }
}
