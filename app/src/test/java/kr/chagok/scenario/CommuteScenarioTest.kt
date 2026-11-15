package kr.chagok.scenario

import kotlinx.coroutines.runBlocking
import kr.chagok.domain.model.Category
import kr.chagok.domain.model.ClassifyReason
import kr.chagok.domain.model.TripPlace
import kr.chagok.testing.TestApp
import kr.chagok.testing.TestApp.Companion.await
import kr.chagok.testing.TripFixtures
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * 출퇴근: 첫 실행에서 집·회사를 정하면 업무 운행 중 집 ↔ 회사만 ⑧출·퇴근으로 나뉜다.
 * 근거는 그대로 "기본값 업무"이고 화면이 "· 집↔회사"를 덧붙인다. 집·회사를 안 정하면 전부 ⑨일반 업무.
 */
@RunWith(RobolectricTestRunner::class)
class CommuteScenarioTest {
    private lateinit var app: TestApp

    @Before
    fun setUp() {
        app = TestApp()
        app.startRecording()
    }

    @After
    fun tearDown() = app.close()

    @Test
    fun `집·회사를 정한 차로 집에서 회사에 가면 출퇴근으로 기록되고 음성도 출퇴근이라고 알린다`() = runBlocking<Unit> {
        app.completeFirstRun(home = TripFixtures.HOME, office = TripFixtures.OFFICE)
        app.navi.location = TripFixtures.HOME

        val trip = app.driveTrip(km = 12.0, destination = TripFixtures.OFFICE)

        assertEquals(Category.COMMUTE, trip.category)
        assertEquals(ClassifyReason.DEFAULT_BUSINESS, trip.reason)
        assertEquals(12.0, trip.formDistances().commuteKm, 1e-9)
        assertEquals(0.0, trip.formDistances().businessKm, 0.0)
        // 도착 안내만 본다. driveTrip 은 목적지와 주차를 곧바로 이어 보내서 "출퇴근으로 기록됩니다"가 주차보다
        // 늦게 처리될 수 있다. 그 안내는 GleoDriveFlowScenarioTest 에서 기다려서 본다
        assertEquals("회사 도착, 출퇴근으로 기록했어요.", app.voiceOutput.spoken.last())
    }

    @Test
    fun `퇴근 방향(회사 → 집)도 출퇴근이고, 회사에서 고객사로 가면 일반 업무`() = runBlocking<Unit> {
        app.completeFirstRun(home = TripFixtures.HOME, office = TripFixtures.OFFICE)

        app.navi.location = TripFixtures.OFFICE
        val toClient = app.driveTrip(km = 8.8, destination = TripFixtures.CLIENT)
        app.navi.location = TripFixtures.OFFICE
        val goingHome = app.driveTrip(km = 12.0, destination = TripFixtures.HOME)

        assertEquals(Category.BUSINESS, toClient.category)
        assertEquals(Category.COMMUTE, goingHome.category)
    }

    @Test
    fun `내비 없이 집 근처 주소에서 출발해 회사에 가도 300m 안이면 집으로 보아 출퇴근`() = runBlocking<Unit> {
        app.completeFirstRun(home = TripFixtures.HOME, office = TripFixtures.OFFICE)
        app.navi.location = TripPlace(address = "성남시 분당구 집 앞 골목", latitude = 37.3665, longitude = 127.1082)

        val trip = app.driveTrip(km = 12.0, destination = TripFixtures.OFFICE)

        assertEquals(Category.COMMUTE, trip.category)
    }

    @Test
    fun `집·회사를 안 정한 차는 집에서 회사에 가도 일반 업무`() = runBlocking<Unit> {
        app.completeFirstRun(home = null, office = null)
        app.navi.location = TripFixtures.HOME

        val trip = app.driveTrip(km = 12.0, destination = TripFixtures.OFFICE)

        assertEquals(Category.BUSINESS, trip.category)
        assertEquals(ClassifyReason.DEFAULT_BUSINESS, trip.reason)
        assertEquals(12.0, trip.formDistances().businessKm, 1e-9)
    }

    @Test
    fun `집만 정하고 회사를 안 정하면 집·회사 구분을 쓰지 않는다`() = runBlocking<Unit> {
        app.completeFirstRun(home = TripFixtures.HOME, office = null)
        app.navi.location = TripFixtures.HOME

        val trip = app.driveTrip(km = 12.0, destination = TripFixtures.OFFICE)

        assertNull(await(app.settings.settings) { it.isFirstRunDone }.home)
        assertEquals(Category.BUSINESS, trip.category)
    }

    @Test
    fun `출근길이라도 출발 전에 개인을 누르면 개인, 근거는 출발 전 선택`() = runBlocking<Unit> {
        app.completeFirstRun(home = TripFixtures.HOME, office = TripFixtures.OFFICE)
        app.navi.location = TripFixtures.HOME
        app.nextDeparture.pickCategory(Category.PERSONAL)
        await(app.nextDeparture.next) { it?.category == Category.PERSONAL }

        val trip = app.driveTrip(km = 12.0, destination = TripFixtures.OFFICE)

        assertEquals(Category.PERSONAL, trip.category)
        assertEquals(ClassifyReason.PRE_PICK, trip.reason)
    }
}
