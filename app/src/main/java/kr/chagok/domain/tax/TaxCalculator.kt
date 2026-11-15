package kr.chagok.domain.tax

/**
 * 업무용승용차 관련비용 계산 (만 원, 12개월 보유). 업무사용금액은 관련비용 × 업무사용비율이고, 비율은 기록이 없으면
 * 1,500만 원 한도로, 있으면 업무용 사용거리 ÷ 총주행거리로 정한다. 그중 감가상각비가 연 800만 원을 넘는 만큼은
 * 다음 해로 이월하고, 업무사용금액 밖의 비용은 인정하지 않고 차량 사용자 상여로 본다.
 */
object TaxCalculator {
    private const val MONTHS_PER_YEAR = 12

    /** 보유 형태별 연간 차량 비용과 그중 감가상각비(상당액). [carInput] 은 리스·렌트면 월 요금, 구입이면 차량 가격 */
    fun carCost(ownership: Ownership, carInput: Double): CarCost = when (ownership) {
        Ownership.BUY -> {
            val depreciation = carInput / TaxConfig.BUY_DEPRECIATION_YEARS
            CarCost(yearly = depreciation, depreciation = depreciation)
        }
        Ownership.LEASE -> monthly(carInput, TaxConfig.LEASE_DEPRECIATION_RATE)
        Ownership.RENT -> monthly(carInput, TaxConfig.RENT_DEPRECIATION_RATE)
    }

    /** 기록 없음 vs 운행기록부([logRatio]) 비교. 줄어드는 세금은 양수일수록 기록이 유리 */
    fun compare(ownership: Ownership, carInput: Double, otherCost: Double, logRatio: Double): TaxComparison {
        val car = carCost(ownership, carInput)
        val totalCost = car.yearly + otherCost
        val noLog = treat(totalCost, car.depreciation, noLogRatio(totalCost))
        val withLog = treat(totalCost, car.depreciation, logRatio)
        return TaxComparison(
            totalCost = totalCost,
            depreciation = car.depreciation,
            noLog = noLog,
            withLog = withLog,
            corpTaxSaved = (withLog.deductedNow - noLog.deductedNow) * TaxConfig.CORP_TAX_RATE,
            incomeTaxSaved = (noLog.bonus - withLog.bonus) * TaxConfig.INCOME_TAX_RATE,
        )
    }

    private fun monthly(monthlyFee: Double, depreciationRate: Double): CarCost {
        val yearly = monthlyFee * MONTHS_PER_YEAR
        return CarCost(yearly = yearly, depreciation = yearly * depreciationRate)
    }

    private fun noLogRatio(totalCost: Double): Double =
        if (totalCost <= TaxConfig.NO_LOG_LIMIT) 1.0 else TaxConfig.NO_LOG_LIMIT / totalCost

    // 업무사용비율 하나로 올해 비용 인정·이월·상여를 계산
    private fun treat(totalCost: Double, depreciation: Double, ratio: Double): CostTreatment {
        val businessAmount = totalCost * ratio
        val carryOver = maxOf(0.0, depreciation * ratio - TaxConfig.DEPRECIATION_LIMIT)
        return CostTreatment(
            ratio = ratio,
            businessAmount = businessAmount,
            deductedNow = businessAmount - carryOver,
            carryOver = carryOver,
            bonus = totalCost - businessAmount,
        )
    }
}

data class CarCost(val yearly: Double, val depreciation: Double)

/** 업무사용비율 하나로 본 올해 비용 처리 (만 원) */
data class CostTreatment(
    val ratio: Double,
    val businessAmount: Double,
    /** 올해 비용 인정 */
    val deductedNow: Double,
    /** 다음 해로 이월 */
    val carryOver: Double,
    /** 사용자 상여 처리 */
    val bonus: Double,
)

data class TaxComparison(
    val totalCost: Double,
    val depreciation: Double,
    val noLog: CostTreatment,
    val withLog: CostTreatment,
    val corpTaxSaved: Double,
    val incomeTaxSaved: Double,
) {
    val taxSaved: Double get() = corpTaxSaved + incomeTaxSaved
}
