package kr.chagok.ui.simulator

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kr.chagok.AppContainer
import kr.chagok.domain.FiscalYear
import kr.chagok.domain.TripRules
import kr.chagok.domain.tax.MoneyInput
import kr.chagok.domain.tax.Ownership
import kr.chagok.domain.tax.TaxCalculator
import kr.chagok.domain.tax.TaxComparison
import kr.chagok.domain.tax.TaxConfig
import kr.chagok.ui.WhileUiSubscribed
import kr.chagok.ui.containerFactory
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * 절세 계산 상태. 입력값은 여기 들고 있어 탭을 오가도 남고, 차량 비용은 보유 형태마다 따로 기억한다.
 * 기록 쪽 업무사용비율은 이번 사업연도 운행으로 계산한다. 운행이 아직 없으면 예시 비율([TaxConfig.EXAMPLE_BUSINESS_RATIO])을
 * 쓰고 그렇다고 표시한다.
 */
class SimulatorViewModel(private val container: AppContainer) : ViewModel() {

    private val inputs = MutableStateFlow(SimulatorInputs())

    // 운행 목록이나 사업연도가 바뀔 때만 다시 센다. 입력값을 움직일 때마다 운행을 훑지 않게 한다. 달린 거리가 없으면 null
    private val logRatio = combine(container.trips.trips, container.today.date) { trips, today ->
        val totals = TripRules.totalsOf(TripRules.ofFiscalYear(trips, FiscalYear.of(today)))
        if (totals.total > 0) totals.businessRatio else null
    }.distinctUntilChanged()

    val state: StateFlow<SimulatorUiState> = combine(inputs, logRatio) { input, ratio ->
        input.toUiState(ratio)
    }.stateIn(viewModelScope, WhileUiSubscribed, SimulatorInputs().toUiState(null))

    fun selectOwnership(ownership: Ownership) = inputs.update { it.copy(ownership = ownership) }

    /** 슬라이더를 끌 때. 범위·눈금 밖 값은 맞춰 넣는다 */
    fun setCarInput(value: Float) = inputs.update { current ->
        val snapped = current.ownership.input.snap(value)
        current.copy(carInputs = current.carInputs + (current.ownership to snapped))
    }

    /** −/+ 버튼: 한 칸(step)씩. [direction] 은 -1 또는 1 */
    fun stepCarInput(direction: Int) = inputs.update { current ->
        val input = current.ownership.input
        val next = input.snap((current.carInput + direction * input.step).toFloat())
        current.copy(carInputs = current.carInputs + (current.ownership to next))
    }

    fun setOtherCost(value: Float) = inputs.update { it.copy(otherCost = TaxConfig.OTHER_COST_INPUT.snap(value)) }

    fun stepOtherCost(direction: Int) = inputs.update { current ->
        val input = TaxConfig.OTHER_COST_INPUT
        current.copy(otherCost = input.snap((current.otherCost + direction * input.step).toFloat()))
    }

    private fun SimulatorInputs.toUiState(ratio: Double?): SimulatorUiState {
        val comparison = TaxCalculator.compare(
            ownership,
            carInput.toDouble(),
            otherCost.toDouble(),
            ratio ?: TaxConfig.EXAMPLE_BUSINESS_RATIO,
        )
        return SimulatorUiState(
            ownership = ownership,
            carInput = carInput,
            yearlyCarCost = TaxCalculator.carCost(ownership, carInput.toDouble()).yearly,
            otherCost = otherCost,
            comparison = comparison,
            headline = headlineOf(comparison),
            isExampleRatio = ratio == null,
        )
    }

    // 보이는 값(만 원 반올림)으로 판단한다. 0.4만 원이면 "0만 원 줄어요"가 된다
    private fun headlineOf(comparison: TaxComparison): SimulatorHeadline = when {
        comparison.taxSaved.roundToLong() > 0 -> SimulatorHeadline.SAVED
        comparison.totalCost <= TaxConfig.NO_LOG_LIMIT -> SimulatorHeadline.UNDER_LIMIT
        else -> SimulatorHeadline.NO_LOG_BETTER
    }

    companion object {
        val Factory = containerFactory(::SimulatorViewModel)
    }
}

/** 사용자가 넣은 값 (만 원). 처음 값은 [MoneyInput.initial] */
private data class SimulatorInputs(
    val ownership: Ownership = Ownership.DEFAULT,
    val carInputs: Map<Ownership, Int> = Ownership.entries.associateWith { it.input.initial },
    val otherCost: Int = TaxConfig.OTHER_COST_INPUT.initial,
) {
    val carInput: Int get() = carInputs.getValue(ownership)
}

data class SimulatorUiState(
    val ownership: Ownership,
    /** 리스·렌트면 월 요금, 구입이면 차량 가격 (만 원) */
    val carInput: Int,
    /** 한 해 차량 비용 (만 원). 리스·렌트는 월 요금 × 12, 구입은 해마다 상각하는 금액 */
    val yearlyCarCost: Double,
    /** 그 밖의 비용 (만 원, 연) */
    val otherCost: Int,
    val comparison: TaxComparison,
    val headline: SimulatorHeadline,
    /** 운행 기록이 없어 예시 업무사용비율로 계산했는지 */
    val isExampleRatio: Boolean,
)

/** 맨 위 한 줄: 줄어드는 세금, 또는 기록이 유리하지 않은 이유 */
enum class SimulatorHeadline {
    SAVED,

    /** 관련비용이 기록 없이 인정되는 한도 이하 */
    UNDER_LIMIT,

    /** 업무사용비율이 낮아 기록 없이 받는 한도가 더 유리 */
    NO_LOG_BETTER,
}

/** 범위 안으로 넣고 가장 가까운 눈금(min + n × step)에 맞춘다 */
internal fun MoneyInput.snap(value: Float): Int {
    val steps = ((value - min) / step).roundToInt()
    return (min + steps * step).coerceIn(min, max)
}
