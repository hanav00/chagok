package kr.chagok.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kr.chagok.AppContainer
import kr.chagok.R
import kr.chagok.domain.ChagokConfig
import kr.chagok.domain.model.Category
import kr.chagok.domain.model.Refuel
import kr.chagok.recorder.RefuelDictation
import kr.chagok.ui.WhileUiSubscribed
import kr.chagok.ui.containerFactory
import kr.chagok.ui.format.Formatters
import kr.chagok.ui.launchTripDictation
import kr.chagok.ui.launchSaving
import java.time.LocalDate
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/**
 * 홈 화면 상태. 방금 운행은 기록기가 알려 주고, 닫은 뒤의 "다시 보기"와 카드에서 사람을 골랐는지는 이 화면이 들고 있다.
 *
 * @param timerContext "다시 보기" 줄을 [ChagokConfig.RECENT_CLOSED_MILLIS] 동안 세는 곳 (가상 시간으로 돌릴 수 있게 따로 받는다)
 */
class HomeViewModel(
    private val container: AppContainer,
    private val timerContext: CoroutineContext = EmptyCoroutineContext,
) : ViewModel() {
    private val recentUi = MutableStateFlow(RecentUi())
    private var closedTimer: Job? = null
    private var summaryIntroDate: LocalDate? = null

    // 금액을 듣고 있는 주유·충전 (한 번에 하나만 듣는다)
    private val listeningRefuelId = MutableStateFlow<Long?>(null)

    private val records = combine(
        container.settings.settings,
        container.trips.trips,
        container.people.people,
        container.admin.state,
        container.nextDeparture.next,
    ) { settings, trips, people, admin, next ->
        next?.let { HomeRecords(settings, trips, people, admin, it) }
    }

    // 날짜·계기판은 차와 시계에서 온다 (combine 은 다섯 개까지라 묶는다). 자정이 지나면 오늘 요약·고를 수 있는 미지정이 바뀐다
    private val todayAndOdometer = combine(container.today.date, container.recorder.odometerKm, ::Pair)

    // 되살릴 장소와 주유·충전 할 일도 같은 이유로 묶는다
    private val restorableAndRefuels = combine(
        container.trips.restorableTripId,
        container.refuels.refuels,
        listeningRefuelId,
    ) { restorableId, refuels, listeningId -> RefuelSide(restorableId, refuels, listeningId) }

    val state: StateFlow<HomeUiState?> = combine(
        records,
        recentUi,
        container.recorder.recentTripId,
        restorableAndRefuels,
        todayAndOdometer,
    ) { records, ui, recorderRecentId, side, (today, odometerKm) ->
        records ?: return@combine null // 설정을 읽는 동안은 비워 둔다
        buildHomeUiState(
            today = today,
            records = records,
            recentId = recorderRecentId,
            recentUi = ui,
            restorableTripId = side.restorableTripId,
            odometerKm = odometerKm,
            hasGleo = container.isGleoAvailable,
            refuels = side.refuels,
            listeningRefuelId = side.listeningRefuelId,
        )
    }.stateIn(viewModelScope, WhileUiSubscribed, null)

    init {
        // 새 운행이 들어오면 앞 카드의 닫음·다시 열기·사람 고름은 잊는다
        viewModelScope.launch {
            container.recorder.recentTripId.filterNotNull().collect {
                closedTimer?.cancel()
                recentUi.update { current -> RecentUi(isListening = current.isListening) }
            }
        }
    }

    fun recentToPersonal() {
        val id = shownRecentId() ?: return
        val isAdminEdit = isAdminEdit()
        launchSaving(container.messages) { container.trips.classify(id, Category.PERSONAL, isAdminEdit) }
    }

    /** 방금 개인으로 바꾼 거면 원래 분류·장소로, 처음부터 개인이었으면 일반 업무로 */
    fun recentToWork() {
        val id = shownRecentId() ?: return
        val isAdminEdit = isAdminEdit()
        launchSaving(container.messages) { container.trips.revertToWork(id, isAdminEdit) }
    }

    /**
     * 요약 카드가 들어올 때 움직일지. 하루에 처음 한 번만 true (매번 움직이면 장식이 된다).
     * 화면을 다시 그릴 때마다 묻지 않게 화면 쪽에서 한 번만 부른다.
     */
    fun takeSummaryIntro(): Boolean {
        val today = container.today.date.value
        if (summaryIntroDate == today) return false
        summaryIntroDate = today
        return true
    }

    fun markRecentLanded(tripId: Long) = recentUi.update { it.copy(landedId = tripId) }

    fun pickRecentPerson(personId: Long) {
        val id = shownRecentId() ?: return
        recentUi.update { it.copy(isPicked = true) }
        assignPerson(id, personId)
    }

    /** 마이크로 방문 목적을 남기거나 말로 분류·사람을 고친다 */
    fun dictate() {
        val id = shownRecentId() ?: return
        launchTripDictation(
            container,
            id,
            isListening = { recentUi.value.isListening },
            setListening = { listening -> recentUi.update { it.copy(isListening = listening) } },
        )
    }

    /** 닫으면 개인으로 바꾼 장소 삭제를 확정하고, 실수로 닫았을 때를 위해 잠깐 "다시 보기"를 남긴다 */
    fun dismissRecent() {
        val id = shownRecentId() ?: return
        container.trips.commitPlaceDeletion(id)
        container.recorder.clearRecentTrip()
        closedTimer?.cancel()
        recentUi.update { it.copy(closedId = id) }
        closedTimer = viewModelScope.launch(timerContext) {
            delay(ChagokConfig.RECENT_CLOSED_MILLIS)
            recentUi.update { it.copy(closedId = null) }
        }
    }

    fun reopenRecent() {
        val id = recentUi.value.closedId ?: return
        closedTimer?.cancel()
        container.recorder.showRecentTrip(id) // 새 카드처럼 다시 들어온다 (닫음 줄은 init 에서 비운다)
    }

    fun classify(tripId: Long, category: Category) {
        val isAdminEdit = isAdminEdit()
        launchSaving(container.messages) { container.trips.classify(tripId, category, isAdminEdit) }
    }

    /** 미지정 운행에 사람을 고른다 */
    fun assignPerson(tripId: Long, personId: Long) {
        val isAdminEdit = isAdminEdit()
        launchSaving(container.messages) { container.trips.assignPerson(tripId, personId, isAdminEdit) }
    }

    /** 주유·충전 금액을 마이크로 받는다. 듣는 중에 다시 누르면 무시한다 */
    fun dictateRefuelAmount(refuelId: Long) {
        if (listeningRefuelId.value != null) return
        listeningRefuelId.value = refuelId
        launchSaving(container.messages, retry = { dictateRefuelAmount(refuelId) }) {
            try {
                when (val result = container.refuelDictation.dictate(refuelId)) {
                    is RefuelDictation.Result.Done ->
                        container.messages.showDone(R.string.refuel_amount_saved, Formatters.won(result.won))
                    RefuelDictation.Result.NotHeard -> container.messages.show(R.string.voice_not_heard)
                }
            } finally {
                listeningRefuelId.value = null
            }
        }
    }

    /** 금액을 모른다고 넘긴다. 홈 할 일에서 빠진다 */
    fun skipRefuelAmount(refuelId: Long) {
        launchSaving(container.messages) { container.refuels.skipAmount(refuelId) }
    }

    /** 시작 가이드를 닫는다. 다시 보이지 않는다 */
    fun dismissGuide() {
        launchSaving(container.messages) { container.settings.dismissGuide() }
    }

    fun pickNextCategory(category: Category) = container.nextDeparture.pickCategory(category)

    fun pickNextPerson(personId: Long) = container.nextDeparture.pickPerson(personId)

    private fun shownRecentId(): Long? = container.recorder.recentTripId.value

    // 관리자 모드에서 고친 것은 "관리자 수정"으로 남는다. 저장이 끝날 때가 아니라 누른 순간의 상태로 정한다
    private fun isAdminEdit(): Boolean = container.admin.state.value.isAdminEdit

    private data class RefuelSide(
        val restorableTripId: Long?,
        val refuels: List<Refuel>,
        val listeningRefuelId: Long?,
    )

    companion object {
        val Factory = containerFactory(::HomeViewModel)
    }
}
