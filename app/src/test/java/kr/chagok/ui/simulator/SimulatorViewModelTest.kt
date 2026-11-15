package kr.chagok.ui.simulator

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kr.chagok.domain.model.Category
import kr.chagok.domain.tax.Ownership
import kr.chagok.domain.tax.TaxConfig
import kr.chagok.testing.TestApp.Companion.await
import kr.chagok.testing.TripFixtures.trip
import kr.chagok.testing.ViewModelTestRule
import kr.chagok.testing.seedTrips
import kr.chagok.ui.format.Formatters
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate
import kotlin.math.abs

/**
 * 절세 계산 탭: 리스 월 150만 원 + 그 밖의 비용 연 600만 원,
 * 이 차의 업무사용비율 85.1% → "기록 덕분에 세금 약 227만 원 줄어요".
 * 입력값은 보유 형태마다 따로 기억하고, 슬라이더·−/+ 값은 눈금과 범위에 맞춘다.
 */
@RunWith(RobolectricTestRunner::class)
class SimulatorViewModelTest {
    @get:Rule
    val rule = ViewModelTestRule()
    private val app get() = rule.app

    private fun simulator() = rule.viewModel { SimulatorViewModel(app.container) }

    /** 올해 업무 851km · 개인 149km → 업무사용비율 85.1% (작년 운행은 비율에 안 들어간다) */
    private suspend fun seedRatio851() = app.seedTrips(
        trip(startKm = 0.0, endKm = 5_000.0, date = LocalDate.of(2025, 6, 1), category = Category.PERSONAL),
        trip(startKm = 5_000.0, endKm = 5_851.0, date = LocalDate.of(2026, 4, 1)),
        trip(startKm = 5_851.0, endKm = 6_000.0, category = Category.PERSONAL),
    )

    /**
     * 입력만 바꿔 보는 테스트. 운행이 없어 기록 쪽은 예시 비율로 계산한다.
     * 상태는 구독할 때만 계산되므로(WhileSubscribed) 화면처럼 구독한 채로 바뀐 값을 차례로 받는다.
     */
    private fun inputTest(
        block: suspend ReceiveTurbine<SimulatorUiState>.(SimulatorViewModel) -> Unit,
    ) = runBlocking<Unit> {
        val simulator = simulator()
        simulator.state.test {
            awaitItem() // 처음 값
            block(simulator)
            cancelAndIgnoreRemainingEvents()
        }
    }

    private suspend fun SimulatorViewModel.awaitRatio(ratio: Double) =
        await(state) { abs(it.comparison.withLog.ratio - ratio) < 1e-9 }

    @Test
    fun `처음 열면 → 리스 월 150만 원, 그 밖의 비용 연 600만 원`() = runBlocking<Unit> {
        val state = simulator().state.first()

        assertEquals(Ownership.LEASE, state.ownership)
        assertEquals(150, state.carInput)
        assertEquals(600, state.otherCost)
    }

    @Test
    fun `시연 값(리스 150·그 밖 600·비율 85·1퍼센트) → 기록 덕분에 세금 약 227만 원`() = runBlocking<Unit> {
        app.completeFirstRun()
        seedRatio851()

        val state = simulator().awaitRatio(0.851)

        assertEquals("227만 원", Formatters.money(state.comparison.taxSaved))
        // 연간 비용 = 150 × 12 + 600, 기록 없으면 1,500만 원까지만 인정
        assertEquals(2_400.0, state.comparison.totalCost, 1e-9)
        assertEquals(0.625, state.comparison.noLog.ratio, 1e-9)
    }

    @Test
    fun `기록 쪽 비율은 운행이 바뀌면 다시 센다`() = runBlocking<Unit> {
        app.completeFirstRun()
        val trips = seedRatio851()
        val simulator = simulator()
        simulator.awaitRatio(0.851)

        app.trips.classify(trips.last().id, Category.BUSINESS)

        simulator.awaitRatio(1.0)
    }

    @Test
    fun `운행이 하나도 없다 → 예시 비율로 계산하고 예시라고 알린다 (0퍼센트로 '기록 없이가 유리'를 보이지 않게)`() = runBlocking<Unit> {
        app.completeFirstRun()

        val state = simulator().state.first()
        assertEquals(TaxConfig.EXAMPLE_BUSINESS_RATIO, state.comparison.withLog.ratio, 0.0)
        assertTrue(state.isExampleRatio)
    }

    @Test
    fun `리스 200으로 바꾸고 구입으로 → 구입은 처음 값 6,000, 구입 8,000 넣고 리스로 돌아오면 200 그대로`() = inputTest { simulator ->
        rule.onMain { simulator.setCarInput(200f) }
        assertEquals(200, awaitItem().carInput)

        rule.onMain { simulator.selectOwnership(Ownership.BUY) }
        assertEquals(6_000, awaitItem().carInput)

        rule.onMain { simulator.setCarInput(8_000f) }
        assertEquals(8_000, awaitItem().carInput)

        rule.onMain { simulator.selectOwnership(Ownership.LEASE) }
        assertEquals(200, awaitItem().carInput)

        rule.onMain { simulator.selectOwnership(Ownership.BUY) }
        assertEquals(8_000, awaitItem().carInput)
    }

    @Test
    fun `렌트는 리스와 따로 기억한다`() = inputTest { simulator ->
        rule.onMain { simulator.setCarInput(300f) }
        awaitItem()

        rule.onMain { simulator.selectOwnership(Ownership.RENT) }

        val rent = awaitItem()
        assertEquals(Ownership.RENT, rent.ownership)
        assertEquals(Ownership.RENT.input.initial, rent.carInput)
    }

    @Test
    fun `그 밖의 비용은 보유 형태를 바꿔도 그대로`() = inputTest { simulator ->
        rule.onMain { simulator.setOtherCost(900f) }
        awaitItem()

        rule.onMain { simulator.selectOwnership(Ownership.BUY) }

        assertEquals(900, awaitItem().otherCost)
    }

    @Test
    fun `슬라이더 값 → 가장 가까운 눈금(리스 5만 원 단위)에 맞춘다`() = inputTest { simulator ->
        rule.onMain { simulator.setCarInput(153f) }
        assertEquals(155, awaitItem().carInput)

        rule.onMain { simulator.setCarInput(147.4f) }
        assertEquals(145, awaitItem().carInput)
    }

    @Test
    fun `슬라이더 값이 범위 밖 → 최소·최대로 (리스 30~400)`() = inputTest { simulator ->
        rule.onMain { simulator.setCarInput(10f) }
        assertEquals(30, awaitItem().carInput)

        rule.onMain { simulator.setCarInput(9_999f) }
        assertEquals(400, awaitItem().carInput)
    }

    @Test
    fun `−·+ 한 칸 → 리스는 5만 원씩, 끝에서는 더 가지 않는다`() = inputTest { simulator ->
        rule.onMain { simulator.stepCarInput(-1) }
        assertEquals(145, awaitItem().carInput)

        rule.onMain { simulator.setCarInput(400f) }
        assertEquals(400, awaitItem().carInput)
        rule.onMain { simulator.stepCarInput(1) } // 최대에서 + → 그대로라 새 값이 없다
        rule.onMain { simulator.stepCarInput(-1) }
        assertEquals("400 다음 값이 바로 395 (405 를 거치지 않았다)", 395, awaitItem().carInput)
    }

    @Test
    fun `구입 −·+ 한 칸 → 500만 원씩, 최소 2,000 아래로는 안 간다`() = inputTest { simulator ->
        rule.onMain { simulator.selectOwnership(Ownership.BUY) }
        assertEquals(6_000, awaitItem().carInput)

        rule.onMain { simulator.stepCarInput(1) }
        assertEquals(6_500, awaitItem().carInput)

        rule.onMain { simulator.setCarInput(2_000f) }
        assertEquals(2_000, awaitItem().carInput)
        rule.onMain { simulator.stepCarInput(-1) }
        rule.onMain { simulator.stepCarInput(1) }
        assertEquals(2_500, awaitItem().carInput)
    }

    @Test
    fun `그 밖의 비용 → 50만 원 눈금, 0~2,000 범위`() = inputTest { simulator ->
        rule.onMain { simulator.setOtherCost(630f) }
        assertEquals(650, awaitItem().otherCost)

        rule.onMain { simulator.setOtherCost(-5f) }
        assertEquals(0, awaitItem().otherCost)
        rule.onMain { simulator.stepOtherCost(-1) }
        rule.onMain { simulator.stepOtherCost(1) }
        assertEquals("0 다음 값이 바로 50 (−50 을 거치지 않았다)", 50, awaitItem().otherCost)

        rule.onMain { simulator.setOtherCost(2_100f) }
        assertEquals(TaxConfig.OTHER_COST_INPUT.max, awaitItem().otherCost)
        rule.onMain { simulator.stepOtherCost(-1) }
        assertEquals(1_950, awaitItem().otherCost)
    }

    @Test
    fun `입력을 바꾸면 → 비교 결과가 그 값으로 다시 계산된다`() = runBlocking<Unit> {
        app.completeFirstRun()
        seedRatio851()
        val simulator = simulator()
        simulator.awaitRatio(0.851)

        rule.onMain { simulator.setCarInput(100f) }

        // 연간 비용 = 100 × 12 + 600 = 1,800
        val state = await(simulator.state) { it.carInput == 100 }
        assertEquals(1_800.0, state.comparison.totalCost, 1e-9)
    }

    @Test
    fun `시연 값 → 맨 위는 줄어드는 세금, 한 해 차량 비용은 150 × 12 = 1,800만 원`() = runBlocking<Unit> {
        app.completeFirstRun()
        seedRatio851()

        val state = simulator().awaitRatio(0.851)

        assertEquals(SimulatorHeadline.SAVED, state.headline)
        assertEquals(1_800.0, state.yearlyCarCost, 1e-9)
    }

    @Test
    fun `구입 6,000만 원 → 한 해 차량 비용은 5년 상각한 1,200만 원`() = inputTest { simulator ->
        rule.onMain { simulator.selectOwnership(Ownership.BUY) }

        assertEquals(1_200.0, awaitItem().yearlyCarCost, 1e-9)
    }

    @Test
    fun `관련비용이 1,500만 원 이하(리스 30·그 밖 0) → 맨 위는 한도 이하라 기록 없이도 인정된다는 안내`() = inputTest { simulator ->
        rule.onMain { simulator.setCarInput(30f) }
        awaitItem()

        rule.onMain { simulator.setOtherCost(0f) }

        val state = awaitItem()
        assertEquals(360.0, state.comparison.totalCost, 1e-9)
        assertEquals(SimulatorHeadline.UNDER_LIMIT, state.headline)
    }

    @Test
    fun `업무사용비율이 10퍼센트 → 맨 위는 기록 없이 받는 한도가 더 유리하다는 안내`() = runBlocking<Unit> {
        app.completeFirstRun()
        app.seedTrips(
            trip(startKm = 0.0, endKm = 100.0),
            trip(startKm = 100.0, endKm = 1_000.0, category = Category.PERSONAL),
        )

        val state = simulator().awaitRatio(0.1)

        assertEquals(SimulatorHeadline.NO_LOG_BETTER, state.headline)
    }
}
