package kr.chagok.testing

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.withTimeout
import kr.chagok.AppContainer
import kr.chagok.AppEnvironment
import kr.chagok.car.Gear
import kr.chagok.car.VoiceInput
import kr.chagok.domain.FirstRunChoice
import kr.chagok.domain.NewPerson
import kr.chagok.domain.model.Category
import kr.chagok.domain.model.Trip
import kr.chagok.domain.model.TripPlace
import kr.chagok.domain.model.VehicleUsage
import java.io.File
import java.time.Duration

/**
 * 가짜 차로 돌리는 차곡 앱 전체. 실제 앱과 같은 [AppContainer] 를 메모리 DB·임시 DataStore·가짜 SDK 로 만든다.
 * 비동기(Room·DataStore·기록기)는 진짜 스레드에서 돌므로 결과는 [await] 로 조건이 될 때까지 기다린다. 테스트 끝에 [close].
 *
 * @param startOdometerKm 앱이 켜질 때 계기판 값. null 이면 아직 못 받은 것
 * @param startGear 앱이 켜질 때 기어 (꺼졌다 켜짐 복구는 이 값으로 잇거나 마감한다)
 * @param disk 저장소. 앞 앱의 것을 넘기면 꺼졌다 다시 켠 앱이 된다 ([restart])
 */
class TestApp(
    startOdometerKm: Double? = START_ODOMETER_KM,
    startGear: Gear = Gear.PARK,
    val disk: TestDisk = TestDisk(),
    val clock: MutableClock = MutableClock(),
) {
    val vehicle = FakeVehicleSource(odometerKm = startOdometerKm, gear = startGear)
    val navi = FakeNaviSource()
    val voiceOutput = RecordingVoiceOutput()
    var voiceInput: VoiceInput = ScriptedVoiceInput()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val application: Application = ApplicationProvider.getApplicationContext()

    val container = AppContainer(
        application.resources,
        AppEnvironment(
            database = disk.database,
            settingsStore = testDataStore(scope, File(disk.directory, "settings").apply { mkdirs() }),
            adminStore = testDataStore(scope, File(disk.directory, "admin").apply { mkdirs() }),
            vehicle = vehicle,
            navi = navi,
            voiceOutput = voiceOutput,
            voiceInput = { voiceInput },
            clock = clock,
            scope = scope,
        ),
    )

    val trips get() = container.trips
    val recorder get() = container.recorder
    val nextDeparture get() = container.nextDeparture
    val settings get() = container.settings
    val people get() = container.people
    val admin get() = container.admin

    /** 기록 서비스가 뜬 것처럼 기록기를 시작한다 */
    fun startRecording() = container.startRecording()

    /** 첫 실행을 마친 상태로 (기본: 업무 · 혼자 · 집·회사 없음) */
    suspend fun completeFirstRun(
        usage: VehicleUsage = VehicleUsage.SOLO,
        defaultCategory: Category = Category.BUSINESS,
        people: List<String> = listOf("박정훈"),
        home: TripPlace? = null,
        office: TripPlace? = null,
    ) {
        val result = FirstRunChoice(
            isSkipped = false,
            usage = usage,
            defaultCategory = defaultCategory,
            home = home,
            office = office,
            people = people.map { NewPerson(it, it.take(1)) },
        ).toResult()
        container.people.replaceAll(result.people)
        container.settings.completeFirstRun(result)
        await(container.people.people) { it.size == result.people.size }
        await(container.nextDeparture.next) { it != null && it.category == defaultCategory }
    }

    /**
     * 운행 한 번: 출발해 계기판을 [km] 만큼 늘리고 주차한다. 저장된 운행을 돌려준다.
     * [destination] 은 내비 경로 목적지 (null 이면 내비 없이 운행). 도착하면 내비 안내가 끝난다.
     */
    suspend fun driveTrip(km: Double, destination: TripPlace? = null, minutes: Long = DEFAULT_TRIP_MINUTES): Trip {
        val countBefore = trips.trips.value.size
        startDriving()
        destination?.let { navi.destination.value = it }
        vehicle.drive(km)
        clock.advance(Duration.ofMinutes(minutes))
        val trip = park(countBefore)
        navi.destination.value = null
        return trip
    }

    /**
     * 출발만 (기어 D). 출발 처리(빠진 주행 저장·진행 중 운행 열기)가 끝나 진행 중 운행이 디스크에 열릴 때까지 기다린다.
     * 주행 중 화면·관리자 잠금을 볼 때 쓴다.
     */
    suspend fun startDriving() {
        vehicle.shift(Gear.DRIVE)
        await(recorder.isDriving) { it }
        await(recorder.currentDeparture) { it != null }
        awaitValue({ trips.activeTrip() }) { it != null }
    }

    /**
     * 주차 (기어 P). 운행이 저장되고 주차 처리(도착지 채우기 · 도착 음성 · "방금 운행")가 끝날 때까지 기다린다.
     * 운행 목록은 저장 직후 바뀌고 음성은 그 뒤에 나가서, 목록만 기다리면 음성을 확인하는 테스트가 가끔 먼저 돈다.
     * 출발 때 "기록 안 된 주행"이 먼저 붙을 수 있어 목록의 마지막이 아니라 "방금 운행" id 로 찾는다.
     */
    suspend fun park(countBefore: Int = trips.trips.value.size): Trip {
        val previousRecent = recorder.recentTripId.value
        vehicle.shift(Gear.PARK)
        val tripId = requireNotNull(await(recorder.recentTripId) { it != null && it != previousRecent })
        // 운행은 주차하자마자 저장되고 도착지는 뒤에 채워진다. 목록이 마지막 저장본(도착지 채움까지)을 보일 때까지 기다린다
        val stored = requireNotNull(trips.find(tripId))
        await(trips.trips) { list -> list.size > countBefore && list.any { it == stored } }
        return stored
    }

    /** 앱이 꺼진 사이 계기판만 늘어남 (빠진 주행) */
    fun driveWhileAppOff(km: Double) = vehicle.drive(km)

    /** 날짜가 [days] 일 지난 것처럼: 시계를 앞으로 돌리고 화면의 오늘을 다시 읽게 한다 (실제 앱은 자정에 저절로) */
    fun passDays(days: Long) {
        clock.advance(Duration.ofDays(days))
        container.today.refresh()
    }

    /**
     * 앱(또는 차)이 꺼졌다 켜짐: 이 앱을 끄고 같은 저장소·시계로 새 앱을 만든다. 실제 앱처럼 꺼지는 순간에 따로
     * 저장하지 않아, 메모리에만 있던 것(관리자 잠금 풀림, 다음 출발 선택)은 사라진다.
     * 새 앱의 기록기는 아직 시작하지 않는다 ([startRecording]). 이 앱은 더 쓰지 않고 테스트 끝에는 새 앱만 [close] 한다.
     */
    suspend fun restart(odometerKm: Double? = vehicle.odometerKm.value, gear: Gear = Gear.PARK): TestApp {
        scope.coroutineContext.job.cancelAndJoin() // DataStore 가 파일을 놓아야 새 앱이 같은 파일을 연다
        return TestApp(odometerKm, gear, disk, clock)
    }

    fun close() {
        scope.cancel()
        disk.close()
    }

    companion object {
        const val START_ODOMETER_KM = 1_000.0
        private const val DEFAULT_TRIP_MINUTES = 20L
        private const val AWAIT_MILLIS = 10_000L // 테스트 전체를 한꺼번에 돌릴 때 첫 Robolectric 기동이 느려도 버티게
        private const val POLL_MILLIS = 20L

        /** [flow] 가 [condition] 을 만족할 때까지 기다린다 (최대 [AWAIT_MILLIS]) */
        suspend fun <T> await(flow: Flow<T>, condition: (T) -> Boolean): T =
            withTimeout(AWAIT_MILLIS) { flow.first(condition) }

        /** Flow 가 없는 값(DB 의 진행 중 운행 등)을 [condition] 을 만족할 때까지 다시 읽는다 (최대 [AWAIT_MILLIS]) */
        suspend fun <T> awaitValue(read: suspend () -> T, condition: (T) -> Boolean): T =
            withTimeout(AWAIT_MILLIS) {
                var value = read()
                while (!condition(value)) {
                    delay(POLL_MILLIS)
                    value = read()
                }
                value
            }
    }
}
