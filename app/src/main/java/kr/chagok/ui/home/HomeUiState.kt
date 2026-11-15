package kr.chagok.ui.home

import kr.chagok.domain.FiscalYear
import kr.chagok.domain.ChagokConfig
import kr.chagok.domain.TripRules
import kr.chagok.domain.model.AdminState
import kr.chagok.domain.model.Category
import kr.chagok.domain.model.CategoryDay
import kr.chagok.domain.model.DistanceTotals
import kr.chagok.domain.model.ChagokSettings
import kr.chagok.domain.model.NextDeparture
import kr.chagok.domain.model.Person
import kr.chagok.domain.model.Refuel
import kr.chagok.domain.model.Trip
import kr.chagok.ui.guide.StartGuide
import java.time.LocalDate
import java.time.YearMonth

/**
 * 홈 화면 한 장의 상태.
 * 위에서 아래 순서가 곧 화면 순서다: 할 일(방금 운행·분류 대기·미지정·주유 금액) 먼저, 볼거리(요약 카드)는 맨 아래.
 * null 인 칸은 해당될 때만 보이는 덩어리다.
 */
data class HomeUiState(
    /** 설정 > 사용자 순서 그대로, 숨긴 사람은 빠져 있다 */
    val people: List<Person>,
    val recent: RecentCardState?,
    /** 방금 운행 카드를 닫은 뒤 잠깐 남는 "닫았어요 · 다시 보기" 줄 */
    val isRecentClosed: Boolean,
    val pendingTrips: List<Trip>,
    /** [pendingTrips] 맨 앞 하나를 크게 보여 주는 장면. 없으면 null */
    val gap: GapSceneState?,
    val unassigned: UnassignedState?,
    val refuel: RefuelTodoState?,
    val next: NextDepartureState,
    val today: Map<Category, CategoryDay>?,
    /** 오늘 운행, 시간순 (넓은 창 홈은 요약 대신 한 줄씩 보인다) */
    val todayTrips: List<Trip>,
    val summary: SummaryState,
    /** 홈 시작 가이드 (첫 실행에서 건너뛴 것 이어 하기). 닫았거나 다 했으면 null */
    val guide: StartGuide?,
)

/** 방금 운행 카드 (도착·주차 직후) */
data class RecentCardState(
    val trip: Trip,
    val isFirstVisit: Boolean,
    /** 방금 개인으로 바꿔서 아직 장소를 되살릴 수 있다 ("카드를 닫으면 장소를 지워요") */
    val canRestorePlaces: Boolean,
    val isListening: Boolean,
    /** 공용차에서 사람을 안 고르고 탔거나, 카드에서 막 골랐다 (고른 뒤에도 바꿀 수 있게 남긴다) */
    val hasFacePicker: Boolean,
    val hasHint: Boolean,
    /** 같은 날 이 운행보다 앞선 운행. 카드 왼쪽 블록 탑의 아래 칸 */
    val earlierToday: List<TripBlockState>,
    /** 이 운행 블록이 이미 떨어져 쌓였다. 홈을 다시 열 때마다 떨어지지 않게 */
    val isLanded: Boolean,
) {
    val isWork: Boolean get() = !trip.isPersonal
}

/**
 * 블록 그림의 블록 하나 = 운행 하나
 * @property placeName 이름 있는 도착지 (큰 블록 안 글자). 개인 운행·기록 안 된 주행은 null
 */
data class TripBlockState(
    val id: Long,
    val category: Category?,
    val km: Double,
    val isGap: Boolean,
    val placeName: String? = null,
)

internal fun Trip.toBlock(): TripBlockState =
    TripBlockState(id, category, km, isGap, destination?.takeIf { it.hasName }?.name)

/** 기록 안 된 주행 장면: 맨 앞 분류 대기 하나와, 그날 앞뒤 운행으로 쌓은 탑 (빈칸 포함, 아래부터) */
data class GapSceneState(val trip: Trip, val dayStack: List<TripBlockState>)

/** 누가 탔는지 골라 주세요: 지금 고를 수 있는 운행 + 운전자라 못 고르는 지난 운행 수 */
data class UnassignedState(val openTrips: List<Trip>, val lockedCount: Int) {
    val count: Int get() = openTrips.size + lockedCount
}

/** 금액을 아직 안 받은 주유·충전과, 지금 금액을 듣고 있는 것 */
data class RefuelTodoState(val refuels: List<Refuel>, val listeningId: Long?)

/** 다음 출발 [업무|개인] 토글과 (공용차) 누가 타나요 */
data class NextDepartureState(
    val category: Category,
    val defaultCategory: Category,
    val isChanged: Boolean,
    /** 토글 아래 한 줄: 바꿨으면 늘, 기본값 안내는 사용법 안내 기간에만 */
    val isNoteShown: Boolean,
    /** 공용차만. 혼자 쓰는 차는 null */
    val who: WhoState?,
)

data class WhoState(
    val selectedId: Long?,
    val lastPersonId: Long?,
    /** "안 고르고 출발하면 '사용자 미지정'으로…" 안내. 사용법 안내 기간에 아무도 안 골랐을 때만 */
    val isNoneNoteShown: Boolean,
)

/** 요약 카드: 사업연도 업무사용비율 · 이번 달 운행 · 기록 연속성 */
data class SummaryState(
    val fiscalYear: Int,
    val yearTotals: DistanceTotals,
    val month: YearMonth,
    val monthTotals: DistanceTotals,
    /** 이번 달 날짜마다 그날 운행 블록. 0번이 1일, 크기는 그달 날 수 */
    val monthDays: List<List<TripBlockState>>,
    val todayOfMonth: Int,
    /**
     * 계기판을 아직 못 읽었거나 기록이 하나도 없으면 null (연속성 줄을 숨긴다).
     * 기록이 없으면 견줄 마지막 기록이 없어, 아무것도 안 했는데 "○km까지 기록됨"이 된다
     */
    val odometerKm: Double?,
    val unrecordedKm: Double,
)

/** 저장소에서 오는 값 묶음 */
internal data class HomeRecords(
    val settings: ChagokSettings,
    val trips: List<Trip>,
    val people: List<Person>,
    val admin: AdminState,
    val next: NextDeparture,
)

/** 방금 운행 카드에 대해 이 화면만 들고 있는 값 */
internal data class RecentUi(
    /** 닫은 카드. [ChagokConfig.RECENT_CLOSED_MILLIS] 동안 "다시 보기"로 남는다 */
    val closedId: Long? = null,
    val isPicked: Boolean = false,
    val isListening: Boolean = false,
    /** 떨어져 쌓이는 그림을 이미 보여 준 운행 */
    val landedId: Long? = null,
)

internal fun buildHomeUiState(
    today: LocalDate,
    records: HomeRecords,
    recentId: Long?,
    recentUi: RecentUi,
    restorableTripId: Long?,
    odometerKm: Double?,
    hasGleo: Boolean,
    refuels: List<Refuel> = emptyList(),
    listeningRefuelId: Long? = null,
): HomeUiState {
    val trips = records.trips
    val isShared = records.settings.usage.isShared
    val isHintPeriod = records.settings.isHintPeriod(today)
    val recentTrip = recentId?.let { id -> trips.firstOrNull { it.id == id } }
    val fiscalYear = FiscalYear.of(today)
    val month = YearMonth.from(today)
    return HomeUiState(
        people = records.people,
        recent = recentTrip?.let { trip ->
            RecentCardState(
                trip = trip,
                isFirstVisit = TripRules.isFirstVisit(trip, trips),
                canRestorePlaces = trip.id == restorableTripId,
                isListening = recentUi.isListening,
                hasFacePicker = isShared && !trip.isGap && (trip.isUnassigned || recentUi.isPicked),
                hasHint = isHintPeriod,
                earlierToday = trips.takeWhile { it.id != trip.id }.filter { it.date == trip.date }.map(Trip::toBlock),
                isLanded = recentUi.landedId == trip.id,
            )
        },
        isRecentClosed = recentTrip == null && recentUi.closedId != null,
        pendingTrips = trips.filter { it.isPending },
        gap = trips.firstOrNull { it.isPending }?.let { gapScene(it, trips) },
        unassigned = if (isShared) unassignedOf(trips, recentTrip?.id, records.admin, today) else null,
        refuel = refuels.filter { it.needsAmount }
            .takeIf { it.isNotEmpty() }
            ?.let { RefuelTodoState(it, listeningRefuelId) },
        next = NextDepartureState(
            category = records.next.category,
            defaultCategory = records.settings.defaultCategory,
            isChanged = records.next.isChanged,
            isNoteShown = records.next.isChanged || isHintPeriod,
            who = if (isShared) {
                WhoState(
                    selectedId = records.next.personId,
                    lastPersonId = TripRules.lastPersonId(trips),
                    isNoneNoteShown = records.next.personId == null && isHintPeriod,
                )
            } else {
                null
            },
        ),
        today = TripRules.daySummary(trips, today),
        todayTrips = trips.filter { it.date == today },
        summary = SummaryState(
            fiscalYear = fiscalYear.year,
            yearTotals = TripRules.totalsOf(TripRules.ofFiscalYear(trips, fiscalYear)),
            month = month,
            monthTotals = TripRules.totalsOf(TripRules.ofMonth(trips, month)),
            monthDays = blocksByDay(TripRules.ofMonth(trips, month), month),
            todayOfMonth = today.dayOfMonth,
            odometerKm = odometerKm.takeIf { trips.isNotEmpty() },
            unrecordedKm = TripRules.unrecordedKm(trips.lastOrNull()?.endKm, odometerKm),
        ),
        guide = StartGuide.of(
            records.settings,
            records.admin.hasPin,
            hasTrip = trips.any { !it.isGap },
            hasGleo = hasGleo,
        ),
    )
}

/** 빈칸 아래·위로 이만큼씩만 쌓는다 */
private const val GAP_STACK_AROUND = 2

private fun gapScene(gap: Trip, trips: List<Trip>): GapSceneState {
    val day = trips.filter { it.date == gap.date }
    val at = day.indexOfFirst { it.id == gap.id }
    val from = (at - GAP_STACK_AROUND).coerceAtLeast(0)
    val to = (at + GAP_STACK_AROUND + 1).coerceAtMost(day.size)
    val around = day.subList(from, to)
    return GapSceneState(gap, around.map(Trip::toBlock))
}

private fun blocksByDay(trips: List<Trip>, month: YearMonth): List<List<TripBlockState>> {
    val byDay = trips.groupBy { it.date.dayOfMonth }
    return (1..month.lengthOfMonth()).map { day -> byDay[day].orEmpty().map(Trip::toBlock) }
}

// 방금 운행 카드에 있는 운행은 카드에서 고르니 뺀다. 운전자는 오늘 것만, 지난 것은 관리자가 고른다
private fun unassignedOf(trips: List<Trip>, recentId: Long?, admin: AdminState, today: LocalDate): UnassignedState? {
    val list = trips.filter { it.isUnassigned && it.id != recentId }
    if (list.isEmpty()) return null
    val (open, locked) = list.partition { admin.canOpen(it, today) }
    return UnassignedState(openTrips = open, lockedCount = locked.size)
}
