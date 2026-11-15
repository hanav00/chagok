package kr.chagok.domain.tax

import kr.chagok.domain.FiscalYear
import kr.chagok.domain.TripRules
import kr.chagok.testing.DemoSampleTrips
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 업무용승용차 관련비용 계산 [TaxCalculator].
 * 금액 단위는 만 원. 화면은 만 원 단위로 반올림해 보여 주므로 "화면 숫자"는 [shown] 으로 비교한다.
 */
class TaxCalculatorTest {
    private val delta = 1e-6

    private fun shown(value: Double): Long = Math.round(value)

    @Test
    fun `리스 월 150 + 그 밖의 비용 600, 업무사용비율 85·1퍼센트면 세금이 약 227만 원 줄어든다`() {
        val result = TaxCalculator.compare(Ownership.LEASE, carInput = 150.0, otherCost = 600.0, logRatio = 0.851)

        assertEquals(2_400.0, result.totalCost, delta)
        assertEquals(1_674.0, result.depreciation, delta)
        assertEquals(18L, shown(result.corpTaxSaved))
        assertEquals(209L, shown(result.incomeTaxSaved))
        assertEquals(227L, shown(result.taxSaved))
    }

    @Test
    fun `기준 예시 - 기록 없음은 62·5퍼센트 인정, 올해 1,254 · 이월 246 · 상여 900`() {
        val noLog = TaxCalculator.compare(Ownership.LEASE, carInput = 150.0, otherCost = 600.0, logRatio = 0.851).noLog

        assertEquals(0.625, noLog.ratio, 1e-9)
        assertEquals(1_500.0, noLog.businessAmount, delta)
        assertEquals(1_254L, shown(noLog.deductedNow))
        assertEquals(246.25, noLog.carryOver, delta)
        assertEquals(900.0, noLog.bonus, delta)
    }

    @Test
    fun `기준 예시 - 운행기록이 있으면 85·1퍼센트 인정, 올해 1,418 · 이월 625 · 상여 358`() {
        val withLog =
            TaxCalculator.compare(Ownership.LEASE, carInput = 150.0, otherCost = 600.0, logRatio = 0.851).withLog

        assertEquals(0.851, withLog.ratio, 0.0)
        assertEquals(2_042.4, withLog.businessAmount, delta)
        assertEquals(1_418L, shown(withLog.deductedNow))
        assertEquals(624.574, withLog.carryOver, delta)
        assertEquals(358L, shown(withLog.bonus))
    }

    @Test
    fun `시연 샘플 기록에서 계산한 실제 비율(85·07퍼센트)로도 줄어드는 세금은 227만 원`() {
        val trips = DemoSampleTrips.trips()
        val ratio =
            TripRules.totalsOf(TripRules.ofFiscalYear(trips, FiscalYear.of(DemoSampleTrips.TODAY))).businessRatio

        val result = TaxCalculator.compare(Ownership.LEASE, carInput = 150.0, otherCost = 600.0, logRatio = ratio)

        assertEquals(227L, shown(result.taxSaved))
    }

    @Test
    fun `절세 계산 화면의 처음 값은 기준 예시와 같다 (리스 · 월 150 · 그 밖의 비용 600)`() {
        assertEquals(Ownership.LEASE, Ownership.DEFAULT)
        assertEquals(150, Ownership.DEFAULT.input.initial)
        assertEquals(600, TaxConfig.OTHER_COST_INPUT.initial)
    }

    @Test
    fun `리스는 월 리스료 × 12가 1년 비용, 그중 93퍼센트가 감가상각비 상당액`() {
        val cost = TaxCalculator.carCost(Ownership.LEASE, 150.0)

        assertEquals(1_800.0, cost.yearly, delta)
        assertEquals(1_674.0, cost.depreciation, delta)
    }

    @Test
    fun `장기렌트는 월 렌트료 × 12가 1년 비용, 그중 70퍼센트가 감가상각비 상당액`() {
        val cost = TaxCalculator.carCost(Ownership.RENT, 150.0)

        assertEquals(1_800.0, cost.yearly, delta)
        assertEquals(1_260.0, cost.depreciation, delta)
    }

    @Test
    fun `구입은 차량 가격을 5년 정액으로 나눈 값이 해마다 비용이다`() {
        val cost = TaxCalculator.carCost(Ownership.BUY, 6_000.0)

        assertEquals(1_200.0, cost.yearly, 0.0)
        assertEquals(1_200.0, cost.depreciation, 0.0)
    }

    @Test
    fun `장기렌트 월 150 + 600, 85·1퍼센트 - 기록 없음은 감가상각비가 한도 아래라 이월 없음, 약 239만 원 줄어든다`() {
        val result = TaxCalculator.compare(Ownership.RENT, carInput = 150.0, otherCost = 600.0, logRatio = 0.851)

        assertEquals(0.0, result.noLog.carryOver, 0.0) // 1,260 × 62.5% = 787.5 ≤ 800
        assertEquals(272.26, result.withLog.carryOver, delta) // 1,260 × 85.1% − 800
        assertEquals(29.7154, result.corpTaxSaved, delta)
        assertEquals(208.824, result.incomeTaxSaved, delta)
        assertEquals(239L, shown(result.taxSaved))
    }

    @Test
    fun `구입 6,000 + 600, 85·1퍼센트 - 관련비용 1,800 이라 기록 없음도 83·3퍼센트 인정, 약 13만 원 줄어든다`() {
        val result = TaxCalculator.compare(Ownership.BUY, carInput = 6_000.0, otherCost = 600.0, logRatio = 0.851)

        assertEquals(1_800.0, result.totalCost, delta)
        assertEquals(1_500.0 / 1_800.0, result.noLog.ratio, 1e-9)
        assertEquals(200.0, result.noLog.carryOver, delta) // 1,200 × 83.3% − 800
        assertEquals(221.2, result.withLog.carryOver, delta) // 1,200 × 85.1% − 800
        assertEquals(13L, shown(result.taxSaved))
    }

    @Test
    fun `관련비용이 정확히 1,500만 원이면 기록 없이도 100퍼센트 인정`() {
        val result = TaxCalculator.compare(Ownership.LEASE, carInput = 100.0, otherCost = 300.0, logRatio = 0.9)

        assertEquals(TaxConfig.NO_LOG_LIMIT, result.totalCost, 0.0)
        assertEquals(1.0, result.noLog.ratio, 0.0)
        assertEquals(0.0, result.noLog.bonus, 0.0)
    }

    @Test
    fun `관련비용이 1,500만 원을 넘으면 기록 없음 비율은 1,500 ÷ 관련비용`() {
        val result = TaxCalculator.compare(Ownership.LEASE, carInput = 100.0, otherCost = 350.0, logRatio = 0.9)

        assertEquals(1_550.0, result.totalCost, 0.0)
        assertEquals(1_500.0 / 1_550.0, result.noLog.ratio, 1e-9)
        assertEquals(1_500.0, result.noLog.businessAmount, delta)
    }

    @Test
    fun `업무사용 감가상각비가 800만 원 이하면 이월 없이 업무사용금액 전부가 올해 비용`() {
        val result = TaxCalculator.compare(Ownership.RENT, carInput = 50.0, otherCost = 300.0, logRatio = 0.9)

        assertEquals(0.0, result.noLog.carryOver, 0.0)
        assertEquals(0.0, result.withLog.carryOver, 0.0)
        assertEquals(result.withLog.businessAmount, result.withLog.deductedNow, 0.0)
    }

    @Test
    fun `업무사용 감가상각비가 정확히 800만 원이면 이월은 0`() {
        // 구입 4,000 → 해마다 800. 업무사용비율 100% 면 딱 한도
        val result = TaxCalculator.compare(Ownership.BUY, carInput = 4_000.0, otherCost = 0.0, logRatio = 1.0)

        assertEquals(0.0, result.withLog.carryOver, 0.0)
    }

    @Test
    fun `업무사용금액 = 올해 비용 + 이월, 관련비용 = 업무사용금액 + 상여`() {
        val result = TaxCalculator.compare(Ownership.LEASE, carInput = 300.0, otherCost = 900.0, logRatio = 0.7)

        listOf(result.noLog, result.withLog).forEach {
            assertEquals(it.businessAmount, it.deductedNow + it.carryOver, delta)
            assertEquals(result.totalCost, it.businessAmount + it.bonus, delta)
        }
    }

    @Test
    fun `관련비용이 1,500만 원 이하인데 기록 비율이 100퍼센트 아래면 기록이 오히려 불리하다 (음수)`() {
        val result = TaxCalculator.compare(Ownership.RENT, carInput = 50.0, otherCost = 300.0, logRatio = 0.9)

        assertEquals(1.0, result.noLog.ratio, 0.0)
        assertTrue(result.taxSaved < 0)
    }

    @Test
    fun `관련비용이 1,500만 원 이하이고 기록 비율도 100퍼센트면 차이가 없다 (0)`() {
        val result = TaxCalculator.compare(Ownership.RENT, carInput = 50.0, otherCost = 300.0, logRatio = 1.0)

        assertEquals(0.0, result.taxSaved, delta)
    }

    @Test
    fun `한도를 넘어도 기록 비율이 기록 없음 비율보다 낮으면 음수`() {
        val result = TaxCalculator.compare(Ownership.LEASE, carInput = 150.0, otherCost = 600.0, logRatio = 0.3)

        assertTrue(result.taxSaved < 0)
    }

    @Test
    fun `줄어드는 세금 = 법인세 + 소득세`() {
        val result = TaxCalculator.compare(Ownership.LEASE, carInput = 150.0, otherCost = 600.0, logRatio = 0.851)

        assertEquals(result.corpTaxSaved + result.incomeTaxSaved, result.taxSaved, 0.0)
    }
}
