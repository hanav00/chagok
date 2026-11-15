package kr.chagok.scenario

import kotlinx.coroutines.runBlocking
import kr.chagok.domain.model.Category
import kr.chagok.domain.model.ClassifyReason
import kr.chagok.gleo.GleoProtocol
import kr.chagok.gleo.GleoReply
import kr.chagok.gleo.GleoTripTarget
import kr.chagok.testing.TestApp
import kr.chagok.testing.TestApp.Companion.await
import kr.chagok.testing.TripFixtures
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Gleo 음성 명령 (혼자 쓰는 차): 화면을 누르지 않고 말로 분류·방문 목적을 고친다.
 * Gleo 가 문장을 함수로 바꿔 부른 뒤부터 확인한다.
 */
@RunWith(RobolectricTestRunner::class)
class GleoVoiceScenarioTest {
    private lateinit var app: TestApp
    private val gleo get() = app.container.gleo

    @Before
    fun setUp() = runBlocking<Unit> {
        app = TestApp()
        app.startRecording()
        app.completeFirstRun()
        app.navi.location = TripFixtures.OFFICE
    }

    @After
    fun tearDown() = app.close()

    @Test
    fun `주행 중 '이번 운행은 개인이야'라고 하면 개인 운행이 되고, 주차해도 위치를 남기지 않는다`() = runBlocking<Unit> {
        app.startDriving()
        app.navi.destination.value = TripFixtures.MART

        val reply = gleo.setCategory(Category.PERSONAL, target = null)
        await(app.recorder.currentDeparture) { it?.category == Category.PERSONAL }
        val locationReadsBeforeParking = app.navi.locationRequests
        app.vehicle.drive(3.2)
        val trip = app.park()

        assertEquals(GleoReply.Status.SUCCESS, reply.status)
        assertEquals(Category.PERSONAL, trip.category)
        assertEquals(ClassifyReason.USER_EDIT, trip.reason)
        assertNull(trip.origin)
        assertNull(trip.destination)
        assertEquals(locationReadsBeforeParking, app.navi.locationRequests)
    }

    @Test
    fun `기본값 개인인 차에서 주행 중 '업무야'라고 하면 업무로 기록되고 도착지가 남는다`() = runBlocking<Unit> {
        app.settings.setDefaultCategory(Category.PERSONAL)
        await(app.nextDeparture.next) { it?.category == Category.PERSONAL }
        app.startDriving()

        gleo.setCategory(Category.BUSINESS, target = GleoTripTarget.CURRENT)
        app.navi.destination.value = TripFixtures.CLIENT
        app.vehicle.drive(8.8)
        val trip = app.park()

        assertEquals(Category.BUSINESS, trip.category)
        assertEquals(ClassifyReason.USER_EDIT, trip.reason)
        assertEquals(TripFixtures.CLIENT, trip.destination)
    }

    @Test
    fun `주차한 뒤 '방금 운행은 개인이었어'면 장소가 지워지고, 바로 '업무였어'면 되살아난다`() = runBlocking<Unit> {
        val trip = app.driveTrip(km = 8.8, destination = TripFixtures.CLIENT)

        gleo.handle(GleoProtocol.SET_TRIP_CATEGORY, JSONObject("""{"category":"PERSONAL","trip":"LAST"}"""))
        val personal = app.trips.find(trip.id)!!
        gleo.handle(GleoProtocol.SET_TRIP_CATEGORY, JSONObject("""{"category":"BUSINESS","trip":"LAST"}"""))
        val restored = app.trips.find(trip.id)!!

        assertEquals(Category.PERSONAL, personal.category)
        assertEquals(ClassifyReason.USER_EDIT, personal.reason)
        assertNull(personal.destination)
        assertEquals(Category.BUSINESS, restored.category)
        assertEquals(ClassifyReason.DEFAULT_BUSINESS, restored.reason)
        assertEquals(TripFixtures.CLIENT, restored.destination)
    }

    @Test
    fun `출퇴근으로 잡힌 방금 운행에 '업무로 바꿔'라고 하면 일반 업무가 된다`() = runBlocking<Unit> {
        app.completeFirstRun(home = TripFixtures.HOME, office = TripFixtures.OFFICE)
        app.navi.location = TripFixtures.HOME
        val trip = app.driveTrip(km = 12.0, destination = TripFixtures.OFFICE)

        val reply = gleo.setCategory(Category.BUSINESS, target = GleoTripTarget.LAST)

        assertEquals(GleoReply.Status.SUCCESS, reply.status)
        assertEquals(Category.BUSINESS, app.trips.find(trip.id)!!.category)
        assertEquals(ClassifyReason.USER_EDIT, app.trips.find(trip.id)!!.reason)
    }

    @Test
    fun `주차 중 '다음엔 개인으로'면 다음 출발이 개인이 되고, 주행 중에는 주차한 뒤에 하라고 답한다`() = runBlocking<Unit> {
        val parked = gleo.setCategory(Category.PERSONAL, target = GleoTripTarget.NEXT)
        await(app.nextDeparture.next) { it?.category == Category.PERSONAL }
        app.driveTrip(km = 2.0)
        app.startDriving()
        val driving = gleo.setCategory(Category.PERSONAL, target = GleoTripTarget.NEXT)

        assertEquals(GleoReply.Status.SUCCESS, parked.status)
        assertEquals(GleoReply.Status.FAIL, driving.status)
    }

    @Test
    fun `오늘 운행이 없으면 고칠 운행이 없다고 답하고, 어제 운행은 고치지 않는다`() = runBlocking<Unit> {
        val yesterday = app.driveTrip(km = 8.8, destination = TripFixtures.CLIENT)
        app.passDays(1)

        val reply = gleo.setCategory(Category.PERSONAL, target = GleoTripTarget.LAST)

        assertEquals(GleoReply.Status.FAIL, reply.status)
        assertEquals(Category.BUSINESS, app.trips.find(yesterday.id)!!.category)
    }

    @Test
    fun `분류 값을 못 알아들었으면 업무인지 개인인지 되묻는다`() = runBlocking<Unit> {
        val reply = gleo.handle(GleoProtocol.SET_TRIP_CATEGORY, JSONObject("""{"category":"MAYBE"}"""))

        assertEquals(GleoReply.Status.ASKING, reply.status)
    }

    @Test
    fun `주차한 뒤 '방금 간 데는 신제품 미팅이었어'면 방문 목적이 방금 운행 비고에 남는다`() = runBlocking<Unit> {
        val trip = app.driveTrip(km = 8.8, destination = TripFixtures.CLIENT)

        val reply = gleo.handle(GleoProtocol.ADD_TRIP_NOTE, JSONObject("""{"note":" 신제품 미팅 "}"""))

        assertEquals(GleoReply.Status.SUCCESS, reply.status)
        assertEquals("신제품 미팅", app.trips.find(trip.id)!!.note)
    }

    @Test
    fun `방문 목적은 주행 중이거나 개인 운행이면 남기지 않는다`() = runBlocking<Unit> {
        val trip = app.driveTrip(km = 3.0)
        app.trips.classify(trip.id, Category.PERSONAL)
        val onPersonal = gleo.addNote("장보기")
        app.startDriving()
        val whileDriving = gleo.addNote("신제품 미팅")

        assertEquals(GleoReply.Status.FAIL, onPersonal.status)
        assertEquals(GleoReply.Status.FAIL, whileDriving.status)
        assertEquals("", app.trips.find(trip.id)!!.note)
    }

    @Test
    fun `혼자 쓰는 차에서 사람을 말하면 사람은 늘 같다고 답한다`() = runBlocking<Unit> {
        val reply = gleo.setUser("박정훈")

        assertEquals(GleoReply.Status.FAIL, reply.status)
    }
}
