package kr.chagok.scenario

import kotlinx.coroutines.runBlocking
import kr.chagok.domain.model.Category
import kr.chagok.domain.model.ClassifyReason
import kr.chagok.domain.model.VehicleUsage
import kr.chagok.recorder.TripDictation
import kr.chagok.testing.ScriptedVoiceInput
import kr.chagok.testing.TestApp
import kr.chagok.testing.TripFixtures
import kr.chagok.testing.dictate
import kr.chagok.testing.setUpLockedAdmin
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * 마이크로 기록 고치기: 정차 중 방금 운행 카드·운행 상세의 마이크에 "개인", "박정훈이 탔어"라고 말하면
 * 버튼을 누른 것과 똑같이 분류·사람이 바뀐다. 그 밖의 말은 방문 목적으로 남는다.
 */
@RunWith(RobolectricTestRunner::class)
class VoiceEditScenarioTest {
    private lateinit var app: TestApp

    @Before
    fun setUp() = runBlocking<Unit> {
        app = TestApp()
        app.startRecording()
    }

    @After
    fun tearDown() = app.close()

    @Test
    fun `개인이라고 말하면 개인 운행이 되고 장소는 남지 않는다`() = runBlocking<Unit> {
        app.completeFirstRun()
        val trip = app.driveTrip(km = 8.8, destination = TripFixtures.CLIENT)
        app.voiceInput = ScriptedVoiceInput("개인 용무였어")

        val result = app.dictate(trip.id)

        val changed = app.trips.find(trip.id)!!
        assertEquals(TripDictation.Result.DONE, result)
        assertEquals(Category.PERSONAL, changed.category)
        assertEquals(ClassifyReason.USER_EDIT, changed.reason)
        assertNull(changed.destination)
        assertEquals("", changed.note)
    }

    @Test
    fun `기록 안 된 주행에 출퇴근이라고 말하면 바꾸지 않고 알린다`() = runBlocking<Unit> {
        app.completeFirstRun()
        app.driveTrip(km = 8.8, destination = TripFixtures.CLIENT)
        app.driveWhileAppOff(km = 5.0)
        app.driveTrip(km = 3.0, destination = TripFixtures.OFFICE)
        val gap = app.trips.trips.value.single { it.isGap }
        app.voiceInput = ScriptedVoiceInput("출퇴근")

        val result = app.dictate(gap.id)

        assertEquals(TripDictation.Result.NOT_ALLOWED, result)
        assertEquals(gap.category, app.trips.find(gap.id)!!.category)
    }

    @Test
    fun `공용차 미지정 운행에 박정훈이 탔어라고 말하면 박정훈 운행이 된다`() = runBlocking<Unit> {
        app.completeFirstRun(usage = VehicleUsage.SHARED, people = listOf("박정훈", "김지은"))
        val parkJunghoon = app.people.people.value.first { it.name == "박정훈" }
        val trip = app.driveTrip(km = 8.8, destination = TripFixtures.CLIENT)
        app.voiceInput = ScriptedVoiceInput("박정훈이 탔어")

        val result = app.dictate(trip.id)

        assertEquals(TripDictation.Result.DONE, result)
        assertEquals(parkJunghoon.id, app.trips.find(trip.id)!!.personId)
    }

    @Test
    fun `이미 사람이 정해진 운행은 관리자가 잠겨 있으면 말로 바꿀 수 없다`() = runBlocking<Unit> {
        app.completeFirstRun(usage = VehicleUsage.SHARED, people = listOf("박정훈", "김지은"))
        val parkJunghoon = app.people.people.value.first { it.name == "박정훈" }
        val trip = app.driveTrip(km = 8.8, destination = TripFixtures.CLIENT)
        app.trips.assignPerson(trip.id, parkJunghoon.id)
        app.setUpLockedAdmin()
        app.voiceInput = ScriptedVoiceInput("김지은 운전했어")

        val result = app.dictate(trip.id)

        assertEquals(TripDictation.Result.PERSON_LOCKED, result)
        assertEquals(parkJunghoon.id, app.trips.find(trip.id)!!.personId)
        assertEquals("", app.trips.find(trip.id)!!.note)
    }

    @Test
    fun `등록된 사람이 아니면 사람을 바꾸지 않고 방문 목적으로 남긴다`() = runBlocking<Unit> {
        app.completeFirstRun(usage = VehicleUsage.SHARED, people = listOf("박정훈"))
        val trip = app.driveTrip(km = 8.8, destination = TripFixtures.CLIENT)
        app.voiceInput = ScriptedVoiceInput("거래처 사장님이 탔어")

        app.dictate(trip.id)

        val saved = app.trips.find(trip.id)!!
        assertNull(saved.personId)
        assertEquals("거래처 사장님이 탔어", saved.note)
    }
}
