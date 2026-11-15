package kr.chagok.scenario

import kotlinx.coroutines.runBlocking
import kr.chagok.domain.model.Person
import kr.chagok.domain.model.VehicleUsage
import kr.chagok.gleo.GleoProtocol
import kr.chagok.gleo.GleoReply
import kr.chagok.testing.TestApp
import kr.chagok.testing.TestApp.Companion.await
import kr.chagok.testing.TripFixtures
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Gleo 음성 명령 (공용차, 3명): "박정훈이 운전해"로 서식 ④사용자를 정한다.
 * 운전자 권한만 쓴다: 미지정인 운행만 채우고, 이미 정해진 사람은 바꾸지 않는다 (관리자 일).
 */
@RunWith(RobolectricTestRunner::class)
class GleoUserScenarioTest {
    private lateinit var app: TestApp
    private lateinit var parkJunghoon: Person
    private lateinit var kimJieun: Person
    private val gleo get() = app.container.gleo

    @Before
    fun setUp() = runBlocking<Unit> {
        app = TestApp()
        app.startRecording()
        app.completeFirstRun(usage = VehicleUsage.SHARED, people = listOf("박정훈", "김지은", "김정훈"))
        val people = app.people.people.value
        parkJunghoon = people[0]
        kimJieun = people[1]
    }

    @After
    fun tearDown() = app.close()

    @Test
    fun `출발 전에 '김지은 님이 타'라고 하면 누가 타나요가 김지은이 되고, 그 운행에 기록된다`() = runBlocking<Unit> {
        val reply = gleo.handle(GleoProtocol.SET_TRIP_USER, JSONObject("""{"name":"김지은 님"}"""))
        await(app.nextDeparture.next) { it?.personId == kimJieun.id }
        val trip = app.driveTrip(km = 8.8, destination = TripFixtures.CLIENT)

        assertEquals(GleoReply.Status.SUCCESS, reply.status)
        assertEquals(kimJieun.id, trip.personId)
    }

    @Test
    fun `안 고르고 출발했어도 주행 중에 말하면 이번 운행 사람이 된다`() = runBlocking<Unit> {
        app.startDriving()

        gleo.setUser("박정훈")
        app.vehicle.drive(5.0)
        val trip = app.park()

        assertEquals(parkJunghoon.id, trip.personId)
    }

    @Test
    fun `주차한 뒤 말하면 미지정이던 방금 운행이 채워진다`() = runBlocking<Unit> {
        val trip = app.driveTrip(km = 8.8, destination = TripFixtures.CLIENT)

        gleo.setUser("박정훈")

        assertEquals(parkJunghoon.id, app.trips.find(trip.id)!!.personId)
    }

    @Test
    fun `이미 사람이 정해진 운행 중에 다른 사람을 말하면 관리자만 바꿀 수 있다고 답한다`() = runBlocking<Unit> {
        app.nextDeparture.pickPerson(kimJieun.id)
        await(app.nextDeparture.next) { it?.personId == kimJieun.id }
        app.startDriving()

        val reply = gleo.setUser("박정훈")
        app.vehicle.drive(5.0)
        val trip = app.park()

        assertEquals(GleoReply.Status.FAIL, reply.status)
        assertEquals(kimJieun.id, trip.personId)
    }

    @Test
    fun `'정훈'처럼 여러 사람이 맞으면 되묻고, 없는 이름이면 등록된 사람이 없다고 답한다`() = runBlocking<Unit> {
        val ambiguous = gleo.setUser("정훈")
        val unknown = gleo.setUser("이영희")
        val partial = gleo.setUser("지은")

        assertEquals(GleoReply.Status.ASKING, ambiguous.status)
        assertEquals(GleoReply.Status.FAIL, unknown.status)
        assertEquals(GleoReply.Status.SUCCESS, partial.status)
    }
}
