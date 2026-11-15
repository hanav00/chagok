package kr.chagok.scenario

import kotlinx.coroutines.runBlocking
import kr.chagok.domain.model.Category
import kr.chagok.testing.ScriptedVoiceInput
import kr.chagok.testing.TestApp
import kr.chagok.testing.TestApp.Companion.await
import kr.chagok.testing.TripFixtures
import kr.chagok.testing.dictate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * 다시 가는 거래처: 방문 목적을 남긴 곳을 내비로 다시 찍으면 차가 지난 메모를 한 번 읽어 준다.
 * 동승석에 사람이 있으면 메모는 빼고, 메모가 없는 곳·개인 운행은 말하지 않는다.
 */
@RunWith(RobolectricTestRunner::class)
class VisitBriefingScenarioTest {
    private lateinit var app: TestApp

    @Before
    fun setUp() = runBlocking<Unit> {
        app = TestApp()
        app.startRecording()
        app.completeFirstRun()
    }

    @After
    fun tearDown() = app.close()

    @Test
    fun `메모를 남긴 거래처를 3주 뒤 다시 찍으면 출발 전에 지난 메모를 읽고, 도착 때까지 다시 말하지 않는다`() =
        runBlocking<Unit> {
            visitClientWithNote("신제품 견적 요청")
            app.passDays(21)

            app.navi.destination.value = TripFixtures.CLIENT
            app.startDriving()
            app.vehicle.drive(8.8)
            app.park()

            assertEquals(
                listOf("○○전자, 3주 만이에요. 지난번 메모, 신제품 견적 요청.", "○○전자 도착, 업무로 기록했어요."),
                app.voiceOutput.spoken.drop(1),
            )
        }

    @Test
    fun `동승석에 사람이 있으면 메모는 읽지 않는다`() = runBlocking<Unit> {
        visitClientWithNote("신제품 견적 요청")
        app.passDays(21)
        app.vehicle.passengerCount.value = 1

        app.navi.destination.value = TripFixtures.CLIENT

        val spoken = await(app.voiceOutput.spokenFlow) { it.size == 2 }
        assertEquals("○○전자, 3주 만이에요.", spoken.last())
    }

    @Test
    fun `동승석 값을 아직 못 받았으면 혼자인지 몰라 메모를 읽지 않는다`() = runBlocking<Unit> {
        visitClientWithNote("신제품 견적 요청")
        app.vehicle.passengerCount.value = null

        app.navi.destination.value = TripFixtures.CLIENT

        val spoken = await(app.voiceOutput.spokenFlow) { it.size == 2 }
        assertEquals("○○전자, 오늘 또 가요.", spoken.last())
    }

    @Test
    fun `메모 없이 다녀온 곳은 다시 가도 도착 안내만 한다`() = runBlocking<Unit> {
        app.driveTrip(km = 8.8, destination = TripFixtures.CLIENT)
        app.driveTrip(km = 8.8, destination = TripFixtures.CLIENT)

        assertEquals(List(2) { "○○전자 도착, 업무로 기록했어요." }, app.voiceOutput.spoken)
    }

    @Test
    fun `다음 출발을 개인으로 골라 두면 메모를 남긴 곳이라도 말하지 않는다`() = runBlocking<Unit> {
        visitClientWithNote("신제품 견적 요청")
        app.nextDeparture.pickCategory(Category.PERSONAL)
        await(app.recorder.currentDeparture) { it?.category == Category.PERSONAL }

        app.navi.destination.value = TripFixtures.CLIENT
        app.startDriving()
        app.vehicle.drive(3.0)
        app.park()

        assertEquals(listOf("3.0킬로미터 운행, 개인으로 기록했어요."), app.voiceOutput.spoken.drop(1))
    }

    // 거래처에 다녀와 방문 목적을 말로 남긴다. 도착 안내 한 문장이 먼저 쌓인다
    private suspend fun visitClientWithNote(note: String) {
        val trip = app.driveTrip(km = 8.8, destination = TripFixtures.CLIENT)
        app.voiceInput = ScriptedVoiceInput(note)
        app.dictate(trip.id)
        await(app.voiceOutput.spokenFlow) { it.size == 1 }
    }
}
