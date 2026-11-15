package kr.chagok.recorder

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kr.chagok.car.Gear
import kr.chagok.domain.model.Category
import kr.chagok.domain.model.TripPlace
import kr.chagok.testing.MutableClock
import kr.chagok.testing.TestApp
import kr.chagok.testing.TestApp.Companion.await
import kr.chagok.testing.TestApp.Companion.awaitValue
import kr.chagok.testing.TripFixtures
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowLog
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * 운행 자동 기록기의 가장자리 경우. 대표 흐름은 scenario 패키지에 있고, 여기는 기어·계기판·내비 신호가
 * 늦거나 겹치거나 애매할 때 기록이 어긋나지 않는지 본다.
 */
@RunWith(RobolectricTestRunner::class)
class TripRecorderTest {
    private lateinit var app: TestApp

    @After
    fun tearDown() {
        if (::app.isInitialized) app.close()
    }

    @Test
    fun `출발 순간에 계기판 값이 아직 없으면 기다렸다가 처음 받은 값을 출발 거리로 쓴다`() = runBlocking<Unit> {
        app = recordingApp(odometerKm = null)

        app.vehicle.shift(Gear.DRIVE)
        await(app.recorder.isDriving) { it }
        app.vehicle.odometerKm.value = 1_000.0
        val opened = awaitValue({ app.trips.activeTrip() }) { it != null }!!
        app.vehicle.drive(5.0)
        val trip = app.park()

        assertEquals(1_000.0, opened.startKm, 0.0)
        assertEquals(1_000.0, trip.startKm, 0.0)
        assertEquals(1_005.0, trip.endKm, 0.0)
    }

    @Test
    fun `주행 중 계기판이 바뀔 때마다 진행 중 운행에 마지막 거리·시각이 저장된다`() = runBlocking<Unit> {
        app = recordingApp()
        app.startDriving()

        app.clock.advance(Duration.ofMinutes(5))
        app.vehicle.drive(1.5)
        val first = awaitValue({ app.trips.activeTrip() }) { it?.lastKm == 1_001.5 }!!
        app.clock.advance(Duration.ofMinutes(5))
        app.vehicle.drive(1.5)
        val second = awaitValue({ app.trips.activeTrip() }) { it?.lastKm == 1_003.0 }!!

        assertEquals(LocalTime.of(9, 5), first.lastAt)
        assertEquals(LocalTime.of(9, 10), second.lastAt)
        assertEquals(1_000.0, second.startKm, 0.0)
    }

    @Test
    fun `기어를 아직 모르는(UNKNOWN) 채 켜지면 복구를 미루고, 기어 D 를 받은 뒤 같은 운행을 잇는다`() = runBlocking<Unit> {
        app = recordingApp()
        app.startDriving()
        app.vehicle.drive(3.0)
        awaitValue({ app.trips.activeTrip() }) { it?.lastKm == 1_003.0 }

        app = app.restart(odometerKm = 1_003.0, gear = Gear.UNKNOWN)
        app.startRecording()
        app.vehicle.drive(1.0)
        assertFalse(app.recorder.isDriving.value)
        app.vehicle.shift(Gear.DRIVE)
        app.vehicle.drive(1.0)
        awaitValue({ app.trips.activeTrip() }) { it?.lastKm == 1_005.0 }
        val trip = app.park(countBefore = 0)

        val all = app.trips.trips.value
        assertEquals(1, all.size)
        assertEquals(1_000.0, trip.startKm, 0.0)
        assertEquals(1_005.0, trip.endKm, 0.0)
    }

    @Test
    fun `주차한 자리에서 계기판이 그대로면 다시 출발해도 기록 안 된 주행이 생기지 않는다`() = runBlocking<Unit> {
        app = recordingApp()

        app.driveTrip(km = 5.0)
        app.driveTrip(km = 5.0)

        assertEquals(0, app.trips.trips.value.count { it.isGap })
    }

    @Test
    fun `0·1km 미만 오차는 빠진 주행으로 치지 않는다`() = runBlocking<Unit> {
        app = recordingApp(odometerKm = 2_040.0)
        driveTo(2_048.3)

        app.vehicle.odometerKm.value = 2_048.35
        driveTo(2_050.0)

        assertEquals(0, app.trips.trips.value.count { it.isGap })
    }

    @Test
    fun `앱이 꺼진 사이 계기판이 한 칸(0·1km)만 늘어도 빠진 주행으로 잡는다`() = runBlocking<Unit> {
        app = recordingApp(odometerKm = 2_040.0)
        driveTo(2_048.3)

        app.vehicle.odometerKm.value = 2_048.4
        driveTo(2_050.0)

        val gap = app.trips.trips.value.single { it.isGap }
        assertEquals(2_048.3, gap.startKm, 0.0)
        assertEquals(2_048.4, gap.endKm, 0.0)
    }

    @Test
    fun `신호 대기 N·후진 R 처럼 P 가 아닌 기어끼리 바뀌어도 운행은 하나로 이어진다`() = runBlocking<Unit> {
        app = recordingApp()
        app.startDriving()

        app.vehicle.drive(2.0)
        app.vehicle.shift(Gear.NEUTRAL)
        app.vehicle.shift(Gear.REVERSE)
        app.vehicle.drive(0.1)
        app.vehicle.shift(Gear.DRIVE)
        app.vehicle.drive(2.9)
        val trip = app.park()

        assertEquals(1, app.trips.trips.value.size)
        assertEquals(5.0, trip.km, 1e-9)
        assertEquals(1, app.voiceOutput.spoken.size)
    }

    @Test
    fun `출발 전에 내비 경로를 정해 두면 출발할 때 목적지가 운행에 붙는다`() = runBlocking<Unit> {
        app = recordingApp()
        app.navi.location = TripFixtures.OFFICE
        app.navi.destination.value = TripFixtures.CLIENT

        app.startDriving()

        val opened = app.trips.activeTrip()!!
        assertEquals(TripFixtures.OFFICE, opened.origin)
        assertEquals(TripFixtures.CLIENT, opened.destination)
    }

    @Test
    fun `주행 중 내비 목적지를 받으면 운행에 붙어 도착 전에 안내가 끝나도 도착지로 남는다`() = runBlocking<Unit> {
        app = recordingApp()
        app.startDriving()

        app.navi.destination.value = TripFixtures.CLIENT
        awaitValue({ app.trips.activeTrip() }) { it?.destination == TripFixtures.CLIENT }
        app.navi.destination.value = null
        app.vehicle.drive(8.8)
        val trip = app.park()

        assertEquals(TripFixtures.CLIENT, trip.destination)
    }

    @Test
    fun `경로 없이 내비가 빈 목적지를 줘도 붙이지 않고 주차한 자리 주소를 도착지로 남긴다`() = runBlocking<Unit> {
        app = recordingApp()
        app.navi.location = TripFixtures.OFFICE
        app.navi.destination.value = TripPlace() // 경로가 없을 때 getDestinationInfo 가 주는 값 (에뮬레이터)

        app.startDriving()
        val opened = app.trips.activeTrip()!!
        app.navi.location = TripFixtures.STREET
        app.vehicle.drive(3.0)
        val trip = app.park()

        assertNull(opened.destination)
        assertEquals(TripFixtures.OFFICE, trip.origin)
        assertEquals(TripFixtures.STREET, trip.destination)
    }

    @Test
    fun `주차 때 위치 응답이 늦으면 출발 자리를 도착지로 쓰지 않고 비워 둔다`() = runBlocking<Unit> {
        app = recordingApp()
        app.navi.location = TripFixtures.OFFICE
        app.startDriving()

        app.navi.location = TripFixtures.STREET
        app.navi.isLate = true
        app.vehicle.drive(8.8)
        val trip = app.park()

        assertEquals(TripFixtures.OFFICE, trip.origin)
        assertNull(trip.destination)
        assertEquals(listOf("8.8킬로미터 운행, 업무로 기록했어요."), app.voiceOutput.spoken)
    }

    @Test
    fun `출발 때 위치 응답이 늦으면 마지막으로 받은 위치(앞 운행을 마친 자리)를 출발지로 쓴다`() = runBlocking<Unit> {
        app = recordingApp()
        app.navi.location = TripFixtures.STREET
        app.driveTrip(km = 3.0)

        app.navi.isLate = true
        app.startDriving()

        assertEquals(TripFixtures.STREET, app.trips.activeTrip()!!.origin)
    }

    @Test
    fun `주행 중 빈 목적지를 받아도 진행 중 운행에 붙이지 않는다`() = runBlocking<Unit> {
        app = recordingApp()
        app.navi.location = TripFixtures.STREET
        app.startDriving()

        app.navi.destination.value = TripPlace()
        app.vehicle.drive(1.0)
        val running = awaitValue({ app.trips.activeTrip() }) { it?.lastKm == 1_001.0 }!!
        val trip = app.park()

        assertNull(running.destination)
        assertEquals(TripFixtures.STREET, trip.destination)
    }

    @Test
    fun `개인 운행은 주행 중 내비 목적지를 받아도 운행에 붙이지 않는다`() = runBlocking<Unit> {
        app = recordingApp()
        app.nextDeparture.pickCategory(Category.PERSONAL)
        await(app.nextDeparture.next) { it?.category == Category.PERSONAL }
        app.startDriving()

        app.navi.destination.value = TripFixtures.MART
        app.vehicle.drive(1.0)
        val running = awaitValue({ app.trips.activeTrip() }) { it?.lastKm == 1_001.0 }!!
        val trip = app.park()

        assertNull(running.destination)
        assertNull(running.origin)
        assertNull(trip.destination)
        assertEquals(0, app.navi.locationRequests)
    }

    @Test
    fun `주행 중에 다음 출발 토글을 바꿔도 이번 운행 분류는 출발 때 값 그대로다`() = runBlocking<Unit> {
        app = recordingApp()
        app.startDriving()

        app.nextDeparture.pickCategory(Category.PERSONAL)
        await(app.nextDeparture.next) { it?.category == Category.PERSONAL }
        app.vehicle.drive(4.0)

        assertEquals(Category.BUSINESS, app.recorder.currentDeparture.value!!.category)
        assertEquals(Category.BUSINESS, app.park().category)
    }

    @Test
    fun `자정을 넘겨 도착해도 운행 날짜는 출발한 날이다`() = runBlocking<Unit> {
        app = recordingApp(clock = MutableClock(LocalDateTime.of(2026, 10, 26, 23, 50)))

        val trip = app.driveTrip(km = 20.0, minutes = 30)

        assertEquals(LocalDate.of(2026, 10, 26), trip.date)
        assertEquals(LocalTime.of(23, 50), trip.startedAt)
        assertEquals(LocalTime.of(0, 20), trip.endedAt)
    }

    @Test
    fun `기록 서비스가 여러 번 떠도 기록기는 한 번만 돌아 운행·음성이 겹치지 않는다`() = runBlocking<Unit> {
        app = recordingApp()
        app.startRecording()
        app.startRecording()

        app.driveTrip(km = 8.8, destination = TripFixtures.CLIENT)

        assertEquals(1, app.trips.trips.value.size)
        assertEquals(1, app.voiceOutput.spoken.size)
    }

    @Test
    fun `주차해서 도착지를 기다리는 동안 고른 다음 출발 분류는 지워지지 않는다`() = runBlocking<Unit> {
        app = recordingApp()
        app.startDriving()
        app.vehicle.drive(3.0)
        app.navi.whileLocating = { app.nextDeparture.pickCategory(Category.PERSONAL) }

        app.park()
        app.navi.whileLocating = {}
        val next = app.driveTrip(km = 1.0)

        assertEquals(Category.PERSONAL, next.category)
    }

    @Test
    fun `내비 없이 주차하면 그 자리 주소를 기다리기 전에 운행부터 기록부에 남긴다`() = runBlocking<Unit> {
        app = recordingApp()
        val located = CompletableDeferred<Unit>()
        app.navi.location = TripFixtures.STREET
        app.startDriving()
        app.vehicle.drive(3.0)
        app.navi.whileLocating = { located.await() }

        app.vehicle.shift(Gear.PARK)

        val saved = await(app.trips.trips) { it.isNotEmpty() }.single()
        assertEquals(1_003.0, saved.endKm, 0.0)
        assertNull("주소는 아직", saved.destination)
        assertNull("진행 중 운행은 이미 비웠다", awaitValue({ app.trips.activeTrip() }) { it == null })
        assertTrue("음성은 도착지가 정해진 뒤", app.voiceOutput.spoken.isEmpty())

        located.complete(Unit)
        val filled = await(app.trips.trips) { it.single().destination != null }.single()
        assertEquals(TripFixtures.STREET, filled.destination)
        assertEquals(1, await(app.voiceOutput.spokenFlow) { it.isNotEmpty() }.size)
    }

    @Test
    fun `주소를 기다리는 동안 꺼져도 운행은 남고 다시 켜도 두 번 마감하지 않는다`() = runBlocking<Unit> {
        app = recordingApp()
        app.startDriving()
        app.vehicle.drive(3.0)
        app.navi.whileLocating = { CompletableDeferred<Unit>().await() } // 끝내 답이 없다

        app.vehicle.shift(Gear.PARK)
        await(app.trips.trips) { it.isNotEmpty() }
        app = app.restart(gear = Gear.PARK)
        app.startRecording()

        val trips = await(app.trips.trips) { it.isNotEmpty() }
        assertEquals(1, trips.size)
        assertEquals(1_003.0, trips.single().endKm, 0.0)
        assertNull(awaitValue({ app.trips.activeTrip() }) { it == null })
    }

    @Test
    fun `나중에 받은 주소가 집이면 회사에서 출발한 운행은 출퇴근이 된다`() = runBlocking<Unit> {
        app = TestApp().apply {
            startRecording()
            completeFirstRun(home = TripFixtures.HOME, office = TripFixtures.OFFICE)
        }
        app.navi.location = TripFixtures.OFFICE
        app.startDriving()
        app.vehicle.drive(5.0)
        app.navi.location = TripFixtures.HOME

        val trip = app.park()

        assertEquals(TripFixtures.HOME, trip.destination)
        assertEquals(Category.COMMUTE, trip.category)
    }

    @Test
    fun `주소를 기다리는 동안 개인으로 바꾸면 위치를 남기지 않고 개인 그대로다`() = runBlocking<Unit> {
        app = recordingApp()
        app.navi.location = TripFixtures.STREET
        app.startDriving()
        app.vehicle.drive(3.0)
        app.navi.whileLocating = {
            val saved = await(app.trips.trips) { it.isNotEmpty() }.single()
            app.trips.classify(saved.id, Category.PERSONAL)
        }

        val trip = app.park()

        assertEquals(Category.PERSONAL, trip.category)
        assertNull(trip.destination)
    }

    @Test
    fun `주소가 끝내 안 와도 기다리는 동안 개인으로 바꿨으면 도착 안내는 개인으로 말한다`() = runBlocking<Unit> {
        app = recordingApp()
        app.startDriving()
        app.vehicle.drive(3.0)
        app.navi.isLate = true
        app.navi.whileLocating = {
            val saved = await(app.trips.trips) { it.isNotEmpty() }.single()
            app.trips.classify(saved.id, Category.PERSONAL)
        }

        app.park()

        assertEquals(listOf("3.0킬로미터 운행, 개인으로 기록했어요."), app.voiceOutput.spoken)
    }

    @Test
    fun `주차 저장이 실패해도 다음 운행이 앞 운행에 이어 붙지 않고, 저장이 되살아나면 앞 운행부터 남긴다`() =
        runBlocking<Unit> {
            app = recordingApp()
            app.startDriving()
            app.vehicle.drive(5.0)
            awaitValue({ app.trips.activeTrip() }) { it?.lastKm == 1_005.0 }
            failTripInserts(true)
            app.vehicle.shift(Gear.PARK)
            awaitRecorderFailures(1)

            // 저장이 막힌 동안 다시 출발해 달린다. 두 이벤트 모두 앞 운행부터 옮기려다 실패한다
            app.vehicle.shift(Gear.DRIVE)
            awaitRecorderFailures(2)
            app.vehicle.drive(3.0)
            awaitRecorderFailures(3)
            failTripInserts(false)
            app.vehicle.drive(2.0)
            await(app.recorder.recentTripId) { it != null }
            awaitValue({ app.trips.activeTrip() }) { it != null && it.startKm > 1_005.0 }
            app.park()

            val all = app.trips.trips.value
            val first = all.first { !it.isGap }
            assertEquals(1_000.0, first.startKm, 0.0)
            assertEquals(1_005.0, first.endKm, 0.0)
            assertEquals(1_010.0, all.maxOf { it.endKm }, 0.0)
            assertEquals(10.0, all.sumOf { it.km }, 0.001)
        }

    // 기록기가 차 이벤트 처리에 실패한 횟수가 [count] 가 될 때까지 기다린다
    private suspend fun awaitRecorderFailures(count: Int) =
        awaitValue({ ShadowLog.getLogsForTag("TripRecorder").count { it.msg.endsWith("처리 실패") } }) { it >= count }

    // 운행을 기록부로 옮기는 INSERT 를 막는다 (빠진 주행도 같은 표라 함께 막힌다)
    private fun failTripInserts(isFailing: Boolean) {
        val sql = app.disk.database.openHelper.writableDatabase
        if (isFailing) {
            sql.execSQL("CREATE TRIGGER fail_trip_insert BEFORE INSERT ON trips BEGIN SELECT RAISE(ABORT, 'test'); END")
        } else {
            sql.execSQL("DROP TRIGGER fail_trip_insert")
        }
    }

    // 차가 주는 값(0.1km 단위) 그대로 계기판을 [endKm] 으로 옮기는 운행 한 번
    private suspend fun driveTo(endKm: Double) {
        val countBefore = app.trips.trips.value.size
        app.startDriving()
        app.vehicle.odometerKm.value = endKm
        app.park(countBefore)
    }

    // 기록기를 켜고 첫 실행(업무 · 혼자)을 마친 앱
    private suspend fun recordingApp(
        odometerKm: Double? = TestApp.START_ODOMETER_KM,
        clock: MutableClock = MutableClock(),
    ): TestApp = TestApp(startOdometerKm = odometerKm, clock = clock).apply {
        startRecording()
        completeFirstRun()
    }
}
