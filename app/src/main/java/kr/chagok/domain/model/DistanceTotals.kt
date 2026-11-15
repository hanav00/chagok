package kr.chagok.domain.model

/** 운행 묶음(한 달·사업연도)의 분류별 거리 합계 (km) */
data class DistanceTotals(
    val total: Double = 0.0,
    val commute: Double = 0.0,
    val business: Double = 0.0,
    val personal: Double = 0.0,
    val pending: Double = 0.0,
) {
    /** ⑫업무용 사용거리 = ⑧출·퇴근 + ⑨일반 업무 */
    val businessUseKm: Double get() = commute + business

    /** ⑬업무사용비율 = 업무용 사용거리 ÷ 총주행거리 */
    val businessRatio: Double get() = if (total > 0) businessUseKm / total else 0.0

    /** 한 분류의 거리. null 은 분류 대기 */
    fun kmOf(category: Category?): Double = when (category) {
        Category.COMMUTE -> commute
        Category.BUSINESS -> business
        Category.PERSONAL -> personal
        null -> pending
    }
}

/** 하루 운행의 한 분류 요약 (홈 "오늘 요약") */
data class CategoryDay(
    val count: Int,
    val km: Double,
    /** 도착한 곳 이름 (같은 곳은 한 번만) */
    val placeNames: List<String>,
)
