package kr.chagok.scenario

import kotlinx.coroutines.runBlocking
import kr.chagok.domain.TripRules
import kr.chagok.domain.model.Category
import kr.chagok.domain.model.ClassifyReason
import kr.chagok.testing.TestApp
import kr.chagok.testing.TestApp.Companion.await
import kr.chagok.testing.TripFixtures
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** 아무것도 누르지 않고 처음 가는 고객사에 다녀오면 업무로 기록되고 음성으로 알린다 */
@RunWith(RobolectricTestRunner::class)
class FirstTripScenarioTest {
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
    fun `내비로 고객사에 다녀오면 계기판 거리 그대로 업무 운행이 저장되고 도착 음성이 나간다`() = runBlocking<Unit> {
        val trip = app.driveTrip(km = 8.8, destination = TripFixtures.CLIENT)

        assertEquals(1_000.0, trip.startKm, 0.0)
        assertEquals(1_008.8, trip.endKm, 1e-9)
        assertEquals(Category.BUSINESS, trip.category)
        assertEquals(ClassifyReason.DEFAULT_BUSINESS, trip.reason)
        assertEquals(TripFixtures.CLIENT, trip.destination)
        assertEquals("혼자 쓰는 차는 늘 첫 사람", app.people.people.value.first().id, trip.personId)
        val spoken = await(app.voiceOutput.spokenFlow) { it.isNotEmpty() } // 음성은 저장 뒤 기록기 스레드에서 나간다
        assertEquals(listOf("○○전자 도착, 업무로 기록했어요."), spoken)
        assertEquals(trip.id, app.recorder.recentTripId.value)
    }

    @Test
    fun `출발하면 주행 중 화면이 되고 이번 운행은 기본값 업무로 잡힌다 → 주차하면 홈으로 돌아온다`() = runBlocking<Unit> {
        assertFalse(app.recorder.isDriving.value)

        app.startDriving()

        assertTrue(app.recorder.isDriving.value)
        val departure = app.recorder.currentDeparture.value!!
        assertEquals(Category.BUSINESS, departure.category)
        assertFalse(departure.isChanged)

        app.vehicle.drive(3.0)
        app.park()

        await(app.recorder.isDriving) { !it }
    }

    @Test
    fun `출발 자리와 도착지가 운행에 남고 처음 가는 곳은 처음 간 곳으로 보인다`() = runBlocking<Unit> {
        app.navi.location = TripFixtures.OFFICE

        val first = app.driveTrip(km = 8.8, destination = TripFixtures.CLIENT)
        app.navi.location = TripFixtures.CLIENT
        app.driveTrip(km = 8.8, destination = TripFixtures.OFFICE)
        app.navi.location = TripFixtures.OFFICE
        val again = app.driveTrip(km = 8.8, destination = TripFixtures.CLIENT)

        val all = app.trips.trips.value
        assertEquals(TripFixtures.OFFICE, first.origin)
        assertTrue(TripRules.isFirstVisit(first, all))
        assertFalse(TripRules.isFirstVisit(again, all))
    }

    @Test
    fun `방금 운행 카드는 닫았다가 다시 보기로 다시 열 수 있고 방문 목적을 남길 수 있다`() = runBlocking<Unit> {
        val trip = app.driveTrip(km = 8.8, destination = TripFixtures.CLIENT)

        app.recorder.clearRecentTrip()
        assertNull(app.recorder.recentTripId.value)
        app.recorder.showRecentTrip(trip.id)
        assertEquals(trip.id, app.recorder.recentTripId.value)

        app.trips.setNote(trip.id, "신제품 미팅")
        assertEquals("신제품 미팅", app.trips.find(trip.id)!!.note)
    }

    @Test
    fun `다음 운행을 하면 방금 운행은 새 운행으로 바뀌고 도착 음성도 운행마다 나간다`() = runBlocking<Unit> {
        app.driveTrip(km = 8.8, destination = TripFixtures.CLIENT)
        val second = app.driveTrip(km = 8.8, destination = TripFixtures.OFFICE)

        assertEquals(second.id, app.recorder.recentTripId.value)
        assertEquals(
            listOf("○○전자 도착, 업무로 기록했어요.", "회사 도착, 업무로 기록했어요."),
            app.voiceOutput.spoken,
        )
    }
}
