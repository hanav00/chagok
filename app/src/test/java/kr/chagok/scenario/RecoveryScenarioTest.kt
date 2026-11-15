package kr.chagok.scenario

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kr.chagok.car.Gear
import kr.chagok.domain.model.ActiveTrip
import kr.chagok.domain.model.Category
import kr.chagok.domain.model.ClassifyReason
import kr.chagok.testing.TestApp
import kr.chagok.testing.TestApp.Companion.await
import kr.chagok.testing.TestApp.Companion.awaitValue
import kr.chagok.testing.TripFixtures
import kr.chagok.testing.setUpUnlockedAdmin
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Duration
import java.time.LocalDate

/**
 * 꺼졌다 켜짐 복구: 달리는 동안 앱(또는 차)이 꺼져도 꺼지는 순간에 따로 저장하지 않는다.
 * 계기판이 바뀔 때마다 디스크에 남긴 진행 중 운행으로, 다시 켜졌을 때 주차 중이면 마지막 저장값으로 마감하고
 * 아직 달리는 중이면 같은 운행을 잇는다. 꺼진 동안 더 달린 거리는 다음 출발 때 기록 안 된 주행이 된다.
 */
@RunWith(RobolectricTestRunner::class)
class RecoveryScenarioTest {
    private lateinit var app: TestApp
    private lateinit var savedBeforeCrash: ActiveTrip

    @Before
    fun setUp() = runBlocking<Unit> {
        app = TestApp()
        app.startRecording()
        app.completeFirstRun()
        app.navi.location = TripFixtures.OFFICE
        app.startDriving()
        app.navi.destination.value = TripFixtures.CLIENT
        app.clock.advance(Duration.ofMinutes(10))
        app.vehicle.drive(3.0)
        savedBeforeCrash = awaitValue({ app.trips.activeTrip() }) { it?.lastKm == 1_003.0 && it.destination != null }!!
    }

    @After
    fun tearDown() = app.close()

    @Test
    fun `달리다 꺼진 뒤 주차 상태로 켜지면 마지막 저장값으로 운행을 마감하고 말하지 않는다`() = runBlocking<Unit> {
        app = app.restart(odometerKm = 1_003.0, gear = Gear.PARK)
        app.startRecording()

        val closed = await(app.trips.trips) { it.isNotEmpty() }.single()
        assertEquals(1_000.0, closed.startKm, 0.0)
        assertEquals(savedBeforeCrash.lastKm, closed.endKm, 0.0)
        assertEquals(savedBeforeCrash.lastAt, closed.endedAt)
        assertEquals(TripFixtures.OFFICE, closed.origin)
        assertEquals(TripFixtures.CLIENT, closed.destination)
        assertEquals(Category.BUSINESS, closed.category)
        assertEquals(ClassifyReason.DEFAULT_BUSINESS, closed.reason)
        assertNull(awaitValue({ app.trips.activeTrip() }) { it == null })
        assertTrue(app.voiceOutput.spoken.isEmpty())
        assertNull(app.recorder.recentTripId.value)
    }

    @Test
    fun `꺼진 동안 더 달린 거리는 다음 출발 때 기록 안 된 주행으로 잡혀 계기판이 끊기지 않는다`() = runBlocking<Unit> {
        app = app.restart(odometerKm = 1_005.0, gear = Gear.PARK)
        app.startRecording()
        await(app.trips.trips) { it.size == 1 }

        val next = app.driveTrip(km = 4.0, destination = TripFixtures.OFFICE)

        val all = app.trips.trips.value
        assertEquals(listOf(false, true, false), all.map { it.isGap })
        assertEquals(1_003.0, all[1].startKm, 0.0)
        assertEquals(1_005.0, all[1].endKm, 0.0)
        assertEquals(1_005.0, next.startKm, 0.0)
        assertEquals(1_009.0, next.endKm, 0.0)
    }

    @Test
    fun `달리다 꺼진 뒤 아직 달리는 중(D)으로 켜지면 같은 운행을 이어 달리고 주차하면 한 운행으로 남는다`() = runBlocking<Unit> {
        app = app.restart(odometerKm = 1_004.0, gear = Gear.DRIVE)
        app.startRecording()

        assertTrue(await(app.recorder.isDriving) { it })
        app.vehicle.drive(2.0)
        awaitValue({ app.trips.activeTrip() }) { it?.lastKm == 1_006.0 }
        val trip = app.park(countBefore = 0)

        assertEquals(1, app.trips.trips.value.size)
        assertEquals(1_000.0, trip.startKm, 0.0)
        assertEquals(1_006.0, trip.endKm, 0.0)
        assertEquals(savedBeforeCrash.startedAt, trip.startedAt)
        assertEquals(TripFixtures.CLIENT, trip.destination)
        assertEquals(listOf("○○전자 도착, 업무로 기록했어요."), app.voiceOutput.spoken)
    }

    @Test
    fun `주차 저장을 놓친 운행이 오래됐으면 달리는 중(D)으로 켜져도 잇지 않고 마감한 뒤 이번 운행을 새로 연다`() = runBlocking<Unit> {
        app.clock.advance(Duration.ofHours(23)) // 어제 운행, 오늘 아침 시동
        app = app.restart(odometerKm = 1_003.0, gear = Gear.DRIVE)
        app.startRecording()

        val closed = await(app.trips.trips) { it.isNotEmpty() }.single()
        assertEquals(savedBeforeCrash.lastKm, closed.endKm, 0.0)
        assertEquals(savedBeforeCrash.lastAt, closed.endedAt)
        val reopened =
            awaitValue({ app.trips.activeTrip() }) { it != null && it.startedAt != savedBeforeCrash.startedAt }!!
        assertEquals(1_003.0, reopened.startKm, 0.0)
        assertEquals(LocalDate.now(app.clock), reopened.date)
        assertTrue(app.voiceOutput.spoken.isEmpty())
    }

    @Test
    fun `출발 전 개인으로 달리다 꺼져도 다시 켜면 개인 운행으로 마감한다`() = runBlocking<Unit> {
        app.park()
        app.nextDeparture.pickCategory(Category.PERSONAL)
        await(app.nextDeparture.next) { it?.category == Category.PERSONAL }
        app.startDriving()
        app.vehicle.drive(2.0)
        awaitValue({ app.trips.activeTrip() }) { it?.lastKm == 1_005.0 }

        app = app.restart(gear = Gear.PARK)
        app.startRecording()

        val closed = await(app.trips.trips) { it.size == 2 }.last()
        assertEquals(Category.PERSONAL, closed.category)
        assertEquals(ClassifyReason.PRE_PICK, closed.reason)
        assertNull(closed.destination)
        assertEquals(2.0, closed.km, 1e-9)
    }

    @Test
    fun `다시 켜도 설정·기록은 남고 관리자 모드는 잠겨서 켜진다`() = runBlocking<Unit> {
        app.park()
        app.settings.setTripVoiceOn(false)
        app.setUpUnlockedAdmin()

        app = app.restart()
        app.startRecording()

        assertEquals(1, await(app.trips.trips) { it.isNotEmpty() }.size)
        assertFalse(app.settings.settings.first().isTripVoiceOn)
        val admin = await(app.admin.state) { it.hasPin }
        assertFalse(admin.isUnlocked)
    }
}
