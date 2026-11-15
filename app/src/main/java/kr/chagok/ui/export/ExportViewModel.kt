package kr.chagok.ui.export

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kr.chagok.AppContainer
import kr.chagok.R
import kr.chagok.domain.FiscalYear
import kr.chagok.domain.TripRules
import kr.chagok.domain.VehicleRules
import kr.chagok.domain.model.DistanceTotals
import kr.chagok.domain.model.ChagokSettings
import kr.chagok.domain.model.Person
import kr.chagok.domain.model.Trip
import kr.chagok.domain.model.VehicleField
import kr.chagok.domain.model.VehicleInfo
import kr.chagok.ui.WhileUiSubscribed
import kr.chagok.ui.containerFactory
import java.time.YearMonth

/**
 * 기록부 > 내보내기 상태.
 * 월별은 기록부에서 보던 달을 그대로 내보낸다([show]). 고른 종류(월별·연간)는 탭을 옮겨도 남는다.
 * 관리자만 여는 화면이라(기록부가 막는다) 잠금에 따라 운행을 거르지 않는다.
 */
class ExportViewModel(private val container: AppContainer) : ViewModel() {
    private val month = MutableStateFlow(YearMonth.from(container.today.date.value))
    private val mode = MutableStateFlow(ExportMode.MONTH)

    // 연간은 오늘이 속한 사업연도라 해가 바뀌면 바뀐다 (combine 은 다섯 개까지라 묶는다)
    private val modeAndToday = combine(mode, container.today.date, ::Pair)

    val state: StateFlow<ExportUiState?> = combine(
        month,
        modeAndToday,
        container.trips.trips,
        container.settings.settings,
        container.people.allPeople, // 숨긴 사람도 지난 운행에는 이름이 나간다
    ) { month, (mode, today), trips, settings, people ->
        ExportUiState(
            mode = mode,
            missingVehicleFields = VehicleRules.missingFields(settings.vehicle),
            monthExport = monthExport(month, trips, settings, people),
            yearExport = yearExport(trips, settings, FiscalYear.of(today)),
        )
    }.stateIn(viewModelScope, WhileUiSubscribed, null)

    /** 기록부에서 보고 있던 달 */
    fun show(month: YearMonth) {
        this.month.value = month
    }

    fun selectMode(mode: ExportMode) {
        this.mode.value = mode
    }

    private fun monthExport(
        month: YearMonth,
        trips: List<Trip>,
        settings: ChagokSettings,
        people: List<Person>,
    ): MonthExport {
        val ofMonth = TripRules.ofMonth(trips, month)
        val pending = ofMonth.filter(Trip::isPending)
        // 혼자 쓰는 차는 사람을 묻지 않으니 비어 있어도 미지정이 아니다
        val unassigned = if (settings.usage.isShared) ofMonth.filter(Trip::isUnassigned) else emptyList()
        return MonthExport(
            month = month,
            tripCount = ofMonth.size,
            totals = TripRules.totalsOf(ofMonth),
            towers = TripRules.dailyTotals(trips, month),
            pendingCount = pending.size,
            firstPendingId = pending.firstOrNull()?.id,
            unassignedCount = unassigned.size,
            firstUnassignedId = unassigned.firstOrNull()?.id,
            unnamedPeople = unnamedIn(ofMonth, people),
        )
    }

    // 이 달 운행에 나오는 사람 중 이름 없는 사람 (등록 순서). 서식 ④성명에 "이름 입력 필요"로 나간다
    private fun unnamedIn(trips: List<Trip>, people: List<Person>): List<Person> {
        val ids = trips.mapNotNullTo(HashSet(), Trip::personId)
        return people.filter { it.id in ids && !it.isNamed }
    }

    private fun yearExport(trips: List<Trip>, settings: ChagokSettings, fiscalYear: FiscalYear): YearExport {
        val ofYear = TripRules.ofFiscalYear(trips, fiscalYear)
        return YearExport(
            fiscalYear = fiscalYear,
            vehicle = settings.vehicle,
            totals = TripRules.totalsOf(ofYear),
            tripCount = ofYear.size,
            towers = TripRules.monthlyTotals(trips, fiscalYear),
            firstPendingId = ofYear.firstOrNull(Trip::isPending)?.id,
        )
    }

    companion object {
        val Factory = containerFactory(::ExportViewModel)
    }
}

/** 내보내기 종류: 회사가 보관하는 월별 기록부 원본 / 신고 때 내는 관련비용 명세서용 연간 합계 */
enum class ExportMode(@StringRes val labelRes: Int) {
    MONTH(R.string.export_mode_month),
    YEAR(R.string.export_mode_year),
}

data class ExportUiState(
    val mode: ExportMode,
    /** 서식 머리에 아직 안 넣은 칸. 있으면 "빠진 값 채우기"로 그 칸만 묻는다 (막지는 않는다, 엑셀에 빈칸) */
    val missingVehicleFields: List<VehicleField>,
    val monthExport: MonthExport,
    val yearExport: YearExport,
)

data class MonthExport(
    val month: YearMonth,
    val tripCount: Int,
    val totals: DistanceTotals,
    /** 날짜마다 합계. 내보내기 그림에서 탑이 모여 기록부 한 권이 된다 */
    val towers: List<DistanceTotals>,
    val pendingCount: Int,
    /** "지금 분류하기"로 열 첫 분류 대기 운행 */
    val firstPendingId: Long?,
    val unassignedCount: Int,
    /** "지금 고르기"로 열 첫 미지정 운행 */
    val firstUnassignedId: Long?,
    /** 이 달 운행에 나오는데 이름이 없는 사람. "이름 넣기"로 첫 사람의 사람 시트를 연다 */
    val unnamedPeople: List<Person>,
)

/** 관련비용 명세서(법인세법 시행규칙 별지 제29호서식) 중 앱이 채울 수 있는 값. 사업연도는 오늘이 속한 해 */
data class YearExport(
    val fiscalYear: FiscalYear,
    val vehicle: VehicleInfo,
    val totals: DistanceTotals,
    val tripCount: Int,
    /** 달마다 합계 (사업연도 시작 월부터 12개) */
    val towers: List<DistanceTotals>,
    val firstPendingId: Long?,
)
