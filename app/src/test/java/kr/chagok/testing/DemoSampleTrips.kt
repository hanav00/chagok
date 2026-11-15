package kr.chagok.testing

import kr.chagok.domain.model.Category
import kr.chagok.domain.model.ClassifyReason
import kr.chagok.domain.model.Trip
import kr.chagok.domain.model.TripPlace
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth

/**
 * 시연용 샘플 운행 목록. 홈 화면에 보이는 숫자(10월 539.6km, 사업연도 업무사용비율 85.1%)를 앱 규칙으로 다시 계산해 보려고 둔다.
 * 10월은 운행 하나하나, 1~9월은 달 합계만 있어 달마다 분류별 운행 한 건씩으로 맞춘다. 계기판은 끊기지 않게 이어 붙여
 * [END_ODOMETER_KM] 에서 끝난다.
 * 디버그 시드(src/debug DemoSample)와 따로 둔다. 숫자를 독립적으로 옮겨 적어야 시드가 틀려도 잡힌다.
 */
object DemoSampleTrips {
    val TODAY: LocalDate = LocalDate.of(2026, 10, 26)
    val MONTH: YearMonth = YearMonth.of(2026, 10)

    /** 샘플 운행의 마지막 도착 계기판 (시연용 가짜 차의 처음 값) */
    const val END_ODOMETER_KM = 23_418.2

    const val PARK = 1L
    const val CHOI = 2L

    private val PEOPLE = mapOf("park" to PARK, "choi" to CHOI)

    val PLACES: Map<String, TripPlace> = listOf(
        "home" to "집 (분당 정자동)",
        "office" to "회사 · 성일정밀 판교",
        "factory" to "성일정밀 평택공장",
        "c-elec" to "○○전자 판교사옥",
        "c-mulsan" to "△△물산 강남 본사",
        "c-agency" to "□□대리점 수원점",
        "mart" to "이마트 분당점",
        "golf" to "남서울CC",
    ).associate { (id, name) -> id to TripPlace(name = name, poiId = id) }

    // 장소 사이 거리(km). 키는 두 장소 id 를 정렬해 "|" 로 이은 것
    private val DISTANCES_KM = mapOf(
        "home|office" to 9.6,
        "c-elec|office" to 4.2,
        "c-mulsan|office" to 18.7,
        "c-agency|office" to 27.3,
        "factory|office" to 52.1,
        "home|mart" to 3.8,
        "golf|home" to 21.5,
    )
    private const val DEFAULT_DISTANCE_KM = 12.0

    fun distanceKm(from: String, to: String): Double =
        DISTANCES_KM[listOf(from, to).sorted().joinToString("|")] ?: DEFAULT_DISTANCE_KM

    private data class Row(val day: Int, val person: String, val from: String, val to: String, val category: Category)

    // 10월 샘플 운행. 비고는 합계와 상관없어 옮기지 않는다
    private val OCTOBER_ROWS = listOf(
        Row(1, "park", "home", "office", Category.COMMUTE),
        Row(1, "park", "office", "c-elec", Category.BUSINESS),
        Row(1, "park", "c-elec", "office", Category.BUSINESS),
        Row(1, "park", "office", "home", Category.COMMUTE),
        Row(2, "park", "home", "office", Category.COMMUTE),
        Row(2, "park", "office", "factory", Category.BUSINESS),
        Row(2, "park", "factory", "office", Category.BUSINESS),
        Row(2, "park", "office", "home", Category.COMMUTE),
        Row(3, "park", "home", "mart", Category.PERSONAL),
        Row(3, "park", "mart", "home", Category.PERSONAL),
        Row(5, "park", "home", "office", Category.COMMUTE),
        Row(5, "choi", "office", "c-mulsan", Category.BUSINESS),
        Row(5, "choi", "c-mulsan", "office", Category.BUSINESS),
        Row(5, "park", "office", "home", Category.COMMUTE),
        Row(6, "park", "home", "office", Category.COMMUTE),
        Row(6, "choi", "office", "c-agency", Category.BUSINESS),
        Row(6, "choi", "c-agency", "office", Category.BUSINESS),
        Row(6, "park", "office", "home", Category.COMMUTE),
        Row(11, "park", "home", "golf", Category.PERSONAL),
        Row(11, "park", "golf", "home", Category.PERSONAL),
        Row(13, "park", "home", "office", Category.COMMUTE),
        Row(13, "park", "office", "c-elec", Category.BUSINESS),
        Row(13, "park", "c-elec", "office", Category.BUSINESS),
        Row(13, "park", "office", "home", Category.COMMUTE),
        Row(20, "park", "home", "office", Category.COMMUTE),
        Row(20, "park", "office", "factory", Category.BUSINESS),
        Row(20, "park", "factory", "office", Category.BUSINESS),
        Row(20, "park", "office", "home", Category.COMMUTE),
        Row(23, "park", "home", "office", Category.COMMUTE),
        Row(23, "choi", "office", "c-mulsan", Category.BUSINESS),
        Row(23, "choi", "c-mulsan", "office", Category.BUSINESS),
        Row(23, "park", "office", "home", Category.COMMUTE),
    )

    /** 1~9월 달 합계 (km). 개인 = total − commute − business */
    private data class MonthTotal(val month: Int, val total: Double, val commute: Double, val business: Double)

    private val PRIOR_MONTHS = listOf(
        MonthTotal(1, 1810.0, 690.0, 880.0),
        MonthTotal(2, 1640.0, 610.0, 820.0),
        MonthTotal(3, 2050.0, 720.0, 1110.0),
        MonthTotal(4, 1980.0, 700.0, 1050.0),
        MonthTotal(5, 2210.0, 680.0, 1190.0),
        MonthTotal(6, 1930.0, 700.0, 980.0),
        MonthTotal(7, 2380.0, 650.0, 1210.0),
        MonthTotal(8, 2120.0, 600.0, 1020.0),
        MonthTotal(9, 1960.0, 710.0, 1030.0),
    )

    private const val PRIOR_MONTH_DAY = 15

    private class Plan(
        val date: LocalDate,
        val personId: Long,
        val from: String?,
        val to: String?,
        val category: Category,
        val km: Double,
    )

    /** 1~9월 + 10월 샘플 운행. id 는 1부터 날짜 순서대로 */
    fun trips(): List<Trip> {
        val plans = priorMonthPlans() + octoberPlans()
        var odometer = END_ODOMETER_KM - plans.sumOf { it.km }
        return plans.mapIndexed { index, plan ->
            val startKm = odometer
            odometer += plan.km
            val isPersonal = plan.category == Category.PERSONAL
            Trip(
                id = index + 1L,
                date = plan.date,
                personId = plan.personId,
                origin = plan.from?.takeUnless { isPersonal }?.let(PLACES::getValue),
                destination = plan.to?.takeUnless { isPersonal }?.let(PLACES::getValue),
                startKm = startKm,
                endKm = odometer,
                startedAt = LocalTime.of(9, 0),
                endedAt = LocalTime.of(9, 30),
                category = plan.category,
                reason = ClassifyReason.defaultOf(plan.category),
            )
        }
    }

    private fun priorMonthPlans(): List<Plan> = PRIOR_MONTHS.flatMap { m ->
        val date = LocalDate.of(MONTH.year, m.month, PRIOR_MONTH_DAY)
        listOf(
            Plan(date, PARK, "home", "office", Category.COMMUTE, m.commute),
            Plan(date, PARK, "office", "c-elec", Category.BUSINESS, m.business),
            Plan(date, PARK, null, null, Category.PERSONAL, m.total - m.commute - m.business),
        )
    }

    private fun octoberPlans(): List<Plan> = OCTOBER_ROWS.map { row ->
        Plan(
            date = MONTH.atDay(row.day),
            personId = PEOPLE.getValue(row.person),
            from = row.from,
            to = row.to,
            category = row.category,
            km = distanceKm(row.from, row.to),
        )
    }
}
