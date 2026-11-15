package kr.chagok.domain

import kr.chagok.domain.model.Category
import kr.chagok.domain.model.CategoryDay
import kr.chagok.domain.model.ClassifyReason
import kr.chagok.domain.model.DistanceTotals
import kr.chagok.domain.model.NextDeparture
import kr.chagok.domain.model.Trip
import kr.chagok.domain.model.TripPlace
import java.time.LocalDate
import java.time.YearMonth

private const val MONTHS_IN_YEAR = 12L

/**
 * 운행 묶음에 대한 판단. 운행 하나에 대한 판단(개인인지, 거리, 서식 칸)은 [Trip] 에 있다.
 * 분류는 위치로 맞히지 않는다. 기본값(업무/개인)으로 시동마다 시작하고 사용자가 출발 전·도착 후에 바꾸며,
 * 업무 운행 중 집 ↔ 회사만 출퇴근으로 나눈다.
 */
object TripRules {

    /** 도착할 때 정해지는 분류와 근거. 분류 자체는 출발 때 [next] 로 이미 정해져 있다 */
    fun classifyAtArrival(
        next: NextDeparture,
        origin: TripPlace?,
        destination: TripPlace?,
        home: TripPlace?,
        office: TripPlace?,
    ): Pair<Category, ClassifyReason> {
        val reason = if (next.isChanged) ClassifyReason.PRE_PICK else ClassifyReason.defaultOf(next.category)
        val category = when {
            next.category == Category.PERSONAL -> Category.PERSONAL
            next.category == Category.COMMUTE -> Category.COMMUTE // 사용자가 말로 출퇴근이라고 정함
            PlaceMatcher.isHomeOfficeTrip(origin, destination, home, office) -> Category.COMMUTE
            else -> Category.BUSINESS
        }
        return category to reason
    }

    /**
     * 계기판이 마지막 기록보다 앞서 있는 거리 ("기록 안 된 주행"). 오차([ChagokConfig.GAP_MIN_KM]) 아래면 0.
     * 계기판은 차가 기억하는 값이라 앱이 놓쳐도 사라지지 않는다.
     */
    fun unrecordedKm(lastEndKm: Double?, odometerKm: Double?): Double {
        if (lastEndKm == null || odometerKm == null) return 0.0
        val gap = odometerKm - lastEndKm
        return if (gap >= ChagokConfig.GAP_MIN_KM - ChagokConfig.KM_EPSILON) gap else 0.0
    }

    fun totalsOf(trips: Iterable<Trip>): DistanceTotals = trips.fold(DistanceTotals()) { sum, trip ->
        val km = trip.km
        when (trip.category) {
            Category.COMMUTE -> sum.copy(total = sum.total + km, commute = sum.commute + km)
            Category.BUSINESS -> sum.copy(total = sum.total + km, business = sum.business + km)
            Category.PERSONAL -> sum.copy(total = sum.total + km, personal = sum.personal + km)
            null -> sum.copy(total = sum.total + km, pending = sum.pending + km)
        }
    }

    fun ofMonth(trips: List<Trip>, month: YearMonth): List<Trip> = trips.filter { YearMonth.from(it.date) == month }

    fun ofFiscalYear(trips: List<Trip>, fiscalYear: FiscalYear): List<Trip> = trips.filter { it.date in fiscalYear }

    /** 한 달을 날짜마다 분류별 합계로. 0번이 1일, 크기는 그달 날 수 */
    fun dailyTotals(trips: List<Trip>, month: YearMonth): List<DistanceTotals> {
        val byDay = ofMonth(trips, month).groupBy { it.date.dayOfMonth }
        return (1..month.lengthOfMonth()).map { day -> totalsOf(byDay[day].orEmpty()) }
    }

    /** 사업연도를 달마다 분류별 합계로. 0번이 시작 월, 12개 */
    fun monthlyTotals(trips: List<Trip>, fiscalYear: FiscalYear): List<DistanceTotals> {
        val byMonth = ofFiscalYear(trips, fiscalYear).groupBy { YearMonth.from(it.date) }
        val first = YearMonth.from(fiscalYear.start)
        return (0L until MONTHS_IN_YEAR).map { offset -> totalsOf(byMonth[first.plusMonths(offset)].orEmpty()) }
    }

    /** 하루 운행을 분류별로 (분류한 운행만). 그날 운행이 없으면 null */
    fun daySummary(trips: List<Trip>, date: LocalDate): Map<Category, CategoryDay>? {
        val ofDay = trips.filter { it.date == date && !it.isPending }
        if (ofDay.isEmpty()) return null
        return Category.entries.associateWith { category ->
            val ofCategory = ofDay.filter { it.category == category }
            CategoryDay(
                count = ofCategory.size,
                km = ofCategory.sumOf { it.km },
                placeNames = ofCategory.mapNotNull { it.destination?.takeIf(TripPlace::hasName)?.name }.distinct(),
            )
        }
    }

    /** 이 운행 전에 같은 도착지가 기록된 적이 없는지 ("처음 간 곳" 표시). 이름 있는 목적지만 따진다 */
    fun isFirstVisit(trip: Trip, trips: List<Trip>): Boolean {
        val destination = trip.destination?.takeIf(TripPlace::hasName) ?: return false
        return trips.none { it.id < trip.id && PlaceMatcher.isSamePlace(it.destination, destination) }
    }

    /** 마지막으로 사람이 정해진 운행의 사용자 ("누가 타나요?"의 "지난번" 표시) */
    fun lastPersonId(trips: List<Trip>): Long? = trips.lastOrNull { it.personId != null }?.personId
}
