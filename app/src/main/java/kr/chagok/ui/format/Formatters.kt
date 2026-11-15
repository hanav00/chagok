package kr.chagok.ui.format

import java.text.NumberFormat
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

/** 화면·음성 안내에서 같이 쓰는 표시 형식 */
object Formatters {
    private val LOCALE = Locale.KOREA
    private const val MAN_PER_EOK = 10_000L // 1억 = 1만 만 원
    private val TIME = DateTimeFormatter.ofPattern("HH:mm", LOCALE)
    private val PERIOD_MONTH = DateTimeFormatter.ofPattern("yyyy.MM", LOCALE)
    private val PERIOD_DAY = DateTimeFormatter.ofPattern("yyyy.MM.dd", LOCALE)

    /** 한 줄에 여러 값을 이을 때 ("10:38 · 84.2 km · 업무") */
    const val SEPARATOR = " · "

    /** 12345.6 → "12,345.6" */
    fun km(value: Double, digits: Int = 1): String = NumberFormat.getNumberInstance(LOCALE).apply {
        minimumFractionDigits = digits
        maximumFractionDigits = digits
    }.format(value)

    /**
     * 금액(만 원 단위)을 억 단위로 끊어 읽기 쉽게.
     * 1500 → "1,500만 원", 10000 → "1억 원", 12000 → "1억 2,000만 원"
     */
    fun money(man: Double): String {
        val rounded = man.roundToLong()
        val sign = if (rounded < 0) "−" else ""
        val eok = abs(rounded) / MAN_PER_EOK
        val rest = abs(rounded) % MAN_PER_EOK
        val number = NumberFormat.getIntegerInstance(LOCALE)
        return when {
            eok == 0L -> "$sign${number.format(rest)}만 원"
            rest == 0L -> "$sign${number.format(eok)}억 원"
            else -> "$sign${number.format(eok)}억 ${number.format(rest)}만 원"
        }
    }

    /** 0.851 → "85.1%" */
    /** 32000 → "32,000" (원 단위 금액. 단위 글자는 문구 쪽에 둔다) */
    fun won(won: Long): String = NumberFormat.getIntegerInstance(LOCALE).format(won)

    fun percent(ratio: Double, digits: Int = 1): String = "%.${digits}f%%".format(LOCALE, ratio * 100)

    /** 2026-10-26 → "10.26 (월)" */
    fun day(date: LocalDate): String =
        "%d.%02d (%s)".format(date.monthValue, date.dayOfMonth, date.dayOfWeek.getDisplayName(TextStyle.SHORT, LOCALE))

    /** 2026-10 → "2026년 10월" */
    fun month(month: YearMonth): String = "${month.year}년 ${month.monthValue}월"

    /** 기간 표기: 2026-01-01 → "2026.01" (설정 사업연도) */
    fun periodMonth(date: LocalDate): String = date.format(PERIOD_MONTH)

    /** 기간 표기: 2026-01-01 → "2026.01.01" (서식 머리 사업연도) */
    fun periodDay(date: LocalDate): String = date.format(PERIOD_DAY)

    /** 14:05 */
    fun time(time: LocalTime): String = time.format(TIME)
}
