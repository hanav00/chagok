package kr.chagok.ui.format

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth

/** 화면·음성 안내 표시 형식 [Formatters] */
class FormattersTest {

    @Test
    fun `거리는 소수 첫째 자리까지, 천 단위마다 쉼표`() {
        assertEquals("12,345.6", Formatters.km(12_345.6))
        assertEquals("23,418.2", Formatters.km(23_418.2))
        assertEquals("539.6", Formatters.km(539.6))
    }

    @Test
    fun `정수 거리도 소수 첫째 자리를 채운다 (9 → 9·0)`() {
        assertEquals("9.0", Formatters.km(9.0))
        assertEquals("0.0", Formatters.km(0.0))
    }

    @Test
    fun `계기판 뺄셈 오차는 보이지 않는다 (9·200000000001 → 9·2)`() {
        assertEquals("9.2", Formatters.km(23_418.2 - 23_409.0))
        assertEquals("0.1", Formatters.km(100.1 - 100.0))
    }

    @Test
    fun `자리 수를 정하면 그만큼 보인다`() {
        assertEquals("18,620", Formatters.km(18_619.6, digits = 0))
        assertEquals("8.20", Formatters.km(8.2, digits = 2))
    }

    @Test
    fun `시연 샘플 홈 사업연도 숫자 - 18,619·6km 와 15,839·0km`() {
        assertEquals("18,619.6", Formatters.km(18_619.6))
        assertEquals("15,839.0", Formatters.km(15_839.0))
    }

    @Test
    fun `1억 아래는 만 원 단위로 (1500 → 1,500만 원)`() {
        assertEquals("1,500만 원", Formatters.money(1_500.0))
        assertEquals("227만 원", Formatters.money(227.0))
        assertEquals("0만 원", Formatters.money(0.0))
    }

    @Test
    fun `딱 억 단위면 억 원만 (10000 → 1억 원)`() {
        assertEquals("1억 원", Formatters.money(10_000.0))
        assertEquals("3억 원", Formatters.money(30_000.0))
    }

    @Test
    fun `억과 만이 섞이면 둘 다 (12000 → 1억 2,000만 원)`() {
        assertEquals("1억 2,000만 원", Formatters.money(12_000.0))
        assertEquals("12억 3,456만 원", Formatters.money(123_456.0))
        assertEquals("1억 1만 원", Formatters.money(10_001.0))
    }

    @Test
    fun `음수는 앞에 빼기 기호(−, U+2212)를 붙인다`() {
        assertEquals("−542만 원", Formatters.money(-542.0))
        assertEquals("−1억 2,000만 원", Formatters.money(-12_000.0))
        assertEquals("−1억 원", Formatters.money(-10_000.0))
    }

    @Test
    fun `만 원 단위로 반올림한다 (0·5 는 올림)`() {
        assertEquals("227만 원", Formatters.money(226.872))
        assertEquals("18만 원", Formatters.money(18.048))
        assertEquals("209만 원", Formatters.money(208.824))
        assertEquals("1만 원", Formatters.money(0.5))
        assertEquals("0만 원", Formatters.money(0.4))
    }

    @Test
    fun `반올림해서 억으로 넘어가면 억 원 (9999·6 → 1억 원)`() {
        assertEquals("1억 원", Formatters.money(9_999.6))
    }

    @Test
    fun `반올림해서 0이 되는 음수에는 빼기 기호를 붙이지 않는다`() {
        assertEquals("0만 원", Formatters.money(-0.4))
        assertEquals("0만 원", Formatters.money(-0.5)) // -0.5 를 반올림해도 "-0만 원" 이 아니라 "0만 원"
    }

    @Test
    fun `비율은 백분율 소수 첫째 자리까지 (0·851 → 85·1퍼센트)`() {
        assertEquals("85.1%", Formatters.percent(0.851))
        assertEquals("62.5%", Formatters.percent(0.625))
        assertEquals("100.0%", Formatters.percent(1.0))
        assertEquals("0.0%", Formatters.percent(0.0))
    }

    @Test
    fun `시연 샘플 비율 15,839 ÷ 18,619·6 은 85·1퍼센트`() {
        assertEquals("85.1%", Formatters.percent(15_839.0 / 18_619.6))
    }

    @Test
    fun `비율 자리 수를 정하면 그만큼 (0 자리 → 85퍼센트)`() {
        assertEquals("85%", Formatters.percent(0.851, digits = 0))
        assertEquals("85.07%", Formatters.percent(15_839.0 / 18_619.6, digits = 2))
    }

    @Test
    fun `날짜는 월·일(두 자리)과 요일 한 글자 (2026-10-26 → 10·26 (월))`() {
        assertEquals("10.26 (월)", Formatters.day(LocalDate.of(2026, 10, 26)))
    }

    @Test
    fun `월은 앞에 0을 붙이지 않고 일은 두 자리로 (2026-01-05 → 1·05 (월))`() {
        assertEquals("1.05 (월)", Formatters.day(LocalDate.of(2026, 1, 5)))
    }

    @Test
    fun `요일은 일요일부터 토요일까지 한 글자`() {
        val week = (0L..6L).map { Formatters.day(LocalDate.of(2026, 10, 25).plusDays(it)).takeLast(3) }

        assertEquals(listOf("(일)", "(월)", "(화)", "(수)", "(목)", "(금)", "(토)"), week)
    }

    @Test
    fun `달은 2026년 10월처럼, 1월도 앞에 0 없이`() {
        assertEquals("2026년 10월", Formatters.month(YearMonth.of(2026, 10)))
        assertEquals("2026년 1월", Formatters.month(YearMonth.of(2026, 1)))
    }

    @Test
    fun `기간 표기 - 설정은 2026·01, 서식 머리는 2026·01·01 (두 자리 채움)`() {
        assertEquals("2026.01", Formatters.periodMonth(LocalDate.of(2026, 1, 1)))
        assertEquals("2026.12", Formatters.periodMonth(LocalDate.of(2026, 12, 31)))
        assertEquals("2026.01.01", Formatters.periodDay(LocalDate.of(2026, 1, 1)))
        assertEquals("2027.02.28", Formatters.periodDay(LocalDate.of(2027, 2, 28)))
    }

    @Test
    fun `시각은 24시간 두 자리 시·분, 초는 버린다`() {
        assertEquals("14:05", Formatters.time(LocalTime.of(14, 5)))
        assertEquals("09:00", Formatters.time(LocalTime.of(9, 0)))
        assertEquals("00:00", Formatters.time(LocalTime.MIDNIGHT))
        assertEquals("23:59", Formatters.time(LocalTime.of(23, 59, 59)))
    }

    @Test
    fun `한 줄에 여러 값을 이을 때는 가운뎃점 앞뒤 한 칸`() {
        assertEquals("10:38 · 84.2 km · 업무", listOf("10:38", "84.2 km", "업무").joinToString(Formatters.SEPARATOR))
    }
}
