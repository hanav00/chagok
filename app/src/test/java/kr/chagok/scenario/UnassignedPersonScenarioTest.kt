package kr.chagok.scenario

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kr.chagok.domain.TripRules
import kr.chagok.domain.model.Person
import kr.chagok.domain.model.VehicleUsage
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
 * 사용자 미지정 (공용차, 2명). "누가 타나요?"를 고르지 않고 출발하면 '사용자 미지정'으로 기록되고,
 * 운행이 끝나면 "누가 타나요?"가 비워진다. 미지정 운행은 나중에 사람을 골라 채운다.
 */
@RunWith(RobolectricTestRunner::class)
class UnassignedPersonScenarioTest {
    private lateinit var app: TestApp
    private lateinit var parkJunghoon: Person
    private lateinit var kimJieun: Person

    @Before
    fun setUp() = runBlocking<Unit> {
        app = TestApp()
        app.startRecording()
        app.completeFirstRun(usage = VehicleUsage.SHARED, people = listOf("박정훈", "김지은"))
        val people = app.people.people.value
        parkJunghoon = people[0]
        kimJieun = people[1]
    }

    @After
    fun tearDown() = app.close()

    @Test
    fun `공용차는 처음에 누가 타나요가 비어 있다`() {
        assertNull(app.nextDeparture.next.value!!.personId)
    }

    @Test
    fun `누가 타나요를 안 고르고 출발하면 사용자 미지정으로 기록된다`() = runBlocking<Unit> {
        val trip = app.driveTrip(km = 8.8, destination = TripFixtures.CLIENT)

        assertNull(trip.personId)
        assertTrue(trip.isUnassigned)
    }

    @Test
    fun `김지은을 고르고 출발하면 김지은 운행이 되고, 운행이 끝나면 누가 타나요가 다시 비워진다`() = runBlocking<Unit> {
        app.nextDeparture.pickPerson(kimJieun.id)
        await(app.nextDeparture.next) { it?.personId == kimJieun.id }

        val trip = app.driveTrip(km = 8.8, destination = TripFixtures.CLIENT)

        assertEquals(kimJieun.id, trip.personId)
        assertFalse(trip.isUnassigned)
        await(app.nextDeparture.next) { it?.personId == null }
        val nextTrip = app.driveTrip(km = 3.0, destination = TripFixtures.OFFICE)
        assertTrue(nextTrip.isUnassigned)
        assertEquals(kimJieun.id, TripRules.lastPersonId(app.trips.trips.value))
    }

    @Test
    fun `미지정 운행에 나중에 사람을 고르면 채워지고 관리자 수정 기록은 남지 않는다`() = runBlocking<Unit> {
        val trip = app.driveTrip(km = 8.8, destination = TripFixtures.CLIENT)

        app.trips.assignPerson(trip.id, parkJunghoon.id)

        val assigned = app.trips.find(trip.id)!!
        assertEquals(parkJunghoon.id, assigned.personId)
        assertFalse(assigned.isUnassigned)
        assertEquals(trip.reason, assigned.reason)
        assertTrue(app.trips.adminEdits.first().isEmpty())
    }

    @Test
    fun `기록 안 된 주행은 누가 탔는지 몰라도 미지정으로 치지 않는다`() = runBlocking<Unit> {
        app.driveTrip(km = 8.8, destination = TripFixtures.CLIENT)
        app.driveWhileAppOff(4.0)
        app.driveTrip(km = 3.0, destination = TripFixtures.OFFICE)

        val gap = app.trips.trips.value.single { it.isGap }
        assertNull(gap.personId)
        assertFalse(gap.isUnassigned)
    }
}
