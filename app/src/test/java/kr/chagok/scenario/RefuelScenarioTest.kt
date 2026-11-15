package kr.chagok.scenario

import kotlinx.coroutines.runBlocking
import kr.chagok.domain.model.EnergyKind
import kr.chagok.recorder.RefuelDictation
import kr.chagok.testing.ScriptedVoiceInput
import kr.chagok.testing.TestApp
import kr.chagok.testing.TestApp.Companion.await
import kr.chagok.testing.TestApp.Companion.awaitValue
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * 주유·충전: 잔량이 오르면 차가 알아서 남기고 한 번 말한다. 금액은 홈에서 말로 받거나 "모름"으로 넘긴다.
 * 시동을 끈 채 넣어도 다음 시동에서 처음 받은 잔량으로 알아챈다.
 */
@RunWith(RobolectricTestRunner::class)
class RefuelScenarioTest {
    private lateinit var app: TestApp

    private val refuels get() = app.container.refuels

    @Before
    fun setUp() = runBlocking<Unit> {
        app = TestApp()
        app.startRecording()
        app.completeFirstRun()
    }

    @After
    fun tearDown() = app.close()

    @Test
    fun `시동을 끄고 주유한 뒤 다시 켜면 처음 받은 잔량으로 주유를 남기고 한 번 말한다`() = runBlocking<Unit> {
        app.vehicle.fuelPercent.value = 30
        awaitValue({ refuels.mark(EnergyKind.FUEL) }) { it?.percent == 30 }

        app = app.restart()
        app.vehicle.fuelPercent.value = 85
        app.startRecording()

        val list = await(refuels.refuels) { it.isNotEmpty() }
        assertEquals(EnergyKind.FUEL, list.single().kind)
        assertEquals(30 to 85, list.single().fromPercent to list.single().toPercent)
        assertEquals(TestApp.START_ODOMETER_KM, list.single().odometerKm, 0.0)
        val spoken = await(app.voiceOutput.spokenFlow) { it.isNotEmpty() }
        assertEquals(listOf("주유를 기록했어요. 금액은 화면에서 말해 주세요."), spoken)
    }

    @Test
    fun `충전기를 꽂아 천천히 오르면 한 건으로 남고 끝 잔량만 올라간다`() = runBlocking<Unit> {
        app.vehicle.batteryPercent.value = 40
        awaitValue({ refuels.mark(EnergyKind.CHARGE) }) { it?.percent == 40 }

        app.vehicle.batteryPercent.value = 43
        for (percent in listOf(50, 62, 75)) {
            app.vehicle.batteryPercent.value = percent
            await(refuels.refuels) { it.lastOrNull()?.toPercent == percent }
        }

        val charge = refuels.refuels.value.single()
        assertEquals(40 to 75, charge.fromPercent to charge.toPercent)
        assertEquals(listOf("충전을 기록했어요. 금액은 화면에서 말해 주세요."), app.voiceOutput.spoken)
    }

    @Test
    fun `내리막에서 달리며 오른 배터리(회생 제동)는 충전으로 남기지 않는다`() = runBlocking<Unit> {
        app.vehicle.batteryPercent.value = 50
        awaitValue({ refuels.mark(EnergyKind.CHARGE) }) { it?.percent == 50 }

        // 0.1km 달릴 때마다 1%p씩, 모두 6%p 오른다. 기준점은 계기판이 늘 때 따라 올라간다
        app.startDriving()
        for (percent in 51..56) {
            app.vehicle.batteryPercent.value = percent
            app.vehicle.drive(0.1)
            val km = app.vehicle.odometerKm.value
            awaitValue({ refuels.mark(EnergyKind.CHARGE) }) { it?.odometerKm == km && it?.percent == percent }
        }
        app.park()

        assertTrue(refuels.refuels.value.isEmpty())
    }

    @Test
    fun `주유하고 달린 뒤 또 넣으면 새 기록이 된다`() = runBlocking<Unit> {
        fillUp(from = 30, to = 90)
        app.startDriving()
        app.vehicle.drive(40.0)
        app.vehicle.fuelPercent.value = 70
        awaitValue({ refuels.mark(EnergyKind.FUEL) }) { it?.percent == 70 }
        app.park()

        app.vehicle.fuelPercent.value = 95

        val list = await(refuels.refuels) { it.size == 2 }
        assertEquals(70 to 95, list.last().fromPercent to list.last().toPercent)
        assertEquals(TestApp.START_ODOMETER_KM + 40.0, list.last().odometerKm, 1e-6)
    }

    @Test
    fun `금액을 말하면 남고, 못 알아들으면 그대로 둔다`() = runBlocking<Unit> {
        val refuel = fillUp(from = 30, to = 90)
        app.voiceInput = ScriptedVoiceInput("카드로 했어", "6만 3천 원")

        val first = app.container.refuelDictation.dictate(refuel.id)
        val second = app.container.refuelDictation.dictate(refuel.id)

        assertEquals(RefuelDictation.Result.NotHeard, first)
        assertEquals(RefuelDictation.Result.Done(63_000L), second)
        val saved = await(refuels.refuels) { it.single().amountWon != null }.single()
        assertEquals(63_000L, saved.amountWon)
        assertFalse(saved.needsAmount)
    }

    @Test
    fun `모름으로 넘기면 금액 없이 할 일에서 빠진다`() = runBlocking<Unit> {
        val refuel = fillUp(from = 30, to = 90)

        refuels.skipAmount(refuel.id)

        val saved = await(refuels.refuels) { !it.single().needsAmount }.single()
        assertEquals(null, saved.amountWon)
    }

    @Test
    fun `음성 안내를 끄면 기록은 하고 말하지 않는다`() = runBlocking<Unit> {
        app.settings.setTripVoiceOn(false)
        await(app.settings.settings) { !it.isTripVoiceOn }

        fillUp(from = 30, to = 90)

        assertTrue(app.voiceOutput.spoken.isEmpty())
    }

    // 서 있는 채로 주유: 잔량을 [from] 으로 맞춘 뒤 [to] 로 올리고, 남은 기록을 돌려준다
    private suspend fun fillUp(from: Int, to: Int) = run {
        val countBefore = refuels.refuels.value.size
        app.vehicle.fuelPercent.value = from
        awaitValue({ refuels.mark(EnergyKind.FUEL) }) { it?.percent == from }
        app.vehicle.fuelPercent.value = to
        await(refuels.refuels) { it.size > countBefore }.last()
    }
}
