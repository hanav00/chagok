package kr.chagok.domain

import java.time.LocalDate

/**
 * 사업연도. 운행은 날짜로 저장하므로 시작 월을 나중에 바꿔도 묶음만 다시 계산된다.
 * @property year 사업연도가 시작하는 해
 */
data class FiscalYear(val year: Int, val startMonth: Int = ChagokConfig.FISCAL_YEAR_START_MONTH) {
    val start: LocalDate get() = LocalDate.of(year, startMonth, 1)
    val end: LocalDate get() = start.plusYears(1).minusDays(1)

    operator fun contains(date: LocalDate): Boolean = date in start..end

    companion object {
        fun of(date: LocalDate, startMonth: Int = ChagokConfig.FISCAL_YEAR_START_MONTH): FiscalYear =
            FiscalYear(if (date.monthValue >= startMonth) date.year else date.year - 1, startMonth)
    }
}
