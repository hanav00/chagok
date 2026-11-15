package kr.chagok.ui.onboarding

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kr.chagok.AppContainer
import kr.chagok.R
import kr.chagok.common.runSuspendCatching
import kr.chagok.domain.model.Category
import kr.chagok.domain.model.TripPlace
import kr.chagok.domain.model.VehicleUsage
import kr.chagok.ui.WhileUiSubscribed
import kr.chagok.ui.containerFactory
import kr.chagok.ui.guide.ChangePractice
import kr.chagok.ui.guide.HowStory
import kr.chagok.ui.launchSaving
import java.time.LocalDate

/**
 * 첫 실행 단계 흐름. 필수 입력은 없고, 건너뛰면 기본 분류 업무 · 혼자 쓰는 차 · 집·회사 없음이 된다.
 * 말해 보기는 실제로 바꿔 보는 연습이라, 시작하기·건너뛰기 때 연습으로 바꾼 다음 출발을 되돌린다 ([ChangePractice]).
 * 기록은 이 화면과 상관없이 이미 돌고 있다.
 */
class OnboardingViewModel(private val container: AppContainer) : ViewModel() {

    private val draft = MutableStateFlow(Draft())
    private var finishJob: Job? = null
    private val practice = ChangePractice(container.nextDeparture, container.settings, viewModelScope)

    // "이렇게 기록돼요" 견본 도착지. 문자열 리소스라 화면이 넘겨 준다 ([setHowPlace])
    private val howPlace = MutableStateFlow("")

    // "지금 여기"를 빠르게 여러 번 고르면 위치 읽기가 겹쳐 늦게 끝난 쪽이 결과를 덮는다. 마지막 것만 남긴다
    private var hereJob: Job? = null

    val state: StateFlow<OnboardingUiState> = combine(
        draft,
        container.navi.bookmarks,
        container.admin.state,
        practice.state,
        howPlace,
    ) { current, bookmarks, admin, tried, place ->
        val (home, office) = placeRows(current.placePicks, bookmarks)
        OnboardingUiState(
            step = current.step,
            usage = current.usage,
            people = current.people,
            listeningIndex = current.listeningIndex,
            defaultCategory = current.defaultCategory,
            home = home,
            office = office,
            herePlace = current.herePlace,
            isHereFailed = current.isHereFailed,
            isHereLoading = current.isHereLoading,
            hasPin = admin.hasPin,
            isSkipped = current.isSkipped,
            hasGleo = container.isGleoAvailable,
            practice = tried,
            how = HowStory.build(
                container.tripVoice,
                LocalDate.now(container.clock),
                current.defaultCategory,
                container.isGleoAvailable,
                place,
            ),
        )
    }.stateIn(viewModelScope, WhileUiSubscribed, OnboardingUiState())

    /**
     * [from] 은 누를 때 화면에 보이던 단계. "다음"은 단계가 바뀌어도 같은 자리에 있어서,
     * 화면이 바뀌기 전에 두 번 눌리면 두 번째 누름이 한 단계를 더 넘긴다. 지난 단계에서 온 누름은 무시한다.
     */
    fun next(from: OnboardingStep = draft.value.step) {
        if (from != draft.value.step) return
        state.value.takeIf { it.step == from && it.isNextEnabled }?.nextStep?.let(::go)
    }

    fun previous(from: OnboardingStep = draft.value.step) {
        if (from != draft.value.step) return
        state.value.takeIf { it.step == from }?.previousStep?.let(::go)
    }

    // 정리 화면이 아닌 곳으로 가면 "건너뛰었다"는 풀린다 (다시 하나씩 고르는 중)
    private fun go(step: OnboardingStep) {
        draft.update { it.copy(step = step, isSkipped = it.isSkipped && step == OnboardingStep.DONE) }
        if (step == OnboardingStep.TRY) practice.start(draft.value.defaultCategory)
    }

    /**
     * 건너뛰기: 혼자 쓰는 차로 정리 화면에 간다.
     * 앞에서 정한 PIN 은 지우지 않는다. 적어 둔 복구키까지 모르는 새 무효가 되면 안 된다.
     */
    fun skip() {
        practice.end()
        draft.update { it.copy(step = OnboardingStep.DONE, isSkipped = true) }
    }

    fun selectUsage(usage: VehicleUsage) = draft.update { it.copy(usage = usage) }

    fun selectDefaultCategory(category: Category) = draft.update { it.copy(defaultCategory = category) }

    fun selectPlace(kind: PlaceKind, mode: PlaceMode) {
        if (!state.value.placeRow(kind).canPick(mode)) return
        draft.update { it.copy(placePicks = it.placePicks + (kind to mode)) }
        if (mode == PlaceMode.HERE) loadHere()
    }

    // "지금 여기": 고를 때 위치를 받아 둔다. 못 받으면 그 자리에서 "위치를 못 읽었어요"를 보여 준다
    private fun loadHere() {
        hereJob?.cancel()
        hereJob = viewModelScope.launch {
            draft.update { it.copy(isHereFailed = false, isHereLoading = true) }
            val place = readOrNull("현재 위치") { container.navi.currentLocation() }
            draft.update { it.copy(herePlace = place, isHereFailed = place == null, isHereLoading = false) }
        }
    }

    fun addUnnamedPerson() {
        if (!state.value.canAddPerson) return
        draft.update { it.copy(people = it.people + DraftPerson()) }
    }

    /** 이름을 말해서 추가: 빈 자리를 먼저 만들고 듣는다. 못 알아들으면 이름 없는 사람으로 남긴다 */
    fun addPersonByVoice() {
        // 듣는 중에 또 누르면 빈 사람만 하나 더 생기고, 마이크를 서로 덮어써 둘 다 못 알아듣는다
        if (!state.value.canAddPerson || draft.value.listeningIndex != null) return
        val index = draft.value.people.size
        draft.update { it.copy(people = it.people + DraftPerson(), listeningIndex = index) }
        viewModelScope.launch {
            val name = readOrNull("이름 듣기") { container.voiceInput.listen() }?.trim().orEmpty()
            if (name.isEmpty()) container.messages.show(R.string.voice_not_heard_name)
            draft.update { current ->
                val people = current.people.mapIndexed { i, person ->
                    if (i == index) person.copy(name = name) else person
                }
                current.copy(people = people, listeningIndex = null)
            }
        }
    }

    /** "시작하기": 사람과 설정을 저장한다. 저장되면 껍데기가 탭 화면으로 바꾼다 */
    fun finish(labels: UnnamedLabels) {
        if (finishJob?.isActive == true) return // 두 번 눌러도 한 번만 저장
        val result = state.value.toChoice(labels).toResult()
        practice.end() // 연습으로 바꾼 다음 출발을 되돌린다 (실제 첫 운행은 기본값으로)
        finishJob = launchSaving(container.messages, retry = { finish(labels) }) {
            container.people.replaceAll(result.people)
            container.settings.completeFirstRun(result)
        }
    }

    fun setHowPlace(name: String) {
        howPlace.value = name
    }

    /** "이렇게 기록돼요"의 들어 보기: 실제 음성 안내로 말한다 */
    fun speak(text: String) = container.tripVoice.preview(text)

    /** Gleo 가 없는 차의 1탭 연습 */
    fun tapPractice(category: Category) = practice.tap(category)

    // 읽기 실패는 로그만 남기고 첫 실행을 이어 간다
    private suspend fun <T> readOrNull(what: String, block: suspend () -> T?): T? =
        runSuspendCatching { block() }.onFailure { Log.e(TAG, "$what 실패", it) }.getOrNull()

    /** 사용자가 고른 값 그대로. 즐겨찾기·PIN 과 합쳐 [OnboardingUiState] 가 된다 */
    private data class Draft(
        val step: OnboardingStep = OnboardingStep.WELCOME,
        /** 가장 흔한 혼자 쓰는 차를 미리 골라 둔다. 건너뛰기도 혼자 쓰는 차다 */
        val usage: VehicleUsage? = VehicleUsage.SOLO,
        val people: List<DraftPerson> = listOf(DraftPerson()),
        val listeningIndex: Int? = null,
        val defaultCategory: Category = Category.BUSINESS,
        /** 집·회사를 직접 고른 것만. 안 고른 쪽은 즐겨찾기 유무로 정해진다 ([placeRows]) */
        val placePicks: Map<PlaceKind, PlaceMode> = emptyMap(),
        val herePlace: TripPlace? = null,
        val isHereFailed: Boolean = false,
        val isHereLoading: Boolean = false,
        val isSkipped: Boolean = false,
    )

    companion object {
        private const val TAG = "OnboardingViewModel"
        val Factory = containerFactory(::OnboardingViewModel)
    }
}