package kr.chagok.scenario

import kotlinx.coroutines.runBlocking
import kr.chagok.domain.model.Category
import kr.chagok.domain.model.ClassifyReason
import kr.chagok.gleo.GleoReply
import kr.chagok.gleo.GleoTripTarget
import kr.chagok.testing.TestApp
import kr.chagok.testing.TestApp.Companion.await
import kr.chagok.testing.TripFixtures
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * 시동부터 주차까지 화면을 누르지 않고 말로 하는 흐름. 시동 때 이번 운행이 어떻게 기록될지 듣고,
 * "이번꺼는 개인이야"처럼 분류를 바꾸고, 주차 안내를 들은 뒤 "메모에 고객사 미팅이라고 적어줘"로 마친다.
 */
@RunWith(RobolectricTestRunner::class)
class GleoDriveFlowScenarioTest {
    private lateinit var app: TestApp
    private val gleo get() = app.container.gleo

    @Before
    fun setUp() = runBlocking<Unit> {
        app = TestApp()
        app.startRecording()
        app.completeFirstRun(home = TripFixtures.HOME, office = TripFixtures.OFFICE)
        app.navi.location = TripFixtures.HOME
    }

    @After
    fun tearDown() = app.close()

    @Test
    fun `시동을 걸면 이번 운행이 어떻게 기록될지 말하고, 음성 안내를 끄면 말하지 않는다`() = runBlocking<Unit> {
        app.container.announceIgnition()
        await(app.voiceOutput.spokenFlow) { it.isNotEmpty() }
        app.settings.setTripVoiceOn(false)
        await(app.settings.settings) { !it.isTripVoiceOn }
        app.container.announceIgnition()

        assertEquals(listOf("업무로 기록됩니다."), app.voiceOutput.spoken)
    }

    @Test
    fun `시동 직후 주차 중에 '이번꺼는 개인으로'면 되묻지 않고 곧 떠날 운행이 개인이 된다`() = runBlocking<Unit> {
        val reply = gleo.setCategory(Category.PERSONAL, target = null)
        await(app.nextDeparture.next) { it?.category == Category.PERSONAL }
        val trip = app.driveTrip(km = 3.0)

        assertEquals(GleoReply.Status.SUCCESS, reply.status)
        assertEquals(Category.PERSONAL, trip.category)
    }

    @Test
    fun `한 번 달리고 주차한 뒤 '이번꺼'는 방금 운행인지 다음 운행인지 되묻고, 답한 쪽을 바꾼다`() = runBlocking<Unit> {
        val trip = app.driveTrip(km = 8.8, destination = TripFixtures.CLIENT)

        val asked = gleo.setCategory(Category.PERSONAL, target = null)
        val untouched = app.trips.find(trip.id)!!
        val answered = gleo.setCategory(Category.PERSONAL, target = GleoTripTarget.LAST)

        assertEquals(GleoReply.Status.ASKING, asked.status)
        assertEquals(Category.BUSINESS, untouched.category)
        assertEquals(GleoReply.Status.SUCCESS, answered.status)
        assertEquals(Category.PERSONAL, app.trips.find(trip.id)!!.category)
        assertEquals(Category.BUSINESS, app.nextDeparture.next.value!!.category)
    }

    @Test
    fun `주행 중 '이번꺼는 출퇴근이야'면 출퇴근으로 기록되고 주행 화면도 출퇴근이 된다`() = runBlocking<Unit> {
        app.navi.location = TripFixtures.CLIENT
        app.startDriving()

        gleo.setCategory(Category.COMMUTE, target = null)
        await(app.recorder.currentDeparture) { it?.category == Category.COMMUTE }
        app.vehicle.drive(5.0)
        val trip = app.park()

        assertEquals(Category.COMMUTE, trip.category)
        assertEquals(ClassifyReason.USER_EDIT, trip.reason)
    }

    @Test
    fun `출발 전에 '다음은 출퇴근'이면 그 운행이 출퇴근이고 근거는 출발 전 선택이다`() = runBlocking<Unit> {
        gleo.setCategory(Category.COMMUTE, target = GleoTripTarget.NEXT)
        await(app.nextDeparture.next) { it?.category == Category.COMMUTE }
        app.navi.location = TripFixtures.CLIENT
        val trip = app.driveTrip(km = 4.0, destination = TripFixtures.MART)

        assertEquals(Category.COMMUTE, trip.category)
        assertEquals(ClassifyReason.PRE_PICK, trip.reason)
    }

    @Test
    fun `집에서 출발해 내비로 회사를 찍으면 한 번 '출퇴근으로 기록됩니다'라고 말하고, 고객사는 말하지 않는다`() = runBlocking<Unit> {
        app.startDriving()
        app.navi.destination.value = TripFixtures.CLIENT
        app.navi.destination.value = TripFixtures.OFFICE
        await(app.voiceOutput.spokenFlow) { it.isNotEmpty() }
        app.navi.destination.value = TripFixtures.OFFICE.copy(name = "본사")
        app.vehicle.drive(12.0)
        app.park()

        assertEquals(listOf("출퇴근으로 기록됩니다.", "본사 도착, 출퇴근으로 기록했어요."), app.voiceOutput.spoken)
    }

    @Test
    fun `내비로 회사를 찍으면 주행 화면도 출퇴근이 되고, 고객사로 바꾸면 다시 업무다`() = runBlocking<Unit> {
        app.startDriving()
        await(app.recorder.drivingCategory) { it == Category.BUSINESS }

        app.navi.destination.value = TripFixtures.OFFICE
        await(app.recorder.drivingCategory) { it == Category.COMMUTE }
        app.navi.destination.value = TripFixtures.CLIENT

        await(app.recorder.drivingCategory) { it == Category.BUSINESS }
    }

    @Test
    fun `출발 전에 내비로 회사를 찍어 두었으면 출발할 때 출퇴근이라고 말한다`() = runBlocking<Unit> {
        app.navi.destination.value = TripFixtures.OFFICE

        app.startDriving()

        await(app.voiceOutput.spokenFlow) { it == listOf("출퇴근으로 기록됩니다.") }
    }

    @Test
    fun `주행 중 '업무로 바꿔' → 주차 안내 → '메모에 고객사 미팅'까지 말로 끝난다`() = runBlocking<Unit> {
        app.settings.setDefaultCategory(Category.PERSONAL)
        await(app.nextDeparture.next) { it?.category == Category.PERSONAL }
        app.navi.location = TripFixtures.CLIENT
        app.startDriving()

        gleo.setCategory(Category.BUSINESS, target = null)
        app.navi.destination.value = TripFixtures.MART
        app.vehicle.drive(6.0)
        val trip = app.park()
        val note = gleo.addNote("고객사 미팅")

        assertEquals(Category.BUSINESS, trip.category)
        assertEquals(GleoReply.Status.SUCCESS, note.status)
        assertEquals("고객사 미팅", app.trips.find(trip.id)!!.note)
        assertEquals(listOf("이마트 도착, 업무로 기록했어요."), app.voiceOutput.spoken)
    }
}
