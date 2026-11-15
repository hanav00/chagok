package kr.chagok.domain.tax

/**
 * 절세 계산 숫자 (금액 단위: 만 원). 계산은 [TaxCalculator].
 * 근거: 법인세법 제27조의2 · 시행령 제50조의2 · 시행규칙 제27조의2
 * 가정: 일반 법인(부동산임대업 주업 아님), 업무전용자동차보험 가입, 12개월 보유.
 * TODO: 세무사 검토
 */
object TaxConfig {
    /** 운행기록부가 없을 때 관련비용 인정 한도 (차 1대·연) */
    const val NO_LOG_LIMIT = 1_500.0

    /** 업무사용금액 중 감가상각비(상당액) 연 한도. 넘는 부분은 다음 해로 이월 */
    const val DEPRECIATION_LIMIT = 800.0

    /** 리스: (리스료 − 보험료·자동차세) × 93% (수선유지비 7% 간주). 리스료에 보험료·자동차세가 없다고 본다 */
    const val LEASE_DEPRECIATION_RATE = 0.93

    /** 장기렌트: 렌트료 × 70% */
    const val RENT_DEPRECIATION_RATE = 0.70

    /** 구입: 정액법 5년 (강제 상각) */
    const val BUY_DEPRECIATION_YEARS = 5

    // 세율은 지방소득세를 포함한 대표값이다. 실제 세율은 회사·사람마다 다르다
    /** 법인세 10%(과세표준 2억 이하, 2026년 개시 사업연도부터) + 지방소득세 1% */
    const val CORP_TAX_RATE = 0.11

    /** 차량 사용자 소득세 35% 구간 + 지방소득세 3.5% */
    const val INCOME_TAX_RATE = 0.385

    /**
     * 운행 기록이 아직 없을 때 절세 계산에 쓰는 예시 업무사용비율.
     * 설치 직후 비율 0%로 계산하면 기록이 없는 쪽이 늘 유리하게 나온다
     */
    const val EXAMPLE_BUSINESS_RATIO = 0.85

    /** 그 밖의 비용 (유류비·보험료·자동차세·수리비·통행료, 연) 입력 범위 */
    val OTHER_COST_INPUT = MoneyInput(min = 0, max = 2_000, step = 50, initial = 600)
}

/** 슬라이더 + −/+ 입력 범위 (만 원) */
data class MoneyInput(val min: Int, val max: Int, val step: Int, val initial: Int)

/** 차량 보유 형태. 리스·렌트는 월 요금, 구입은 차량 가격을 넣는다 */
enum class Ownership(val input: MoneyInput, val isMonthly: Boolean) {
    LEASE(MoneyInput(min = 30, max = 400, step = 5, initial = 150), isMonthly = true),
    RENT(MoneyInput(min = 30, max = 400, step = 5, initial = 150), isMonthly = true),
    BUY(MoneyInput(min = 2_000, max = 15_000, step = 500, initial = 6_000), isMonthly = false);

    companion object {
        val DEFAULT = LEASE
    }
}
