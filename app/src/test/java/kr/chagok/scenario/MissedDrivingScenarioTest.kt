package kr.chagok.scenario

import kotlinx.coroutines.runBlocking
import kr.chagok.domain.TripRules
import kr.chagok.domain.model.Category
import kr.chagok.domain.model.ClassifyReason
import kr.chagok.domain.model.Trip
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
 * 앱이 꺼진 사이 8km 를 달리면 다음 출발 때 그 거리가 '기록 안 된 주행'(분류 대기)으로 먼저 잡힌다.
 * 사용자가 고르면 근거 "사용자 선택". 계기판은 끊기지 않으므로 기록부 거리도 끊김 없이 이어진다.
 */
@RunWith(RobolectricTestRunner::class)
class MissedDrivingScenarioTest {
    private lateinit var app: TestApp
    private lateinit var before: Trip

    @Before
    fun setUp() = runBlocking<Unit> {
        app = TestApp()
        app.startRecording()
        app.completeFirstRun()
        before = app.driveTrip(km = 8.8, destination = TripFixtures.CLIENT)
        app.driveWhileAppOff(8.0)
    }

    @After
    fun tearDown() = app.close()

    @Test
    fun `출발 전에도 홈은 계기판이 마지막 기록보다 8km 앞서 있다는 것을 안다`() = runBlocking<Unit> {
        val unrecorded = TripRules.unrecordedKm(app.trips.lastEndKm(), app.recorder.odometerKm.value)

        assertEquals(8.0, unrecorded, 1e-9)
    }

    @Test
    fun `다음에 출발하면 기록 안 된 주행 8km 가 분류 대기로 먼저 생기고 이어서 이번 운행이 기록된다`() = runBlocking<Unit> {
        val trip = app.driveTrip(km = 5.0, destination = TripFixtures.OFFICE)

        val all = app.trips.trips.value
        assertEquals(3, all.size)
        val gap = all[1]
        assertTrue(gap.isGap)
        assertTrue(gap.isPending)
        assertNull(gap.reason)
        assertNull(gap.personId)
        assertFalse(gap.isUnassigned)
        assertNull(gap.startedAt)
        assertNull(gap.destination)
        assertEquals(before.endKm, gap.startKm, 0.0)
        assertEquals(8.0, gap.km, 1e-9)
        assertEquals(gap.endKm, trip.startKm, 0.0)
        assertEquals(Category.BUSINESS, trip.category)
        assertEquals(listOf("○○전자 도착, 업무로 기록했어요.", "회사 도착, 업무로 기록했어요."), app.voiceOutput.spoken)
    }

    @Test
    fun `기록 안 된 주행을 고르면 그 분류가 되고 근거는 사용자 선택`() = runBlocking<Unit> {
        app.driveTrip(km = 5.0, destination = TripFixtures.OFFICE)
        val gap = app.trips.trips.value.single { it.isGap }

        app.trips.classify(gap.id, Category.PERSONAL)

        val picked = app.trips.find(gap.id)!!
        assertEquals(Category.PERSONAL, picked.category)
        assertEquals(ClassifyReason.USER_PICK, picked.reason)
        assertFalse(picked.isPending)
        assertNull(app.trips.restorableTripId.value)
    }

    @Test
    fun `빠진 주행이 몇 번 있어도 모든 운행은 앞 운행이 끝난 계기판 값에서 시작해 끊김이 없다`() = runBlocking<Unit> {
        app.driveTrip(km = 5.0, destination = TripFixtures.OFFICE)
        app.driveWhileAppOff(2.5)
        app.driveTrip(km = 7.0)
        app.driveTrip(km = 1.0)
        app.driveWhileAppOff(0.4)
        val last = app.driveTrip(km = 3.0, destination = TripFixtures.CLIENT)

        val all = await(app.trips.trips) { it.lastOrNull()?.id == last.id }
        assertEquals(3, all.count { it.isGap })
        all.zipWithNext().forEach { (previous, next) ->
            assertEquals("${next.id}번 운행의 출발 계기판", previous.endKm, next.startKm, 0.0)
        }
        assertEquals(TestApp.START_ODOMETER_KM, all.first().startKm, 0.0)
        assertEquals(app.vehicle.odometerKm.value!!, all.last().endKm, 0.0)
        assertEquals(app.vehicle.odometerKm.value!! - TestApp.START_ODOMETER_KM, TripRules.totalsOf(all).total, 1e-9)
    }
}
