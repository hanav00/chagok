package kr.chagok.scenario

import kotlinx.coroutines.runBlocking
import kr.chagok.recorder.TripDictation
import kr.chagok.testing.ScriptedVoiceInput
import kr.chagok.testing.TestApp
import kr.chagok.testing.TripFixtures
import kr.chagok.testing.dictate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * 방문 목적: 방금 운행 카드를 닫았거나 지난 운행이어도 정차 중 운행 상세 시트의 마이크 버튼으로 남기거나 다시 말한다.
 * 카드와 시트가 같은 받아쓰기([kr.chagok.recorder.TripDictation])를 쓴다.
 */
@RunWith(RobolectricTestRunner::class)
class VisitNoteScenarioTest {
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
    fun `방금 운행 카드를 닫은 뒤에도 운행 상세에서 말로 방문 목적을 남긴다`() = runBlocking<Unit> {
        val trip = app.driveTrip(km = 8.8, destination = TripFixtures.CLIENT)
        app.recorder.clearRecentTrip()
        app.voiceInput = ScriptedVoiceInput("  신제품 미팅 ")

        val result = app.dictate(trip.id)

        assertEquals(TripDictation.Result.DONE, result)
        assertEquals("신제품 미팅", app.trips.find(trip.id)!!.note)
    }

    @Test
    fun `지난 운행도 다시 말하면 새 말로 바뀌고, 못 알아들으면 앞에 남긴 말을 그대로 둔다`() = runBlocking<Unit> {
        val earlier = app.driveTrip(km = 8.8, destination = TripFixtures.CLIENT)
        app.driveTrip(km = 8.8, destination = TripFixtures.OFFICE)
        app.voiceInput = ScriptedVoiceInput("단가 협상", "납품 검수", null, "")

        app.dictate(earlier.id)
        app.dictate(earlier.id)
        val onSilence = app.dictate(earlier.id)
        val onBlank = app.dictate(earlier.id)

        assertEquals(TripDictation.Result.NOT_HEARD, onSilence)
        assertEquals(TripDictation.Result.NOT_HEARD, onBlank)
        assertEquals("납품 검수", app.trips.find(earlier.id)!!.note)
    }
}
