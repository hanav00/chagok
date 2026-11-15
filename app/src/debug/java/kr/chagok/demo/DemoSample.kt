package kr.chagok.demo

import kr.chagok.domain.model.Category
import kr.chagok.domain.model.TripPlace
import kr.chagok.domain.model.VehicleInfo

/**
 * [디버그 전용] 시연 샘플 값.
 * 10월 1일부터 쓰던 공용차: 10월은 운행 하나하나, 1~9월은 달마다 분류별 운행 한 건씩으로 합계만 맞춘다.
 * 11월부터 넣는 날 전날까지는 요일로 채워, 언제 넣어도 홈의 이번 달 탑이 차 있다.
 */
internal object DemoSample {
    const val YEAR = 2026
    const val MONTH = 10

    /** 진행자가 외우기 쉬운 관리자 PIN */
    const val ADMIN_PIN = "2580"

    /** 에뮬레이터 계기판이 이보다 작으면 이 값에서 끝나게 둔다. Virtual Console 을 이보다 크게 맞추고 시연한다 */
    const val DEFAULT_END_ODOMETER_KM = 23_418.2

    val VEHICLE = VehicleInfo(model = "그랜저", plate = "123가 4567", company = "(주)성일정밀", bizNo = "123-45-67890")

    data class SamplePerson(val key: String, val name: String, val dept: String)

    val PEOPLE = listOf(
        SamplePerson("park", "박정훈", "대표이사"),
        SamplePerson("choi", "최서연", "영업팀"),
    )

    val PLACES = mapOf(
        "home" to TripPlace(name = "집 (분당 정자동)", poiId = "home"),
        "office" to TripPlace(name = "회사 · 성일정밀 판교", poiId = "office"),
        "factory" to TripPlace(name = "성일정밀 평택공장", poiId = "factory"),
        "c-elec" to TripPlace(name = "○○전자 판교사옥", poiId = "c-elec"),
        "c-mulsan" to TripPlace(name = "△△물산 강남 본사", poiId = "c-mulsan"),
        "c-agency" to TripPlace(name = "□□대리점 수원점", poiId = "c-agency"),
        "mart" to TripPlace(name = "이마트 분당점", poiId = "mart"),
        "golf" to TripPlace(name = "남서울CC", poiId = "golf"),
    )

    private val DISTANCES_KM = mapOf(
        "home|office" to 9.6, "c-elec|office" to 4.2, "c-mulsan|office" to 18.7, "c-agency|office" to 27.3,
        "factory|office" to 52.1, "home|mart" to 3.8, "golf|home" to 21.5,
    )
    private const val DEFAULT_DISTANCE_KM = 12.0

    fun distanceKm(from: String, to: String): Double =
        DISTANCES_KM[listOf(from, to).sorted().joinToString("|")] ?: DEFAULT_DISTANCE_KM

    data class SampleTrip(
        val day: Int,
        val person: String,
        val from: String,
        val to: String,
        val category: Category,
        val note: String = "",
    )

    val OCTOBER = listOf(
        SampleTrip(1, "park", "home", "office", Category.COMMUTE),
        SampleTrip(1, "park", "office", "c-elec", Category.BUSINESS, "견적 협의"),
        SampleTrip(1, "park", "c-elec", "office", Category.BUSINESS),
        SampleTrip(1, "park", "office", "home", Category.COMMUTE),
        SampleTrip(2, "park", "home", "office", Category.COMMUTE),
        SampleTrip(2, "park", "office", "factory", Category.BUSINESS, "생산 일정 점검"),
        SampleTrip(2, "park", "factory", "office", Category.BUSINESS),
        SampleTrip(2, "park", "office", "home", Category.COMMUTE),
        SampleTrip(3, "park", "home", "mart", Category.PERSONAL),
        SampleTrip(3, "park", "mart", "home", Category.PERSONAL),
        SampleTrip(5, "park", "home", "office", Category.COMMUTE),
        SampleTrip(5, "choi", "office", "c-mulsan", Category.BUSINESS, "분기 계약 미팅"),
        SampleTrip(5, "choi", "c-mulsan", "office", Category.BUSINESS),
        SampleTrip(5, "park", "office", "home", Category.COMMUTE),
        SampleTrip(6, "park", "home", "office", Category.COMMUTE),
        SampleTrip(6, "choi", "office", "c-agency", Category.BUSINESS, "신제품 판촉"),
        SampleTrip(6, "choi", "c-agency", "office", Category.BUSINESS),
        SampleTrip(6, "park", "office", "home", Category.COMMUTE),
        SampleTrip(11, "park", "home", "golf", Category.PERSONAL),
        SampleTrip(11, "park", "golf", "home", Category.PERSONAL),
        SampleTrip(13, "park", "home", "office", Category.COMMUTE),
        SampleTrip(13, "park", "office", "c-elec", Category.BUSINESS, "납품 검수"),
        SampleTrip(13, "park", "c-elec", "office", Category.BUSINESS),
        SampleTrip(13, "park", "office", "home", Category.COMMUTE),
        SampleTrip(20, "park", "home", "office", Category.COMMUTE),
        SampleTrip(20, "park", "office", "factory", Category.BUSINESS, "설비 점검"),
        SampleTrip(20, "park", "factory", "office", Category.BUSINESS),
        SampleTrip(20, "park", "office", "home", Category.COMMUTE),
        SampleTrip(23, "park", "home", "office", Category.COMMUTE),
        SampleTrip(23, "choi", "office", "c-mulsan", Category.BUSINESS, "단가 협상"),
        SampleTrip(23, "choi", "c-mulsan", "office", Category.BUSINESS),
        SampleTrip(23, "park", "office", "home", Category.COMMUTE),
    )

    /** 11월부터: 평일마다 거래처 한 곳을 돌아가며 왕복한다 (장소 id, 방문 목적) */
    val WEEKDAY_VISITS = listOf(
        "c-elec" to "견적 협의",
        "c-mulsan" to "분기 계약 미팅",
        "factory" to "생산 일정 점검",
        "c-agency" to "신제품 판촉",
        "c-elec" to "납품 검수",
    )

    /** 11월부터: 토요일 개인 왕복을 번갈아 간다. 일요일은 쉰다 */
    val SATURDAY_PLACES = listOf("mart", "golf")

    /** 1~9월 달 합계 (km): month, total, commute, business. 개인 = total − commute − business */
    data class MonthTotal(val month: Int, val total: Double, val commute: Double, val business: Double)

    val PRIOR_MONTHS = listOf(
        MonthTotal(1, 1810.0, 690.0, 880.0), MonthTotal(2, 1640.0, 610.0, 820.0), MonthTotal(3, 2050.0, 720.0, 1110.0),
        MonthTotal(4, 1980.0, 700.0, 1050.0), MonthTotal(5, 2210.0, 680.0, 1190.0), MonthTotal(6, 1930.0, 700.0, 980.0),
        MonthTotal(7, 2380.0, 650.0, 1210.0), MonthTotal(8, 2120.0, 600.0, 1020.0),
        MonthTotal(9, 1960.0, 710.0, 1030.0),
    )
}
