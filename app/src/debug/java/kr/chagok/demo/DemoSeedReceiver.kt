package kr.chagok.demo

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kr.chagok.AppContainer
import kr.chagok.ChagokApplication
import kr.chagok.common.runSuspendCatching
import kr.chagok.domain.FirstRunResult
import kr.chagok.domain.NewPerson
import kr.chagok.domain.model.Category
import kr.chagok.domain.model.ClassifyReason
import kr.chagok.domain.model.Trip
import kr.chagok.domain.model.TripPlace
import kr.chagok.domain.model.VehicleUsage
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * [디버그 전용] 시연 샘플을 넣는다: 10월 1일부터 어제까지 쓰던 공용차, 관리자 PIN([DemoSample.ADMIN_PIN])으로 잠긴 상태.
 * `--ez solo true` 를 붙이면 박정훈 혼자 타는 차로 넣고 PIN 은 걸지 않는다. 요일과 상관없이 오늘 운행 세 건(출근, 거래처
 * 왕복)을 지금 시각 전에 끝나게 넣어 홈 "오늘"이 차 있게 한다(시연 운행이 그 뒤에 이어 쌓인다).
 * 기록이 없을 때만 넣는다(첫 실행을 마친 뒤여도 된다. 설정은 샘플 값으로 덮어쓴다). 다시 넣으려면 앱 데이터를 지운다.
 *
 *   adb shell am broadcast --user 10 -a kr.chagok.demo.SEED -n kr.chagok/.demo.DemoSeedReceiver
 *   adb shell am broadcast --user 10 -a kr.chagok.demo.SEED -n kr.chagok/.demo.DemoSeedReceiver --ez solo true
 */
class DemoSeedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val container = (context.applicationContext as ChagokApplication).container
        val isSolo = intent.getBooleanExtra(EXTRA_SOLO, false)
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                runSuspendCatching { seed(container, isSolo) }.onFailure { Log.e(TAG, "시연 샘플 넣기 실패", it) }
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun seed(container: AppContainer, isSolo: Boolean) {
        if (container.trips.lastEndKm() != null) {
            Log.w(TAG, "기록이 이미 있어 넣지 않음 (앱 데이터를 지우고 다시)")
            return
        }

        // 혼자 타는 차는 대표 한 명만 두고, 영업팀이 탔던 운행도 대표가 탄 것으로 넣는다
        val samplePeople = if (isSolo) DemoSample.PEOPLE.take(1) else DemoSample.PEOPLE
        container.people.replaceAll(samplePeople.map { NewPerson(it.name, it.name.take(1), it.dept) })
        val people = withTimeoutOrNull(PEOPLE_WAIT_MILLIS) {
            container.people.people.first { it.size == samplePeople.size }
        }
        if (people == null) {
            Log.e(TAG, "사람 등록을 확인하지 못함")
            return
        }
        val registered = samplePeople.zip(people).associate { (sample, person) -> sample.key to person.id }
        val idOf = DemoSample.PEOPLE.associate { it.key to (registered[it.key] ?: people.first().id) }

        val places = demoPlaces(container)
        container.settings.completeFirstRun(
            FirstRunResult(
                usage = if (isSolo) VehicleUsage.SOLO else VehicleUsage.SHARED,
                defaultCategory = Category.BUSINESS,
                home = places.getValue("home"),
                office = places.getValue("office"),
                people = emptyList(),
            ),
        )
        container.settings.setVehicleInfo(DemoSample.VEHICLE)
        // 한 달 쓴 차라 홈 시작 가이드의 "말해 보기"도 해 본 것으로 둔다 (첫 실행을 따로 찍고 지운 뒤 넣어도 카드가 안 남게)
        container.settings.markChangeTried()
        if (!isSolo) {
            container.admin.setPinAndKey(DemoSample.ADMIN_PIN, container.admin.newRecoveryKey())
            container.admin.lock()
        }

        val endKm = maxOf(container.recorder.odometerKm.value ?: 0.0, DemoSample.DEFAULT_END_ODOMETER_KM)
        val now = LocalDateTime.now(container.clock)
        container.trips.importTrips(sampleTrips(idOf, places, endKm, now, includesToday = isSolo))
        Log.i(TAG, "시연 샘플을 넣었음 (${if (isSolo) "혼자" else "공용"}, 마지막 계기판 ${endKm}km)")
    }

    // 계기판이 끊기지 않게 이어 붙여 [endKm] 에서 끝나게 한다
    /**
     * 샘플 장소. 시연에서 내비로 회사를 찍으면 출퇴근으로 잡히게 집은 에뮬레이터 차가 서 있는 자리(출발지로 읽히는 곳),
     * 회사는 내비 즐겨찾기 회사로 바꾼다. 이름은 샘플 그대로 두어 지난 기록과 맞춘다. 읽지 못하면 지어낸 곳 그대로
     */
    private suspend fun demoPlaces(container: AppContainer): Map<String, TripPlace> {
        val here = container.navi.currentLocation()?.takeUnless(TripPlace::isEmpty)
        val office = container.navi.freshBookmarks().office?.takeUnless(TripPlace::isEmpty)
        val sample = DemoSample.PLACES
        Log.i(TAG, "집: ${here?.address ?: "지어낸 곳"}, 회사: ${office?.displayName ?: "즐겨찾기 없음, 지어낸 곳"}")
        return sample + listOfNotNull(
            here?.let { "home" to it.copy(name = sample.getValue("home").name) },
            office?.let { "office" to it.copy(name = sample.getValue("office").name) },
        )
    }

    private fun sampleTrips(
        idOf: Map<String, Long>,
        places: Map<String, TripPlace>,
        endKm: Double,
        now: LocalDateTime,
        includesToday: Boolean,
    ): List<Trip> {
        val today = if (includesToday) todayPlans(idOf.getValue("park"), now) else emptyList()
        val plans = priorMonthPlans(idOf.getValue("park")) + octoberPlans(idOf) +
            recentPlans(idOf, now.toLocalDate()) + today
        var odometer = endKm - plans.sumOf { it.km }
        return plans.map { plan ->
            val startKm = odometer
            odometer += plan.km
            plan.toTrip(startKm, odometer, places)
        }
    }

    private fun octoberPlans(idOf: Map<String, Long>): List<Plan> =
        DemoSample.OCTOBER.groupBy { it.day }.flatMap { (day, trips) ->
            trips.mapIndexed { index, trip ->
                Plan(
                    date = LocalDate.of(DemoSample.YEAR, DemoSample.MONTH, day),
                    startedAt = DEPARTURE_TIMES[index % DEPARTURE_TIMES.size],
                    personId = idOf.getValue(trip.person),
                    from = trip.from,
                    to = trip.to,
                    category = trip.category,
                    km = DemoSample.distanceKm(trip.from, trip.to),
                    note = trip.note,
                )
            }
        }

    // 11월 1일부터 오늘 전날까지. 평일은 출퇴근과 거래처 왕복(사람·거래처를 돌아가며), 토요일은 개인 왕복
    private fun recentPlans(idOf: Map<String, Long>, today: LocalDate): List<Plan> {
        val start = LocalDate.of(DemoSample.YEAR, DemoSample.MONTH, 1).plusMonths(1)
        val dates = generateSequence(start) { it.plusDays(1) }.takeWhile { it < today }
        return dates.withIndex().toList().flatMap { (index, date) ->
            when (date.dayOfWeek) {
                DayOfWeek.SUNDAY -> emptyList()
                DayOfWeek.SATURDAY -> {
                    val place = DemoSample.SATURDAY_PLACES[index % DemoSample.SATURDAY_PLACES.size]
                    listOf(
                        plan(date, 1, idOf.getValue("park"), "home", place, Category.PERSONAL),
                        plan(date, 2, idOf.getValue("park"), place, "home", Category.PERSONAL),
                    )
                }
                else -> {
                    val (client, note) = DemoSample.WEEKDAY_VISITS[index % DemoSample.WEEKDAY_VISITS.size]
                    val visitor = idOf.getValue(if (index % 2 == 0) "park" else "choi")
                    listOf(
                        plan(date, 0, idOf.getValue("park"), "home", "office", Category.COMMUTE),
                        plan(date, 1, visitor, "office", client, Category.BUSINESS, note),
                        plan(date, 2, visitor, client, "office", Category.BUSINESS),
                        plan(date, 3, idOf.getValue("park"), "office", "home", Category.COMMUTE),
                    )
                }
            }
        }
    }

    /**
     * 오늘 출근 → 거래처 → 회사. 언제 녹화하든 다 끝난 운행이 되게 지금 시각에서 거꾸로 [TODAY_TRIP_GAP_MINUTES] 간격으로
     * 둔다. 새벽이라 앞 운행이 어제로 넘어가면 넣지 않는다
     */
    private fun todayPlans(personId: Long, now: LocalDateTime): List<Plan> {
        val (client, note) = DemoSample.WEEKDAY_VISITS[now.dayOfMonth % DemoSample.WEEKDAY_VISITS.size]
        val legs = listOf(
            Triple("home", "office", Category.COMMUTE),
            Triple("office", client, Category.BUSINESS),
            Triple(client, "office", Category.BUSINESS),
        )
        val firstStart = now.minusMinutes(TODAY_TRIP_GAP_MINUTES * legs.size)
        if (firstStart.toLocalDate() != now.toLocalDate()) return emptyList()
        return legs.mapIndexed { index, (from, to, category) ->
            Plan(
                date = now.toLocalDate(),
                startedAt = firstStart.plusMinutes(TODAY_TRIP_GAP_MINUTES * index).toLocalTime(),
                personId = personId,
                from = from,
                to = to,
                category = category,
                km = DemoSample.distanceKm(from, to),
                note = if (to == client) note else "",
            )
        }
    }

    private fun plan(
        date: LocalDate,
        order: Int,
        personId: Long,
        from: String,
        to: String,
        category: Category,
        note: String = "",
    ) = Plan(date, DEPARTURE_TIMES[order], personId, from, to, category, DemoSample.distanceKm(from, to), note)

    // 1~9월은 달 합계만 있으니 분류별 운행 한 건씩으로 합계를 맞춘다
    private fun priorMonthPlans(personId: Long): List<Plan> = DemoSample.PRIOR_MONTHS.flatMap { month ->
        val date = LocalDate.of(DemoSample.YEAR, month.month, PRIOR_MONTH_DAY)
        listOf(
            Plan(date, DEPARTURE_TIMES[0], personId, "home", "office", Category.COMMUTE, month.commute),
            Plan(date, DEPARTURE_TIMES[1], personId, "office", "c-elec", Category.BUSINESS, month.business),
            Plan(
                date, DEPARTURE_TIMES[3], personId, "home", "mart", Category.PERSONAL,
                month.total - month.commute - month.business,
            ),
        )
    }

    private data class Plan(
        val date: LocalDate,
        val startedAt: LocalTime,
        val personId: Long,
        val from: String,
        val to: String,
        val category: Category,
        val km: Double,
        val note: String = "",
    ) {
        val endedAt: LocalTime get() = startedAt.plusMinutes(minOf(km * MINUTES_PER_KM, MAX_TRIP_MINUTES).toLong())

        // 샘플의 업무 운행은 기본값 그대로, 개인 운행은 출발 전에 개인으로 바꾼 것으로 둔다 (개인은 장소 없음)
        fun toTrip(startKm: Double, endKm: Double, places: Map<String, TripPlace>): Trip {
            val isPersonal = category == Category.PERSONAL
            return Trip(
                date = date,
                personId = personId,
                origin = if (isPersonal) null else places[from],
                destination = if (isPersonal) null else places[to],
                startKm = startKm,
                endKm = endKm,
                startedAt = startedAt,
                endedAt = endedAt,
                category = category,
                reason = if (isPersonal) ClassifyReason.PRE_PICK else ClassifyReason.DEFAULT_BUSINESS,
                note = note,
            )
        }
    }

    private companion object {
        const val TAG = "DemoSeedReceiver"
        const val EXTRA_SOLO = "solo"
        const val PEOPLE_WAIT_MILLIS = 3_000L
        const val PRIOR_MONTH_DAY = 15
        const val MINUTES_PER_KM = 1.5
        const val TODAY_TRIP_GAP_MINUTES = 90L // 가장 먼 거래처(평택공장) 편도가 이 안에 끝난다
        const val MAX_TRIP_MINUTES = 120.0 // 1~9월 합계 운행은 길어서 도착 시각이 다음 날로 넘어가지 않게
        val DEPARTURE_TIMES =
            listOf(LocalTime.of(8, 20), LocalTime.of(10, 5), LocalTime.of(15, 40), LocalTime.of(18, 50))
    }
}
