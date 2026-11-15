package kr.chagok.scenario

import kotlinx.coroutines.runBlocking
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

/**
 * 출발 전에 홈 토글 "개인"을 누르고 집에 가면 장소 없이 개인 운행으로 남는다 (근거 "출발 전 선택").
 * 개인 운행은 위치를 읽지 않는다. 도착하면 다음 운행은 다시 업무다.
 */
@RunWith(RobolectricTestRunner::class)
class PersonalBeforeDepartureScenarioTest {
    private lateinit var app: TestApp

    @Before
    fun setUp() = runBlocking<Unit> {
        app = TestApp()
        app.startRecording()
        app.completeFirstRun()
        app.navi.location = TripFixtures.OFFICE
        app.nextDeparture.pickCategory(Category.PERSONAL)
        await(app.nextDeparture.next) { it?.category == Category.PERSONAL }
    }

    @After
    fun tearDown() = app.close()

    @Test
    fun `개인을 누르면 다음 출발이 개인으로 바뀐 것으로 표시된다`() {
        val next = app.nextDeparture.next.value!!
        assertEquals(Category.PERSONAL, next.category)
        assertTrue(next.isChanged)
    }

    @Test
    fun `개인으로 출발하면 주행 중 화면이 개인 운행이고 위치를 한 번도 묻지 않는다`() = runBlocking<Unit> {
        app.startDriving()
        app.navi.destination.value = TripFixtures.HOME

        assertEquals(Category.PERSONAL, app.recorder.currentDeparture.value!!.category)
        app.vehicle.drive(5.0)
        app.park()

        assertEquals(0, app.navi.locationRequests)
    }

    @Test
    fun `집에 도착하면 장소 없이 개인 운행으로 남고 근거는 출발 전 선택, 음성은 거리로 알린다`() = runBlocking<Unit> {
        val trip = app.driveTrip(km = 5.0, destination = TripFixtures.HOME)

        assertEquals(Category.PERSONAL, trip.category)
        assertEquals(ClassifyReason.PRE_PICK, trip.reason)
        assertNull(trip.origin)
        assertNull(trip.destination)
        assertEquals(5.0, trip.km, 1e-9)
        assertEquals(listOf("5.0킬로미터 운행, 개인으로 기록했어요."), app.voiceOutput.spoken)
    }

    @Test
    fun `개인 운행이 끝나면 다음 출발은 다시 기본값 업무이고 그 운행은 업무로 기록된다`() = runBlocking<Unit> {
        app.driveTrip(km = 5.0, destination = TripFixtures.HOME)

        val next = await(app.nextDeparture.next) { it?.category == Category.BUSINESS }!!
        assertFalse(next.isChanged)

        app.navi.location = TripFixtures.HOME
        val work = app.driveTrip(km = 12.0, destination = TripFixtures.CLIENT)
        assertEquals(Category.BUSINESS, work.category)
        assertEquals(ClassifyReason.DEFAULT_BUSINESS, work.reason)
        assertEquals(TripFixtures.HOME, work.origin)
        assertEquals(TripFixtures.CLIENT, work.destination)
    }

    @Test
    fun `개인을 눌렀다가 다시 업무를 누르면 바꾼 것이 아니다`() = runBlocking<Unit> {
        app.nextDeparture.pickCategory(Category.BUSINESS)

        val next = await(app.nextDeparture.next) { it?.category == Category.BUSINESS }!!
        assertFalse(next.isChanged)
        val trip = app.driveTrip(km = 3.0, destination = TripFixtures.CLIENT)
        assertEquals(ClassifyReason.DEFAULT_BUSINESS, trip.reason)
    }
}
