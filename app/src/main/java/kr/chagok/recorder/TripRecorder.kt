package kr.chagok.recorder

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kr.chagok.car.CarConfig
import kr.chagok.car.Gear
import kr.chagok.car.NaviSource
import kr.chagok.car.VehicleSource
import kr.chagok.common.runSuspendCatching
import kr.chagok.data.NextDepartureStore
import kr.chagok.data.TripRepository
import kr.chagok.data.admin.AdminRepository
import kr.chagok.domain.ChagokConfig
import kr.chagok.domain.PlaceMatcher
import kr.chagok.domain.TripRules
import kr.chagok.domain.VisitHistory
import kr.chagok.domain.model.ActiveTrip
import kr.chagok.domain.model.Category
import kr.chagok.domain.model.ClassifyReason
import kr.chagok.domain.model.ChagokSettings
import kr.chagok.domain.model.NextDeparture
import kr.chagok.domain.model.Trip
import kr.chagok.domain.model.TripPlace
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * 운행 자동 기록기. 기어가 P 를 벗어나면 출발, P 로 돌아오면 주차로 보고 계기판 누적거리로 운행을 끊는다.
 * 달리는 동안 진행 중 운행을 디스크에 계속 남겨, 앱이 꺼져도 다시 켜질 때 잇거나 마감한다.
 * 차 이벤트와 음성 명령은 하나씩 차례로 처리한다 (출발 처리 중에 주차가 끼어들지 않게).
 */
class TripRecorder(
    private val vehicle: VehicleSource,
    private val navi: NaviSource,
    private val trips: TripRepository,
    private val departure: NextDepartureStore,
    private val settings: Flow<ChagokSettings>,
    private val admin: AdminRepository,
    private val tripVoice: TripVoice,
    private val clock: Clock,
    private val scope: CoroutineScope,
) {
    private val active = MutableStateFlow<ActiveTrip?>(null)
    private val _recentTripId = MutableStateFlow<Long?>(null)
    private val turn = Mutex() // 기어·계기판 이벤트와 음성 명령이 같은 운행을 동시에 고치지 않게
    private var isStarted = false
    private var isRecovered = false

    // 주차했지만 기록부로 옮기지 못한 운행. 다음 차 이벤트 때 먼저 다시 옮긴다
    private var unsavedParking: Trip? = null

    // 지난 방문을 이미 말한 목적지. 주차하면 비워서 다음에 같은 곳으로 가면 다시 말한다
    private var briefedPlace: TripPlace? = null

    /**
     * 앱이 켜진 뒤 주차까지 마친 운행이 있는지. 없으면 주차 중에 말한 "이번꺼"는 곧 떠날 운행이다.
     * 다시 켜지며 마감한 운행은 세지 않는다
     */
    @Volatile
    var hasParkedSinceStart = false
        private set

    /** P단이 아니면 주행 중 (주행 중 화면으로 바꾸는 기준) */
    val isDriving: StateFlow<Boolean> = vehicle.gear
        .map { it.isDriving }
        .stateIn(scope, SharingStarted.Eagerly, false)

    val odometerKm: StateFlow<Double?> = vehicle.odometerKm

    /** 이번 운행 분류: 주행 중이면 출발 때 정한 값, 아니면 다음 출발 값 */
    val currentDeparture: StateFlow<NextDeparture?> = combine(active, departure.next) { trip, next ->
        trip?.departure ?: next
    }.stateIn(scope, SharingStarted.Eagerly, null)

    /**
     * 주행 화면에 보일 분류. 업무 운행이 내비로 집↔회사 경로가 되면 "출퇴근으로 기록됩니다" 안내에 맞춰 출퇴근을
     * 보인다. 기록부 분류는 도착 때 정한다
     */
    val drivingCategory: StateFlow<Category?> = combine(active, departure.next, settings) { trip, next, current ->
        when {
            trip == null -> next?.category
            isCommuteRoute(trip, current) -> Category.COMMUTE
            else -> trip.departure.category
        }
    }.stateIn(scope, SharingStarted.Eagerly, null)

    /** 방금 주차해 저장한 운행 (홈 "방금 운행" 카드). 앱이 스스로 앞으로 나오지 않으니 화면을 열 때 보인다 */
    val recentTripId: StateFlow<Long?> = _recentTripId.asStateFlow()

    fun clearRecentTrip() {
        _recentTripId.value = null
    }

    /** 방금 닫은 카드를 "다시 보기"로 다시 연다 */
    fun showRecentTrip(tripId: Long) {
        _recentTripId.value = tripId
    }

    fun start() {
        if (isStarted) return
        isStarted = true
        vehicle.start()
        navi.start()
        scope.launch {
            merge(
                vehicle.odometerKm.filterNotNull().map(Event::Odometer),
                vehicle.gear.map(Event::GearChanged),
                navi.destination.filterNotNull().map(Event::Destination),
            ).buffer(Channel.UNLIMITED).collect(::handle)
        }
    }

    private suspend fun handle(event: Event) {
        turn.withLock {
            runSuspendCatching {
                retryUnsavedParking(event)
                when (event) {
                    is Event.Odometer -> onOdometer(event.km)
                    is Event.GearChanged -> onGear(event.gear)
                    is Event.Destination -> onDestination(event.place)
                }
            }.onFailure { Log.e(TAG, "$event 처리 실패", it) }
        }
    }

    private suspend fun onOdometer(km: Double) {
        recoverOnce(vehicle.gear.value)
        val trip = active.value ?: return
        val now = LocalTime.now(clock)
        trips.checkpoint(km, now)
        active.value = trip.copy(lastKm = km, lastAt = now)
    }

    private suspend fun onGear(gear: Gear) {
        recoverOnce(gear)
        val trip = active.value
        when {
            gear.isDriving && trip == null -> startTrip()
            gear == Gear.PARK && trip != null -> finishTrip(trip)
        }
    }

    /**
     * 주행 중 음성으로 이번 운행 분류를 바꾼다. 달리는 운행이 없으면 null (주차 중이다).
     * 개인으로 바꾸면 그때까지 남은 출발지·목적지를 지우고, 주차해도 그 자리 위치를 읽지 않는다.
     * 개인에서 업무로 바꾸면 출발지는 모른 채로 두고 도착지만 남는다.
     */
    suspend fun reclassifyDriving(category: Category): ActiveTrip? = turn.withLock {
        val trip = active.value ?: return@withLock null
        if (trip.departure.category == category) return@withLock trip
        val isPersonal = category == Category.PERSONAL
        val changed = trip.copy(
            departure = trip.departure.copy(category = category),
            origin = if (isPersonal) null else trip.origin,
            destination = if (isPersonal) null else trip.destination,
            isEditedWhileDriving = true,
        )
        saveActive(changed)
    }

    /** 주행 중 음성으로 이번 운행의 사람을 정한다. 이미 정해졌으면 그대로 둔다 (바꾸는 건 관리자 일). 주차 중이면 null */
    suspend fun assignDrivingPerson(personId: Long): ActiveTrip? = turn.withLock {
        val trip = active.value ?: return@withLock null
        if (trip.departure.personId != null) return@withLock trip
        val changed = trip.copy(departure = trip.departure.copy(personId = personId))
        saveActive(changed)
    }

    private suspend fun onDestination(place: TripPlace) {
        attachDestination(place)
        // 안내는 덤이다. 실패해도 목적지 기록에는 영향이 없다
        runSuspendCatching { briefVisit(place) }.onFailure { Log.w(TAG, "지난 방문 안내 실패", it) }
    }

    // 주행 중에 내비 목적지가 정해지면 운행에 붙인다. 도착 이벤트에는 좌표가 없어서 미리 받아 둔다
    private suspend fun attachDestination(place: TripPlace) {
        val trip = active.value ?: return
        if (trip.departure.category == Category.PERSONAL || place.isEmpty) return
        val changed = saveActive(trip.copy(destination = place))
        announceIfCommuteRoute(before = trip, after = changed)
    }

    /**
     * 메모를 남긴 곳을 다시 내비로 찍으면 지난 방문을 한 번 말한다 (출발 전이든 주행 중이든).
     * 개인 운행, 집·회사, 이름 없는 곳은 말하지 않는다. 메모는 동승석이 모두 비어 있을 때만 읽는다
     */
    private suspend fun briefVisit(place: TripPlace) {
        if (!place.hasName || PlaceMatcher.isSamePlace(place, briefedPlace)) return
        // 이벤트가 주차보다 늦게 처리되면 이미 도착한 곳이다
        if (!PlaceMatcher.isSamePlace(place, navi.destination.value)) return
        val category = active.value?.departure?.category ?: departure.next.value?.category
        if (category == Category.PERSONAL) return
        val current = settings.first()
        if (!current.isTripVoiceOn) return
        if (PlaceMatcher.isSamePlace(place, current.home) || PlaceMatcher.isSamePlace(place, current.office)) return
        val visit = VisitHistory.lastNoted(trips.all(), place) ?: return
        val isAlone = vehicle.passengerCount.value == 0
        tripVoice.announceVisit(place.name, visit, LocalDate.now(clock), isAlone)
        briefedPlace = place
    }

    // 업무 운행이 내비 목적지로 집↔회사 경로가 되는 순간 한 번 알린다 (이미 출퇴근이었거나 말로 출퇴근이라고 정했으면 말하지 않는다)
    private suspend fun announceIfCommuteRoute(before: ActiveTrip?, after: ActiveTrip) {
        val current = settings.first()
        if (!current.isTripVoiceOn) return
        val wasCommute = before != null && isCommuteRoute(before, current)
        if (!wasCommute && isCommuteRoute(after, current)) tripVoice.announceCommuteRoute()
    }

    // 업무로 달리는 운행의 출발지·목적지가 집↔회사 (도착 때 출퇴근이 될 운행)
    private fun isCommuteRoute(trip: ActiveTrip, current: ChagokSettings): Boolean =
        trip.departure.category == Category.BUSINESS &&
            PlaceMatcher.isHomeOfficeTrip(trip.origin, trip.destination, current.home, current.office)

    /**
     * 다시 켜진 뒤 처음 한 번, 끝나지 않은 운행을 잇거나 마감한다. 기어를 받기 전에는 미룬다.
     * 판단은 지금 처리하는 이벤트의 기어로 한다. 큐에 D·P 가 함께 쌓였을 때 현재 값 P 로 마감하고 이벤트 D 로
     * 출발하면 0km 운행이 생긴다. 달리는 중이어도 마지막 저장이 오래됐으면 다른 시동의 운행이라 마감한다.
     * 실패하면 다음 이벤트에서 다시 한다 (그냥 넘어가면 다음 출발이 진행 중 운행을 덮어쓴다).
     */
    private suspend fun recoverOnce(gear: Gear) {
        if (isRecovered || gear == Gear.UNKNOWN) return
        val open = trips.activeTrip()
        val isResuming = open != null && gear.isDriving && !isStale(open)
        when {
            open == null -> Unit
            isResuming -> active.value = open
            else -> {
                Log.i(TAG, "끝나지 않은 운행을 마지막 저장값(${open.lastKm}km)으로 마감")
                closeOpenTrip(open)
            }
        }
        isRecovered = true
        if (open != null && !isResuming && gear.isDriving) startTrip()
    }

    // 자정을 넘겨 달린 운행은 마지막 저장 시각이 출발 시각보다 작다 (다음 날이다)
    private fun isStale(trip: ActiveTrip): Boolean {
        val lastDay = if (trip.lastAt < trip.startedAt) trip.date.plusDays(1) else trip.date
        val lastSaved = lastDay.atTime(trip.lastAt)
        val maxGap = Duration.ofMinutes(ChagokConfig.RESUME_MAX_GAP_MINUTES)
        return Duration.between(lastSaved, LocalDateTime.now(clock)) > maxGap
    }

    private suspend fun startTrip() {
        trips.commitPlaceDeletion() // 출발하면 개인으로 바꾼 앞 운행의 장소 삭제를 확정
        admin.lock() // 차를 넘겨받은 운전자는 운전자 권한으로 시작한다
        val km = currentOdometer()
        if (km == null) {
            Log.w(TAG, "계기판 값이 없어 출발을 기록하지 못함")
            return
        }
        val today = LocalDate.now(clock)
        val lastEndKm = trips.lastEndKm()
        if (lastEndKm != null && TripRules.unrecordedKm(lastEndKm, km) > 0) trips.addGap(today, lastEndKm, km)

        val next = departure.next.value
        if (next == null) {
            Log.w(TAG, "설정을 읽기 전이라 출발을 기록하지 못함")
            return
        }
        val isPersonal = next.category == Category.PERSONAL
        val now = LocalTime.now(clock)
        val trip = ActiveTrip(
            date = today,
            startKm = km,
            startedAt = now,
            lastKm = km,
            lastAt = now,
            departure = next,
            origin = if (isPersonal) null else navi.currentLocation(),
            // 출발 전에 정해 둔 경로
            destination = if (isPersonal) null else navi.destination.value?.takeUnless(TripPlace::isEmpty),
        )
        saveActive(trip)
        announceIfCommuteRoute(before = null, after = trip) // 출발 전에 내비로 집·회사를 찍어 둔 경우
    }

    /**
     * 아는 도착지로 먼저 기록부에 옮긴다. 위치를 기다리는 동안 꺼져도 운행은 남는다.
     * 내비 없이 왔으면 그 뒤에 주차한 자리 주소로 도착지와 도착 기준 분류(출퇴근)를 채운다.
     * 음성과 "방금 운행"은 도착지가 정해진 뒤 한 번.
     */
    private suspend fun finishTrip(trip: ActiveTrip) {
        val endKm = vehicle.odometerKm.value ?: trip.lastKm
        // 도착지를 기다린 뒤에 비우면 그사이 고른 다음 출발 값까지 지워진다. 이번 운행 값은 trip 에 있다
        departure.clear()
        val current = settings.first()
        val known = knownDestination(trip, guiding = navi.destination.value)
        val saved = closedTrip(trip, endKm, LocalTime.now(clock), known, current)
        // 저장이 실패해도 차는 섰다. 메모리에서 내려야 다음 계기판·출발이 이 운행에 이어 붙지 않는다
        active.value = null
        briefedPlace = null
        unsavedParking = saved
        val id = saveParking()
        val needsLocation = known == null && trip.departure.category != Category.PERSONAL
        val final = if (needsLocation) fillArrival(saved.copy(id = id), trip, current) else saved
        if (current.isTripVoiceOn) tripVoice.announceArrival(final)
        hasParkedSinceStart = true
        _recentTripId.value = id
    }

    private suspend fun saveParking(): Long {
        val id = trips.closeActiveTrip(checkNotNull(unsavedParking))
        unsavedParking = null
        return id
    }

    /**
     * 주차 저장이 실패했던 운행을 다시 옮긴다. 또 실패하면 이번 이벤트도 처리하지 않는다.
     * 그사이 출발 신호를 놓쳤으면 지금부터 새 운행을 연다 (놓친 거리는 기록 안 된 주행으로 남는다).
     */
    private suspend fun retryUnsavedParking(event: Event) {
        if (unsavedParking == null) return
        val id = saveParking()
        hasParkedSinceStart = true
        _recentTripId.value = id
        // 기어 이벤트는 바로 뒤 onGear 가 출발을 맡는다
        if (event !is Event.GearChanged && vehicle.gear.value.isDriving && active.value == null) startTrip()
    }

    // 방금 도착한 것이 아니라서 위치를 읽지 않고 말하지도 않는다
    private suspend fun closeOpenTrip(trip: ActiveTrip) {
        val saved = closedTrip(trip, trip.lastKm, trip.lastAt, knownDestination(trip, guiding = null), settings.first())
        trips.closeActiveTrip(saved)
        active.value = null
    }

    // 응답이 늦으면 도착지를 비워 둔다 (마지막 위치는 대개 출발 자리다). 운행은 이미 저장돼 있어 실패해도 로그만 남긴다.
    // 위치를 기다리는 사이 사용자가 분류를 고쳤을 수 있어, 위치가 안 왔어도 저장된 운행을 다시 읽어 돌려준다
    private suspend fun fillArrival(saved: Trip, trip: ActiveTrip, current: ChagokSettings): Trip {
        val place = navi.freshLocation()
        return runSuspendCatching {
            if (place == null) {
                trips.find(saved.id)
            } else {
                val (category, reason) = classifyAtArrival(trip, place, current)
                trips.fillArrival(saved, saved.copy(destination = place, category = category, reason = reason))
            }
        }.onFailure { Log.e(TAG, "도착지 채우기 실패", it) }
            .getOrNull() ?: saved
    }

    // 빈 목적지(경로가 없을 때 내비가 준 값)는 디스크에서 되살린 진행 중 운행에 붙어 있을 수 있어 여기서도 거른다.
    // 목적지 이벤트보다 주차가 먼저 처리됐을 수 있어 아직 안내 중인 목적지도 본다
    private fun knownDestination(trip: ActiveTrip, guiding: TripPlace?): TripPlace? {
        if (trip.departure.category == Category.PERSONAL) return null
        return trip.destination?.takeUnless(TripPlace::isEmpty) ?: guiding?.takeUnless(TripPlace::isEmpty)
    }

    private fun closedTrip(
        trip: ActiveTrip,
        endKm: Double,
        endedAt: LocalTime,
        destination: TripPlace?,
        current: ChagokSettings,
    ): Trip {
        val isPersonal = trip.departure.category == Category.PERSONAL
        val (category, reason) = classifyAtArrival(trip, destination, current)
        return Trip(
            date = trip.date,
            personId = trip.departure.personId,
            origin = if (isPersonal) null else trip.origin,
            destination = destination,
            startKm = trip.startKm,
            endKm = endKm,
            startedAt = trip.startedAt,
            endedAt = endedAt,
            category = category,
            reason = reason,
        )
    }

    // 주행 중 음성으로 바꾼 운행은 출발 때 값이 아니라 사람이 고친 것이라 근거가 "사용자 수정"이다
    private fun classifyAtArrival(
        trip: ActiveTrip,
        destination: TripPlace?,
        current: ChagokSettings,
    ): Pair<Category, ClassifyReason> {
        val (category, reason) =
            TripRules.classifyAtArrival(trip.departure, trip.origin, destination, current.home, current.office)
        return category to if (trip.isEditedWhileDriving) ClassifyReason.USER_EDIT else reason
    }

    // 디스크에 먼저 쓰고 메모리에 반영한다. 쓰기가 실패하면 메모리도 그대로라 다음 이벤트가 같은 상태에서 다시 한다
    private suspend fun saveActive(trip: ActiveTrip): ActiveTrip {
        trips.saveActiveTrip(trip)
        active.value = trip
        return trip
    }

    // 출발 순간에 계기판 값이 아직 없으면 잠깐 기다린다
    private suspend fun currentOdometer(): Double? = vehicle.odometerKm.value
        ?: withTimeoutOrNull(CarConfig.ODOMETER_WAIT_MILLIS) { vehicle.odometerKm.filterNotNull().first() }

    private sealed interface Event {
        data class Odometer(val km: Double) : Event
        data class GearChanged(val gear: Gear) : Event
        data class Destination(val place: TripPlace) : Event
    }

    private companion object {
        const val TAG = "TripRecorder"
    }
}
