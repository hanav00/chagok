package kr.chagok.ui.table

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kr.chagok.AppContainer
import kr.chagok.domain.FiscalYear
import kr.chagok.domain.TripRules
import kr.chagok.domain.model.AdminState
import kr.chagok.domain.model.DistanceTotals
import kr.chagok.domain.model.ChagokSettings
import kr.chagok.domain.model.Person
import kr.chagok.domain.model.Trip
import kr.chagok.domain.model.VehicleInfo
import kr.chagok.ui.WhileUiSubscribed
import kr.chagok.ui.containerFactory
import java.time.LocalDate
import java.time.YearMonth

/**
 * 기록부 탭 상태. 보고 있는 달과 서식 머리 펼침은 여기 둔다. 탭을 옮겼다 와도 그대로다.
 * 달을 넘기지 않았으면 이번 달을 따라간다 (자정을 넘겨 달이 바뀌면 새 달을 연다).
 * 관리자 PIN 을 정한 차가 잠겨 있으면 오늘 운행만 보인다.
 */
class TableViewModel(private val container: AppContainer) : ViewModel() {
    /** 사용자가 넘겨 고른 달. null 이면 이번 달을 따라간다 */
    private val pickedMonth = MutableStateFlow<YearMonth?>(null)

    // 보고 있는 달은 오늘에 따라 바뀔 수 있다 (combine 은 다섯 개까지라 묶는다)
    private val monthAndToday = combine(pickedMonth, container.today.date) { picked, today ->
        (picked ?: YearMonth.from(today)) to today
    }

    private val _isFormHeadOpen = MutableStateFlow<Boolean?>(null)

    /** 서식 머리 펼침. null 이면 아직 안 건드렸다. 이때는 넓게면 펼치고 분할 칸이면 접는다 */
    val isFormHeadOpen: StateFlow<Boolean?> = _isFormHeadOpen.asStateFlow()

    val state: StateFlow<TableUiState?> = combine(
        monthAndToday,
        container.trips.trips,
        container.people.allPeople, // 숨긴 사람도 지난 운행에는 이름이 남는다
        container.settings.settings,
        container.admin.state,
    ) { (month, today), trips, people, settings, admin ->
        buildState(month, trips, people, settings, admin, today)
    }.stateIn(viewModelScope, WhileUiSubscribed, null)

    /** 달 넘기기. 이번 달로 돌아오면 다시 이번 달을 따라간다 */
    fun shiftMonth(months: Long) {
        val thisMonth = YearMonth.from(container.today.date.value)
        pickedMonth.update { picked -> (picked ?: thisMonth).plusMonths(months).takeUnless { it == thisMonth } }
    }

    fun setFormHeadOpen(isOpen: Boolean) {
        _isFormHeadOpen.value = isOpen
    }

    private fun buildState(
        month: YearMonth,
        trips: List<Trip>,
        people: List<Person>,
        settings: ChagokSettings,
        admin: AdminState,
        today: LocalDate,
    ): TableUiState {
        val peopleById = people.associateBy(Person::id)
        val fiscalYear = FiscalYear.of(today)
        val isLocked = !admin.canManage
        // 잠겨 있으면 오늘 운행만 보이므로 이번 달에 둔다 (다른 달로 가 있으면 "오늘 운행이 없어요"만 보인다)
        val shownMonth = if (isLocked) YearMonth.from(today) else month
        val visible = TripRules.ofMonth(trips, shownMonth).filter { admin.canOpen(it, today) }
        return TableUiState(
            month = shownMonth,
            isThisMonth = shownMonth == YearMonth.from(today),
            rows = visible.map { trip ->
                TableRow(
                    trip = trip,
                    person = peopleById[trip.personId],
                    // 혼자 쓰는 차는 사람을 묻지 않으니 비어 있어도 "미지정"이 아니다
                    isUnassigned = settings.usage.isShared && trip.isUnassigned,
                )
            },
            monthTotals = TripRules.totalsOf(visible),
            yearTotals = TripRules.totalsOf(TripRules.ofFiscalYear(trips, fiscalYear)),
            fiscalYear = fiscalYear,
            vehicle = settings.vehicle,
            isLocked = isLocked,
            isHintPeriod = settings.isHintPeriod(today),
        )
    }

    companion object {
        val Factory = containerFactory(::TableViewModel)
    }
}

data class TableUiState(
    val month: YearMonth,
    /** 오늘이 속한 달. 열면 가장 최근 줄(분류 대기·미지정)이 보이게 표 끝으로 내린다 */
    val isThisMonth: Boolean,
    /** 이 달 운행 중 지금 열어 볼 수 있는 것 (잠겨 있으면 오늘 것만) */
    val rows: List<TableRow>,
    val monthTotals: DistanceTotals,
    /** ⑪⑫⑬: 오늘이 속한 사업연도 */
    val yearTotals: DistanceTotals,
    val fiscalYear: FiscalYear,
    val vehicle: VehicleInfo,
    /** 관리자 PIN 을 정했고 지금 잠겨 있음: 지난 기록·내보내기를 막는다 */
    val isLocked: Boolean,
    val isHintPeriod: Boolean,
)

/** 기록부 한 줄 = 운행 하나 */
data class TableRow(
    val trip: Trip,
    val person: Person?,
    /** 공용차에서 누가 탔는지 안 고른 운행 (④성명 칸에 "미지정") */
    val isUnassigned: Boolean,
)
