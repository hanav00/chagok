package kr.chagok.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kr.chagok.AppContainer
import kr.chagok.R
import kr.chagok.domain.FiscalYear
import kr.chagok.domain.ChagokConfig
import kr.chagok.domain.model.AdminEdit
import kr.chagok.domain.model.AdminState
import kr.chagok.domain.model.Category
import kr.chagok.domain.model.ChagokSettings
import kr.chagok.domain.model.Person
import kr.chagok.domain.model.TripPlace
import kr.chagok.domain.model.VehicleUsage
import kr.chagok.ui.WhileUiSubscribed
import kr.chagok.ui.containerFactory
import kr.chagok.ui.launchSaving

/**
 * 설정 화면 상태. 차량·사업자와 사람은 줄을 눌러 시트에서 고친다 (ui/vehicle · ui/people).
 * PIN 을 정한 차의 기록 방식은 관리자만 바꾼다.
 */
class SettingsViewModel(private val container: AppContainer) : ViewModel() {

    val state: StateFlow<SettingsUiState?> = combine(
        container.settings.settings,
        container.people.allPeople,
        container.admin.state,
        container.trips.adminEdits,
        container.today.date, // 해가 바뀌면 사업연도도 바뀐다
    ) { settings, people, admin, edits, today ->
        SettingsUiState(
            settings = settings,
            people = people,
            admin = admin,
            recentEdits = edits.take(ChagokConfig.ADMIN_LOG_ROWS),
            fiscalYear = FiscalYear.of(today),
            isGleoAvailable = container.isGleoAvailable,
        )
    }.stateIn(viewModelScope, WhileUiSubscribed, null)

    // 관리자만 쓰는 칸의 다시 하기는 같은 함수로 (그사이 잠겼으면 다시 막는다)
    fun setDefaultCategory(category: Category) {
        if (!canManage()) return
        launchSaving(container.messages, retry = { setDefaultCategory(category) }) {
            container.settings.setDefaultCategory(category)
        }
    }

    fun setUsage(usage: VehicleUsage) {
        if (!canManage()) return
        launchSaving(container.messages, retry = { setUsage(usage) }) { container.settings.setUsage(usage) }
    }

    /**
     * 집·회사를 내비 즐겨찾기로 맞춘다. 출퇴근 분류가 바뀌므로 관리자만. 즐겨찾기에 없는 쪽은 앱에 있던 값을 쓴다.
     * 그래도 한쪽이 비면 바꾸지 않는다. 한 곳만으로는 출퇴근을 나눌 수 없다 (첫 실행과 같은 규칙).
     */
    fun syncPlacesFromNavi() {
        if (!canManage()) return
        launchSaving(container.messages, retry = ::syncPlacesFromNavi) {
            val bookmarks = container.navi.freshBookmarks()
            val current = container.settings.settings.first()
            val home = bookmarks.home?.takeUnless(TripPlace::isEmpty) ?: current.home
            val office = bookmarks.office?.takeUnless(TripPlace::isEmpty) ?: current.office
            if (home == null || office == null) {
                container.messages.show(R.string.settings_places_need_both)
                return@launchSaving
            }
            container.settings.setPlaces(home, office)
            container.messages.show(R.string.settings_places_synced)
        }
    }

    /** 지금 차가 서 있는 곳을 집(또는 회사)으로 (첫 실행의 "지금 여기"와 같다). 관리자만. 위치를 못 읽으면 알린다 */
    fun setPlaceHere(isHome: Boolean) {
        if (!canManage()) return
        launchSaving(container.messages, retry = { setPlaceHere(isHome) }) {
            val here = container.navi.currentLocation()
            if (here == null) {
                container.messages.show(R.string.onboarding_place_here_failed_short)
                return@launchSaving
            }
            if (isHome) container.settings.setHome(here) else container.settings.setOffice(here)
            container.messages.show(
                if (isHome) R.string.settings_place_home_here_done else R.string.settings_place_office_here_done,
            )
        }
    }

    fun setTripVoiceOn(isOn: Boolean) {
        launchSaving(container.messages) { container.settings.setTripVoiceOn(isOn) }
    }

    /** 사용자 순서 한 칸 위로 (맨 위가 기본 사용자). 관리자만 */
    fun movePersonUp(personId: Long) {
        if (!canManage()) return
        launchSaving(container.messages, retry = { movePersonUp(personId) }) { container.people.moveUp(personId) }
    }

    fun lockAdmin() = container.admin.lock()

    // 화면이 잠긴 칸을 막지만, 잠기는 순간 눌린 것까지 한 번 더 막는다
    private fun canManage(): Boolean = container.admin.state.value.canManage

    companion object {
        val Factory = containerFactory(::SettingsViewModel)
    }
}

data class SettingsUiState(
    val settings: ChagokSettings,
    /** 숨긴 사람까지 등록 순서대로 (숨기지 않은 첫 사람이 기본 사용자). 관리자 수정 기록의 이름도 여기서 찾는다 */
    val people: List<Person>,
    val admin: AdminState,
    /** 관리자 수정 기록 최근 것부터 [ChagokConfig.ADMIN_LOG_ROWS] 줄 */
    val recentEdits: List<AdminEdit>,
    /** 오늘이 속한 사업연도 */
    val fiscalYear: FiscalYear,
    /** Gleo 가 깔린 차 ("Gleo로 말하기" 칸을 보일지) */
    val isGleoAvailable: Boolean,
) {
    /** 공용차이거나 PIN 을 정한 차는 관리자 칸을 맨 위에, 혼자 쓰는 차는 아래쪽에 */
    val isAdminFirst: Boolean get() = settings.usage.isShared || admin.hasPin
}
