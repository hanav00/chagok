package kr.chagok.scenario

import kotlinx.coroutines.runBlocking
import kr.chagok.domain.model.Category
import kr.chagok.testing.TestApp
import kr.chagok.testing.TestApp.Companion.await
import kr.chagok.testing.TripFixtures
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * 도착 음성 안내와 내비 없이 운행: 내비 목적지가 없으면 그 자리 주소를 남기고 거리로 알린다.
 * 설정에서 음성 안내를 끄면 기록은 그대로 하고 말하지 않는다.
 */
@RunWith(RobolectricTestRunner::class)
class ArrivalVoiceScenarioTest {
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
    fun `내비 없이 다녀오면 거리로 알린다`() = runBlocking<Unit> {
        app.driveTrip(km = 8.8)

        assertEquals(listOf("8.8킬로미터 운행, 업무로 기록했어요."), app.voiceOutput.spoken)
    }

    @Test
    fun `내비 없이 다녀오면 도착지에 그 자리 주소가 남는다`() = runBlocking<Unit> {
        app.navi.location = TripFixtures.STREET

        val trip = app.driveTrip(km = 8.8)

        assertEquals(TripFixtures.STREET, trip.destination)
        assertEquals(Category.BUSINESS, trip.category)
        assertEquals(listOf("8.8킬로미터 운행, 업무로 기록했어요."), app.voiceOutput.spoken)
    }

    @Test
    fun `설정에서 도착 음성 안내를 끄면 기록은 되지만 말하지 않는다`() = runBlocking<Unit> {
        app.settings.setTripVoiceOn(false)
        await(app.settings.settings) { !it.isTripVoiceOn }

        val trip = app.driveTrip(km = 8.8, destination = TripFixtures.CLIENT)

        assertTrue(app.voiceOutput.spoken.isEmpty())
        assertEquals(Category.BUSINESS, trip.category)
        assertEquals(trip.id, app.recorder.recentTripId.value)
    }

    @Test
    fun `음성 안내를 다시 켜면 다음 도착부터 다시 말한다`() = runBlocking<Unit> {
        app.settings.setTripVoiceOn(false)
        app.driveTrip(km = 8.8, destination = TripFixtures.CLIENT)

        app.settings.setTripVoiceOn(true)
        await(app.settings.settings) { it.isTripVoiceOn }
        app.driveTrip(km = 8.8, destination = TripFixtures.OFFICE)

        assertEquals(listOf("회사 도착, 업무로 기록했어요."), app.voiceOutput.spoken)
    }
}
